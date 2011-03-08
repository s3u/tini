/*
 * Copyright (c) 2011 CONTRIBUTORS
 *
 * This file is licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tini.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Parses an HTTP message and asynchronously invokes handlers to process different parts of a
 * message.</p>
 *
 * @author Subbu Allamaraju
 */
public abstract class HttpParser {

    protected static final Logger logger = Logger.getLogger("org.tini.core.parser");

    // State from channel
    private final AsynchronousSocketChannel channel;
    private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
    private final AtomicBoolean first = new AtomicBoolean(true);

    // Limits
    protected final int maxInitialLineLength = 8192;
    private final int maxHeaderLineSize = 2048;
    private final int maxChunkSize = 8192;
    private final long timeout;
    private final TimeUnit timeUnit;
    private final AtomicInteger bytesRemaining;
    private static final ByteBuffer EMPTY_BUFFER= ByteBuffer.wrap(new byte[0]);

    // TODO: HTTP messages are parsed sequentially for now. Check concurrency issues.

    // Handler
    protected CompletionHandler<Void, Void> beforeNext;
    private final List<CompletionHandler<Map<String, List<String>>, Void>> onHeaders = new ArrayList<CompletionHandler<Map<String, List<String>>, Void>>(1);
    private CompletionHandler<ByteBuffer, Void> onData = new ReadingCompletionHandler();
    private final List<CompletionHandler<Map<String, List<String>>, Void>> onTrailers = new ArrayList<CompletionHandler<Map<String, List<String>>, Void>>(1);

    // Headers - we keep the headers to decide whether to parse the message body as chunks or as one
    // known-length body.
    private Map<String, List<String>> headers;

    /**
     * Creates a parser.
     *
     * @param channel  channel
     * @param timeout  read timeout
     * @param timeUnit timeout unit
     */
    public HttpParser(final AsynchronousSocketChannel channel,
                      final long timeout,
                      final TimeUnit timeUnit) {
        this.channel = channel;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        bytesRemaining = new AtomicInteger(0);

        onHeaders.add(new NullCompletionHandler<Map<String, List<String>>, Void>());
        onTrailers.add(new NullCompletionHandler<Map<String, List<String>>, Void>());

    }

    /**
     * @param beforeNext a handler to register message handlers - note that after each
     *                   request/response, the parser clears message handlers, and hence the caller
     *                   needs to re-register them.
     */
    public void beforeReadNext(final CompletionHandler<Void, Void> beforeNext) {
        this.beforeNext = beforeNext;
    }

    /**
     * Clear handlers before parsing a request/response message.
     */
    protected void clearHandlers() {
        // Clear all handlers
        onHeaders.clear();
        onData = new ReadingCompletionHandler();
        onTrailers.clear();
    }

    /**
     * <p>Registers a handler to receive headers.</p>
     *
     * @param handler handler
     */
    public void onHeaders(final CompletionHandler<Map<String, List<String>>, Void> handler) {
        onHeaders.add(handler);
    }

    /**
     * <p>Registers a handler to receive the message body as zero or several chunks.</p>
     *
     * <p>To avoid buffering, this parser supports only one handler per message.</p>
     *
     * @param handler handler
     */
    public void onData(final CompletionHandler<ByteBuffer, Void> handler) {
        onData = handler;
    }

    /**
     * <p>Registers a handler to receive trailers.</p>
     *
     * @param handler handler
     */
    public void onTrailers(final CompletionHandler<Map<String, List<String>>, Void> handler) {
        onTrailers.add(handler);
    }

    /**
     * Stops further processing and closes the connection.
     */
    protected void shutdown() {
        try {
            logger.severe("Closing the connection");
            channel.close();
        }
        catch(IOException ioe) {
            logger.log(Level.WARNING, ioe.getMessage(), ioe);
            ioe.printStackTrace();
        }
    }

