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

package org.tini.common;

import org.tini.parser.HttpCodecUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 *  A writable message consisting of headers, followed by body, followed by trailers.
 *
 * @author Subbu Allamaraju
 */
public abstract class WritableMessage {

    protected static final Logger logger = Logger.getLogger("org.tini.common");

    protected final Map<String, String> headers = new ConcurrentHashMap<String, String>();

    private final ReadWriteLock headerLock = new ReentrantReadWriteLock();
    private volatile boolean headersWritten = false;

    private final ReadWriteLock endLock = new ReentrantReadWriteLock();
    private volatile boolean hasEnded = false;

    protected boolean isChunked = true;
    protected static final Charset US_ASCII = Charset.forName("US-ASCII");
    protected static final byte[] LAST_CHUNK = "0\r\n\r\n".getBytes(US_ASCII);


    final Sink sink;
    private boolean hasBody = false;

    protected WritableMessage(final Sink sink) {
        this.sink = sink;
    }

    /**
     * <p>Adds headers.</p>
     *
     * @param name  name
     * @param value value
     */
    public void addHeader(final String name, final String value) {
        if(name.equalsIgnoreCase("connection") && value.equals("close")) {
            sink.closeWhenDone();
        }
        headers.put(name.toLowerCase(), value);
    }

    /**
     * Adds headers.
     *
     * @param headers headers
     */
    public void addHeaders(final Map<String, List<String>> headers) {
        for(final String name : headers.keySet()) {
            final List<String> values = headers.get(name);
            for(final String value : values) {
                this.addHeader(name, value);
            }
        }
    }

    /**
     * <p>Set the media type of the response.</p>
     *
     * @param type media type
     */
    public void setContentType(final String type) {
        headers.put("content-type", type);
    }

    /**
     * <p>Write some data.
     *
     * @param data bytes
     */
    public void write(final byte[] data) {
        write(ByteBuffer.wrap(data));
    }

    /**
     * Write the {@code data.remaining()} bytes from the given {@link ByteBuffer} starting from
     * {@code data.position()}.
     *
     * @param data bytes
     */
    public void write(final ByteBuffer data) {
        hasBody = true;
        final ByteArrayOutputStream baos = writeHead(doClose());

        try {
            if(isChunked) {
                final String str = Integer.toHexString(data.remaining());// + ";assoc-id=" + chunkId;
                final byte[] boundary = str.getBytes(US_ASCII);
                baos.write(boundary);
                baos.write(HttpCodecUtil.CRLF);
            }
            baos.write(data.array(), data.position(), data.remaining());
            if(isChunked) {
                baos.write(HttpCodecUtil.CRLF);
            }
            sink.write(ByteBuffer.wrap(baos.toByteArray()), null);
        }
        catch(IOException ioe) {
            // ByteArrayOutputStream does not throw this
        }
    }

    /**
     * <p>End the message.
     */
    public void end() {
        endLock.writeLock().lock();
        try {
            if(hasEnded) {
                return;
            }
            final boolean doClose = doClose();
            final ByteArrayOutputStream baos;
            if(!headersWritten) {
                baos = writeHead(doClose);
            }
            else {
                baos = new ByteArrayOutputStream();
            }

            if(isChunked && hasBody) {
                try {
                    baos.write(LAST_CHUNK);
                }
                catch(IOException ioe) {
                    // ByteArrayOutputStream does not throw this
                }
            }
            if(baos.size() > 0) {
                sink.write(ByteBuffer.wrap(baos.toByteArray()), null);
            }
            sink.end();
            hasEnded = true;
        }
        finally {
            endLock.writeLock().unlock();
        }
    }

    protected void setTransferEncoding() {
        if(!hasBody) return;
        if(headers.containsKey("content-length")) {
            headers.remove("transfer-encoding");
            isChunked = false;
        }
        else {
            headers.put("transfer-encoding", "chunked");
            isChunked = true;
        }
    }

    protected abstract boolean doClose();

    public void writeHead() {
        writeHead(null);
    }

    public void writeHead(final CompletionHandler<Integer, Void> handler) {
        final ByteArrayOutputStream baos = writeHead(doClose());
        sink.write(ByteBuffer.wrap(baos.toByteArray()), handler);
    }

    private ByteArrayOutputStream writeHead(final boolean doClose) {
        headerLock.writeLock().lock();
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
            if(headersWritten) {
                return baos;
            }
            setTransferEncoding();
            if(doClose) {
                headers.put("connection", "close");
            }
            else {
                headers.put("connection", "keep-alive");
            }

            // Add Assoc-Req header - see http://tools.ietf.org/html/draft-nottingham-http-pipeline-00
//        if(isChunked) {
//            headers.put("Assoc-Req", request.getHeader("host") + request.getRequestLine().getUri());
//            headers.put("Assoc-Chunk-ID", chunkId);
//        }
            try {
                writeFirstLine(baos);
                baos.write(HttpCodecUtil.CRLF);

                for(final String name : headers.keySet()) {
                    baos.write(name.toLowerCase().getBytes(US_ASCII));
                    baos.write(": ".getBytes(US_ASCII));
                    baos.write(headers.get(name).getBytes(US_ASCII));
                    baos.write(HttpCodecUtil.CRLF);
                    logger.info("Writing header " + name + ": " + headers.get(name));
                }
                baos.write(HttpCodecUtil.CRLF);
            }
            catch(IOException ioe) {
                // ByteArrayOutputStream does not throw this
            }
            headersWritten = true;
            return baos;
        }
        finally {
            headerLock.writeLock().unlock();
        }
    }

    protected abstract void writeFirstLine(ByteArrayOutputStream baos) throws IOException;
}
