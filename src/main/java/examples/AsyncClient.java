package examples;

import org.tini.client.ClientConnection;
import org.tini.client.ClientRequest;
import org.tini.parser.ResponseLine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Async client example
 */
public class AsyncClient {

    public static void main(final String[] args) {

        final CountDownLatch lock = new CountDownLatch(1);

        String host = "www.subbu.org";
        int port = 80;
        String _path = "/";
        if(args.length > 0 && args[0] != null) {
            try {
                final URI uri = new URI(args[0]);
                host = uri.getHost();
                port = uri.getPort() == -1 ? 80 : uri.getPort();
                _path = uri.getPath() == null ? "" : uri.getPath();
            }
            catch(URISyntaxException use) {
            }
        }

        final String path = _path;
        final ClientConnection connection = new ClientConnection();
        try {
            connection.connect(host, port, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(final Void result, final Void attachment) {
                    final ClientRequest request = connection.request(path, "GET");
                    request.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
                        @Override
                        public void completed(final ResponseLine result, final Void attachment) {
                            System.err.println(result.toString());
                        }

                        @Override
                        public void failed(final Throwable exc, final Void attachment) {
                            exc.printStackTrace();
                        }
                    });
                    request.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                        @Override
                        public void completed(final Map<String, List<String>> result, final Void attachment) {
                            for(final String key : result.keySet()) {
                                for(final String val : result.get(key)) {
                                    System.err.println(key + ": " + val);
                                }
                            }
                        }

                        @Override
                        public void failed(final Throwable exc, final Void attachment) {
                            exc.printStackTrace();
                        }
                    });
                    request.onData(new CompletionHandler<ByteBuffer, Void>() {
                        @Override
                        public void completed(final ByteBuffer result, final Void attachment) {
                            if(result.hasRemaining()) {
                                final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                                System.err.println(charBuffer.toString());
                            }
                            else {
                                try {
                                    connection.disconnect();
                                }
                                finally {
                                    lock.countDown();
                                }
                            }
                        }

                        @Override
                        public void failed(final Throwable exc, final Void attachment) {
                            exc.printStackTrace();
                        }
                    });

                    request.writeHead(); // parsing starts after writing request line and headers
                    request.end();

                }

                @Override
                public void failed(final Throwable exc, final Void attachment) {
                    exc.printStackTrace();
                }
            });
        }
        catch(IOException ioe) {
            ioe.printStackTrace();
        }

        synchronized(lock) {
            try {
                lock.await(10, TimeUnit.SECONDS);
            }
            catch(InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }
}
