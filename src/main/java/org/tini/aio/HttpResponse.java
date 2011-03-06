package org.tini.aio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP 1.1 response
 */
// TODO: Fixed length content (i.e., Content-Length)
public class HttpResponse {

    static final Charset US_ASCII = Charset.forName("US-ASCII");

    private int status = 200;
    private String message = "OK";
    private AtomicBoolean headersWritten = new AtomicBoolean(false);
    private AtomicBoolean closed = new AtomicBoolean(false);

    private static final ByteBuffer LAST_CHUNK =
        ByteBuffer.wrap("0\r\n\r\n".getBytes(US_ASCII));
    static final byte[] CRLF = new byte[]{13, 10};

    private Map<String, String> headers = new ConcurrentHashMap<String, String>();

    private final AsynchronousSocketChannel channel;

    protected HttpResponse(AsynchronousSocketChannel channel) {
        this.channel = channel;
    }

    /**
     * <p>Set the status code and message</p>
     * @param status status code
     * @param message status message
     */
    public void setStatus(int status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * <p>Set the media type of the response.</p>
     *
     * @param type media type
     */
    public void setContentType(String type) {
        headers.put("content-type", type);
    }

    /**
     * <p>Add a response header.</p>
     *
     * @param name name
     * @param value value
     */
    public void addHeader(String name, String value) {
        headers.put(name, value);
    }

    /**
     * <p>Write some text. The text will be encoded using UTF-8.</p>
     *
     * @param body body
     */
    public void write(String body) {
        write(body.getBytes(Charset.forName("UTF-8")));
    }

    /**
     * <p>Write some bytes.
     *
     * @param body bytes
     */
    public void write(byte[] body) {
        writeHead();

        String str = Integer.toHexString(body.length);
        channel.write(ByteBuffer.wrap(str.getBytes(US_ASCII)));
        channel.write(ByteBuffer.wrap(CRLF));
        channel.write(ByteBuffer.wrap(body));
        channel.write(ByteBuffer.wrap(CRLF));
    }

    /**
     * <p>End the response.
     */
    public void end() {
        if(closed.get()) {
            return;
        }
        writeHead();
        LAST_CHUNK.rewind();
        channel.write(LAST_CHUNK);
    }

    private void writeHead() {
        if(headersWritten.get()) {
            return;
        }
        if(!headers.containsKey("content-length")) {
            headers.put("Transfer-Encoding", "chunked");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write("HTTP/1.1".getBytes(US_ASCII));
            baos.write(HttpCodecUtil.SP);
            baos.write(String.valueOf(this.status).getBytes(US_ASCII));
            baos.write(HttpCodecUtil.SP);
            baos.write(this.message.getBytes(US_ASCII));
            baos.write(HttpCodecUtil.SP);
            baos.write(HttpCodecUtil.CRLF);

            for(String name : headers.keySet()) {
                baos.write(name.toLowerCase().getBytes(US_ASCII));
                baos.write(": ".getBytes(US_ASCII));
                baos.write(headers.get(name).getBytes(US_ASCII));
                baos.write(HttpCodecUtil.CRLF);
            }

            baos.write(HttpCodecUtil.CRLF);
            channel.write(ByteBuffer.wrap(baos.toByteArray()));
        }
        catch(IOException ioe) {
            // Should not happen here
        }
        headersWritten.set(true);
    }
}
