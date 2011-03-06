package org.tini.aio;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP 1.1 request
 */
public class HttpRequest {

    private String version = "1.1";
    private String method;
    private String requestUri;
    private Map<String, String> headers = new LinkedHashMap<String, String>();

    private final int maxInitialLineLength = 8192;
    private final int maxHeaderSize = 8192;
    private final int maxChunkSize = 8192;

    private final AsynchronousSocketChannel channel;
    private ByteBuffer readBuffer = ByteBuffer.allocate(128);

    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);

    private AtomicInteger headerSize = new AtomicInteger();

    protected HttpRequest(AsynchronousSocketChannel channel) throws InterruptedException, ExecutionException {
        this.channel = channel;

        // Initialize
        this.channel.read(readBuffer).get();
        readBuffer.rewind();

        // Read first line and headers
        readRequestLine();
        readHeaders();

        // Body and Trailers will be read on demand
    }

    /**
     * <p>Returns the request method.</p>
     *
     * @return method
     */
    public String getMethod() {
        return this.method;
    }

    /**
     * <p>Returs the request URI.</p>
     *
     * @return request URI
     */
    public String getRequestUri() {
        return this.requestUri;
    }

    /**
     * <p>Returns HTTP version.</p>
     *
     * @return version
     */
    public String getVersion() {
        return version;
    }

    /**
     * <p>Returns request headers.</p>
     *
     * @return headers
     */
    // TODO: Multiple value support
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * <p>Registers a handler to receive HTTP request body. If there is no body, the handler will be
     * invoked immediately.</p>
     * <p/>
     * <p>When the requst body uses chunked encoding, the handler will be invoked once per chunk
     * received. This method does not resize chunks.</p>
     *
     * @param handler handler
     */
    // TODO: Change the stream to something else. Not even ByteBuffer.
    public void onData(final CompletionHandler<InputStream, Integer> handler) {
        if(isChunked()) {
            readChunk(handler);
        }
        else {
            // Read contentLength - readBuffer.remaining() bytes and create a single bytebuffer
            // TODO: Account for missing content length - fail the requestin this case
            final int contentLength = getContentLength();
            if(contentLength > 0) {
                final ByteBuffer res = ByteBuffer.allocate(Math.max(contentLength, readBuffer.remaining()));
                res.put(readBuffer.array(), readBuffer.position(), readBuffer.remaining());
                this.channel.read(res, null, new CompletionHandler<Integer, Object>() {
                    @Override
                    public void completed(Integer result, Object attachment) {
                        handler.completed(new ByteArrayInputStream(res.array()), result);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {
                        exc.printStackTrace();
                    }
                });
            }
            else {
                handler.completed(EMPTY_STREAM, 0);
            }
        }
    }

    /**
     * <p>Returns true if the body is chunked.</p>
     *
     * @return boolean
     */
    public boolean isChunked() {
        return "chunked".equals(headers.get("transfer-encoding"));
    }

    /**
     * <p>Returns content length if specified by the client via the <code>Content-Length</code>
     * header. Returns -1 if unspecified.</p>
     *
     * @return content length
     */
    public int getContentLength() {
        String val = headers.get("content-length");
        if(val != null) {
            try {
                return Integer.parseInt(val);
            }
            catch(NumberFormatException pe) {
                return -1;
            }
        }
        else {
            return -1;
        }
    }

    // Blocking call
    private byte read() throws InterruptedException, ExecutionException {
        byte b = -1;
        if(readBuffer.hasRemaining()) {
            b = readBuffer.get();
        }
        else {
            // Read more into the buffer
            readBuffer.clear();
            int read = this.channel.read(readBuffer).get();
            if(read > 0) {
                readBuffer.rewind();
                readBuffer = ByteBuffer.wrap(readBuffer.array(), 0, read);
                b = readBuffer.get();
            }
        }
        return b;
    }

    // Blocking call
    private void readRequestLine() throws InterruptedException, ExecutionException {
        String firstLine = readLine();
        String[] initialLine = splitInitialLine(firstLine);
        if(initialLine.length == 3) {
            this.method = initialLine[0];
            this.requestUri = initialLine[1];
            this.version = initialLine[2];
        }
        else {
            // TODO: Foul request - respond with 400
        }
    }

    // Non-blocking chunk reader
    // TODO: Avoid recursion
    private void readChunk(final CompletionHandler<InputStream, Integer> handler) {
        // Read the first line to determine chunk size
        String line = null;
        try {
            line = readLine();
        }
        catch(Exception e) {
            handler.failed(e, null);
        }
        final int chunkSize = getChunkSize(line);

        if(chunkSize == 0) {
            // Read trailers - TODO
            handler.completed(EMPTY_STREAM, 0);
        }
        else if(chunkSize <= readBuffer.remaining()) {
            // If all the bytes have already been read into readBuffer
            final int pos = readBuffer.position();
            // ByteBuffer wrapper = ByteBuffer.wrap(readBuffer.array(), readBuffer.position(), chunkSize);
            InputStream wrapper = new ByteArrayInputStream(readBuffer.array(), readBuffer.position(), chunkSize);
            handler.completed(wrapper, chunkSize);

            // Move to the end of this chunk if the app has not done so already
            while(readBuffer.position() < pos + chunkSize) {
                readBuffer.get();
            }
            // Read the next chunk
            try {
                readLine();
            }
            catch(Exception e) {
                handler.failed(e, null);
            }
            readChunk(handler);
        }
        else if(chunkSize > readBuffer.remaining()) {
            // If only some have been read into readBuffer,
            // allocate a new buffer of chunkSize, and copy remaining bytes into it
            // and then read toRead bytes
            int toRead = chunkSize - readBuffer.remaining();
            final ByteBuffer temp = ByteBuffer.allocate(chunkSize);
            int remaining = readBuffer.remaining();
            for(int i = 0; i < remaining; i++) {
                temp.put(readBuffer.get());
            }
            this.readBuffer = temp;
            this.channel.read(readBuffer, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    InputStream wrapper = new ByteArrayInputStream(readBuffer.array(), 0, chunkSize);
                    // ByteBuffer wrapper = ByteBuffer.wrap(readBuffer.array(), 0, chunkSize);
//                    wrapper.rewind();
                    handler.completed(wrapper, chunkSize);

                    // Move to the end of this chunk if the app has not done so already
                    while(readBuffer.hasRemaining()) {
                        readBuffer.get();
                    }
                    // Read the next chunk
                    try {
                        readLine();
                    }
                    catch(Exception e) {
                        handler.failed(e, null);
                    }
                    readChunk(handler);
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    exc.printStackTrace();
                }
            });

        }
    }

    //
    /////////////////////////////////////////////////////////////////////////////
    // Some of the following code is distributed from Netty source line. See the terms of
    // license in netty-apache-license
    //
    private void readHeaders() throws InterruptedException, ExecutionException {
        String line = readHeader();
        String name = null;
        String value = null;
        if(line.length() != 0) {
            do {
                char firstChar = line.charAt(0);
                if(name != null && (firstChar == ' ' || firstChar == '\t')) {
                    value = value + ' ' + line.trim();
                }
                else {
                    if(name != null) {
                        headers.put(name.toLowerCase(), value);
                    }
                    String[] header = splitHeader(line);
                    name = header[0];
                    value = header[1];
                }

                line = readHeader();
            }
            while(line.length() != 0);

            // Add the last header.
            if(name != null) {
                headers.put(name.toLowerCase(), value);
            }
        }
    }

    private String readHeader() throws InterruptedException, ExecutionException {
        StringBuilder sb = new StringBuilder(64);

        loop:
        for(; ;) {
            char nextByte = (char) read();
            headerSize.incrementAndGet();

            switch(nextByte) {
                case HttpCodecUtil.CR:
                    nextByte = (char) read();
                    headerSize.incrementAndGet();
                    if(nextByte == HttpCodecUtil.LF) {
                        break loop;
                    }
                    break;
                case HttpCodecUtil.LF:
                    break loop;
            }

            // Abort decoding if the header part is too large.
            if(headerSize.get() >= maxHeaderSize) {
                // TODO: respond with 400
            }

            sb.append(nextByte);
        }
        return sb.toString();
    }

    private String[] splitHeader(String sb) {
        final int length = sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart = findNonWhitespace(sb, 0);
        for(nameEnd = nameStart; nameEnd < length; nameEnd++) {
            char ch = sb.charAt(nameEnd);
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

    private String readLine() throws InterruptedException, ExecutionException {
        StringBuilder sb = new StringBuilder(64);
        int lineLength = 0;
        while(true) {
            byte nextByte = read();
            if(nextByte == HttpCodecUtil.CR) {
                nextByte = read();
                if(nextByte == HttpCodecUtil.LF) {
                    return sb.toString();
                }
            }
            else if(nextByte == HttpCodecUtil.LF) {
                return sb.toString();
            }
            else if(nextByte == -1) {
                return sb.toString();
            }
            else {
                if(lineLength >= maxInitialLineLength) {
                    // TODO return 400
                }
                lineLength++;
                sb.append((char) nextByte);
            }
        }
    }

    private String[] splitInitialLine(String sb) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        int cStart;
        int cEnd;

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

    private int findNonWhitespace(String sb, int offset) {
        int result;
        for(result = offset; result < sb.length(); result++) {
            if(!Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    private int findWhitespace(String sb, int offset) {
        int result;
        for(result = offset; result < sb.length(); result++) {
            if(Character.isWhitespace(sb.charAt(result))) {
                break;
            }
        }
        return result;
    }

    private int findEndOfString(String sb) {
        int result;
        for(result = sb.length(); result > 0; result--) {
            if(!Character.isWhitespace(sb.charAt(result - 1))) {
                break;
            }
        }
        return result;
    }

    private int getChunkSize(String hex) {
        hex = hex.trim();
        for(int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if(c == ';' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                hex = hex.substring(0, i);
                break;
            }
        }

        return Integer.parseInt(hex, 16);
    }
}