    /**
     * Find headers/trailers, and invoke handlers.
     *
     * @param isTrailers true for trailers, false for headers
     */
    protected void findHeaders(final boolean isTrailers) {
        final List<String> lines = new ArrayList<String>(1);
        headers = new LinkedHashMap<String, List<String>>();

        final List<CompletionHandler<Map<String, List<String>>, Void>> handlers = isTrailers ? onTrailers : onHeaders;

        onLine(new CompletionHandler<StringBuilder, Void>() {
            @Override
            public void completed(final StringBuilder result, final Void attachment) {
                if(result.length() == 2 && result.charAt(0) == HttpCodecUtil.CR && result.charAt(1) == HttpCodecUtil.LF ||
                    result.length() == 1 && result.charAt(0) == HttpCodecUtil.LF) {
                    try {
                        for(final CompletionHandler<Map<String, List<String>>, Void> handler : handlers) {
                            try {
                                handler.completed(headers, null);
                            }
                            catch(Throwable t) {
                                logger.log(Level.WARNING, t.getMessage(), t);
                            }
                        }
                    }
                    finally {
                        if(isTrailers) {
                            readNext();
                        }
                        else {
                            findData();
                        }
                    }
                }
                else {
                    final String line = result.toString();

                    final char firstChar = result.charAt(0);
                    if((firstChar == ' ' || firstChar == '\t')) {
                        // We'll process this line in the next round.
                        lines.add(result.toString().trim());
                    }
                    else {
                        // If lines is not empty, process those first
                        if(lines.size() > 0) {
                            final StringBuilder builder = new StringBuilder(lines.get(0).length());
                            for(final String each : lines) {
                                builder.append(each);
                            }
                            parseHeaderLine(builder.toString(), headers);
                            lines.clear();
                        }
                        else {
                            parseHeaderLine(line, headers);
                        }
                    }
                    onLine(this, new StringBuilder(), maxHeaderLineSize);
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    for(final CompletionHandler<Map<String, List<String>>, Void> handler : handlers) {
                        try {
                            handler.failed(exc, null);
                        }
                        catch(Throwable t) {
                            logger.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                }
                finally {
                    shutdown();
                }
            }
        }, new StringBuilder(), maxHeaderLineSize);
    }

    /**
     * Find message body or chunks and invoke the handler
     */
    private void findData() {
        if(isChunked()) {
            readChunk();
        }
        else {
            // Read contentLength - readBuffer.remaining() bytes and create a single bytebuffer
            final int contentLength = getContentLength(headers);
            if(contentLength > 0) {
                if(contentLength <= bytesRemaining.get()) {
                    sendDataToApp(contentLength);

                    onLine(new CompletionHandler<StringBuilder, Void>() {
                        @Override
                        public void completed(final StringBuilder result, final Void attachment) {
                            findTrailers();
                        }

                        @Override
                        public void failed(final Throwable exc, final Void attachment) {
                            try {
                                onData.failed(exc, null);
                            }
                            catch(Throwable t) {
                                logger.log(Level.WARNING, t.getMessage(), t);
                            }
                            finally {
                                findTrailers();
                            }
                        }
                    }, new StringBuilder(), maxHeaderLineSize);
                }
                else if(contentLength > bytesRemaining.get()) {
                    // Keep reading as many times as needed to get chunkSize bytes
                    final int pos = bytesRemaining.get();
                    sendDataToApp(bytesRemaining.get());
                    readSome(contentLength - pos);
                }
            }
            else {
                onData.completed(EMPTY_BUFFER, null);
                findTrailers();
            }
        }
    }

    /**
     * Request the next message
     */
    abstract protected void readNext();

    /**
     * Find trailers and invoke handlers.
     */
    private void findTrailers() {
        if(isChunked()) {
            findHeaders(true);
        }
        else {
            readNext();
        }
    }

    // TODO: Multiple headers on a singe line not supported yet
    private static void parseHeaderLine(final String line, final Map<String, List<String>> headers) {
        final String[] header = splitHeader(line);
        final String name = header[0].toLowerCase();
        final String value = header[1];
        if(name != null) {
            List<String> values = headers.get(name);
            if(values == null) {
                values = new ArrayList<String>();
                headers.put(name, values);
            }
            values.add(value);
        }
    }

    protected void onLine(final CompletionHandler<StringBuilder, Void> handler, final StringBuilder line, final int limit) {
        if(first.get() || bytesRemaining.get() == 0) {
            first.compareAndSet(true, false);
            readBuffer.clear();
            this.channel.read(readBuffer, timeout, timeUnit, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(final Integer result, final Object attachment) {
                    bytesRemaining.set(result);
                    if(result > 0) {
                        readBuffer.rewind();
                        inflightLine(line, limit, handler);
                    }
                }

                @Override
                public void failed(final Throwable exc, final Object attachment) {
                    if(exc instanceof InterruptedByTimeoutException) {
                        logger.log(Level.WARNING, exc.getMessage(), exc);
                    }
                    else if(exc instanceof ClosedChannelException) {
                        // This is usual.
                    }
                    else {
                        try {
                            handler.failed(exc, null);
                        }
                        catch(Throwable t) {
                            logger.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                }
            });
        }
        else {
            inflightLine(line, limit, handler);
        }
    }

    private byte getAByte() {
        byte b = -1;
        if(bytesRemaining.get() > 0) {
            bytesRemaining.decrementAndGet();
            b = readBuffer.get();
        }
        return b;
    }

    private void inflightLine(final StringBuilder line, final int limit, final CompletionHandler<StringBuilder, Void> handler) {
        boolean found = false;
        final int pos = readBuffer.position();
        while(bytesRemaining.get() > 0) {
            byte nextByte = getAByte();
            if(nextByte == HttpCodecUtil.CR) {
                nextByte = getAByte();
                if(nextByte == HttpCodecUtil.LF) {
                    found = true;
                    break;
                }
            }
            else if(nextByte == HttpCodecUtil.LF) {
                found = true;
                break;
            }
            else if(nextByte == -1) {
                found = true;
                break;
            }
        }

        line.append(new String(readBuffer.array(), pos, readBuffer.position() - pos, Charset.forName("US-ASCII")));
        if(line.length() >= limit) {
            try {
                handler.failed(new IOException("Line too long - exceed " + limit), null);
            }
            catch(Throwable t) {
                logger.log(Level.WARNING, t.getMessage(), t);
            }
        }

        if(found) {
            try {
                handler.completed(line, null);
            }
            catch(Throwable t) {
                logger.log(Level.WARNING, t.getMessage(), t);
            }
        }
        else {
            // Call recursively to read more
            onLine(handler, line, limit);
        }
    }

    // Non-blocking chunk reader - reads some bytes, and sends min(read, chunkSize) to the app
    private void readChunk() {
        // Read the first line to determine chunk size
        onLine(new CompletionHandler<StringBuilder, Void>() {
            @Override
            public void completed(final StringBuilder result, final Void attachment) {
                final String line = result.toString();
                final int chunkSize = getChunkSize(line);
                logger.info("chunk size: " + chunkSize + " bytesRemaining: " + bytesRemaining.get());
                if(chunkSize >= maxChunkSize) {
                    try {
                        onData.failed(new IOException("Chunk size larger than " + maxChunkSize), null);
                    }
                    catch(Throwable t) {
                        logger.log(Level.WARNING, t.getMessage(), t);
                    }
                }
                else if(chunkSize == 0) {
                    try {
                        onData.completed(EMPTY_BUFFER, null);
                    }
                    catch(Throwable t) {
                        logger.log(Level.WARNING, t.getMessage(), t);
                    }
                    finally {
                        findTrailers();
                    }
                }
                else if(chunkSize <= bytesRemaining.get()) {
                    sendDataToApp(chunkSize);
                    readEmptyLineAndChunk();
                }
                else if(chunkSize > bytesRemaining.get()) {
                    // Keep reading as many times as needed to get chunkSize bytes
                    final int pos = bytesRemaining.get();
                    sendDataToApp(bytesRemaining.get());
                    readSome(chunkSize - pos);
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    onData.failed(exc, null);
                }
                catch(Throwable t) {
                    logger.log(Level.WARNING, t.getMessage(), t);
                }
                finally {
                    shutdown();
                }
            }
        }, new StringBuilder(), maxHeaderLineSize);

    }

    private void readEmptyLineAndChunk() {
        // Read the empty line and then the next chunk
        onLine(new CompletionHandler<StringBuilder, Void>() {
            @Override
            public void completed(final StringBuilder result, final Void attachment) {
                readChunk();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    onData.failed(exc, null);
                }
                catch(Throwable t) {
                    logger.log(Level.WARNING, t.getMessage(), t);
                }
                finally {
                    findTrailers();
                }
            }
        }, new StringBuilder(), maxHeaderLineSize);
    }

    private void sendDataToApp(final int size) {
        // All the bytes have already been read into readBuffer - send chunkSide bytes to the app
        final int pos = readBuffer.position();
        final byte[] dest = new byte[size];
        System.arraycopy(readBuffer.array(), pos, dest, 0, size);
        try {

            onData.completed(ByteBuffer.wrap(dest), null);
        }
        catch(Throwable t) {
            logger.log(Level.WARNING, t.getMessage(), t);
        }
        // Move to the end of this chunk
        while(readBuffer.position() < pos + size) {
            readBuffer.get();
            bytesRemaining.decrementAndGet();
        }
    }

    private void readSome(final int toRead) {
        // bytesRemaining should be zero now
        assert bytesRemaining.get() == 0;

        // Allocate a readBuffer to hold toRead bytes
        readBuffer = ByteBuffer.allocate(toRead);
        channel.read(readBuffer, timeout, timeUnit, null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(final Integer result, final Object attachment) {
                bytesRemaining.set(result);
                readBuffer.rewind();
                sendDataToApp(Math.min(result, toRead));

                if(result < toRead) {
                    // Read again
                    readSome(toRead - result);
                }
                else if(result >= toRead) {
                    // Read empty line and next chunk
                    readEmptyLineAndChunk();
                }
            }

            @Override
            public void failed(final Throwable exc, final Object attachment) {
                exc.printStackTrace();
            }
        });
    }

    private boolean isChunked() {
        if(headers == null) {
            throw new NullPointerException("No headers");
        }
        final List<String> val = headers.get("transfer-encoding");
        return val != null && val.size() > 0 && "chunked".equals(val.get(0));
    }

    private int getContentLength(final Map<String, List<String>> headers) {
        final Iterable<String> val = headers.get("content-length");
        int length = -1;
        if(val != null) {
            final Iterator<String> it = val.iterator();
            if(it.hasNext()) {
                try {
                    length = Integer.parseInt(it.next());
                }
                catch(NumberFormatException pe) {
                    logger.log(Level.WARNING, pe.getMessage(), pe);
                }
            }
        }
        return length;
    }


    private class NullCompletionHandler<V, A> implements CompletionHandler<V, A> {
        @Override
        public void completed(final V result, final A attachment) {
        }

        @Override
        public void failed(final Throwable exc, final A attachment) {
            logger.log(Level.WARNING, exc.getMessage(), exc);
        }
    }

    private class ReadingCompletionHandler implements CompletionHandler<ByteBuffer, Void> {
        @Override
        public void completed(final ByteBuffer result, final Void attachment) {
            result.position(result.position() + result.remaining());
        }

        @Override
        public void failed(final Throwable exc, final Void attachment) {
            logger.log(Level.WARNING, exc.getMessage(), exc);
        }
    }

    //
    /////////////////////////////////////////////////////////////////////////////
    // Some of the following code is distributed from Netty source line. See the terms of
    // license in NETTY-APACHE-LICENSE
    //
    private static String[] splitHeader(final String sb) {
        final int length = sb.length();
        final int nameStart;
        int nameEnd;
        int colonEnd;
        final int valueStart;
        final int valueEnd;

        nameStart = findNonWhitespace(sb, 0);
        for(nameEnd = nameStart; nameEnd < length; nameEnd++) {
            final char ch = sb.charAt(nameEnd);
            if(ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }

        for(colonEnd = nameEnd; colonEnd < length; colonEnd++) {
            if(sb.charAt(colonEnd) == ':') {
                colonEnd++;
                break;
            }
        }

        valueStart = findNonWhitespace(sb, colonEnd);
        if(valueStart == length) {
            return new String[]{
                sb.substring(nameStart, nameEnd),
                ""
            };
        }

        valueEnd = findEndOfString(sb);
        return new String[]{
            sb.substring(nameStart, nameEnd),
            sb.substring(valueStart, valueEnd)
        };
    }

    protected String[] splitInitialLine(final String sb) {
        final int aStart;
        final int aEnd;
        final int bStart;
        final int bEnd;
        final int cStart;
        final int cEnd;

        aStart = findNonWhitespace(sb, 0);
        aEnd = findWhitespace(sb, aStart);

        bStart = findNonWhitespace(sb, aEnd);
        bEnd = findWhitespace(sb, bStart);

        cStart = findNonWhitespace(sb, bEnd);
        cEnd = findEndOfString(sb);

        return new String[]{
            sb.substring(aStart, aEnd),
            sb.substring(bStart, bEnd),
            cStart < cEnd ? sb.substring(cStart, cEnd) : ""};
    }

    private static int findNonWhitespace(final String sb, final int offset) {
        int result;
        for(result = offset; result < sb.length(); result++) {
            if(!Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    private static int findWhitespace(final String sb, final int offset) {
        int result;
        for(result = offset; result < sb.length(); result++) {
            if(Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    private static int findEndOfString(final String sb) {
        int result;
        for(result = sb.length(); result > 0; result--) {
            if(!Character.isWhitespace(sb.charAt(result - 1))) {
                break;
            }
        }
        return result;
    }

    private static int getChunkSize(String hex) {
        hex = hex.trim();
        for(int i = 0; i < hex.length(); i++) {
            final char c = hex.charAt(i);
            if(c == ';' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                hex = hex.substring(0, i);
                break;
            }
        }

        return Integer.parseInt(hex, 16);
    }
}
