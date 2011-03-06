package examples.aio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class AsyncClient {

    public static void main(String[] args) throws IOException {
        SocketChannel channel = null;

        try {
            // Setup
            InetSocketAddress socketAddress =
                new InetSocketAddress("localhost", 3000);
            Charset charset =
                Charset.forName("ISO-8859-1");
            CharsetDecoder decoder = charset.newDecoder();
            CharsetEncoder encoder = charset.newEncoder();

            // Allocate buffers
            ByteBuffer buffer =
                ByteBuffer.allocateDirect(1024);
            CharBuffer charBuffer =
                CharBuffer.allocate(1024);

            // Connect
            channel = SocketChannel.open();
            channel.connect(socketAddress);

            // Send request
            String request = "GET / \r\n\r\n";
            channel.write(encoder.encode(CharBuffer.wrap(request)));

            // Read response
            while((channel.read(buffer)) != -1) {
                buffer.flip();
                // Decode buffer
                decoder.decode(buffer, charBuffer, false);
                // Display
                charBuffer.flip();
                System.out.println(charBuffer);
                buffer.clear();
                charBuffer.clear();
            }
        }
        finally {
            if(channel != null) {
                try {
                    channel.close();
                }
                catch(IOException ignored) {
                }
            }
        }

    }
}
