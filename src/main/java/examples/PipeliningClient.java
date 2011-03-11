package examples;

import org.tini.client.ClientConnection;
import org.tini.client.ClientRequest;
import org.tini.client.ClientResponse;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Open one connection, and keep sending requests using the same connection.
 * <p/>
 * Expect the client to pipeline (buffer or multiplex) automatically.
 */
public class PipeliningClient {

    public static void main(final String[] args) throws URISyntaxException {

        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch lock = new CountDownLatch(10);

        final ClientConnection connection = new ClientConnection();
        connection.connect("localhost", 3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                for(int i = 0; i < 10; i++) {
                    final ClientRequest request = connection.request("/" + counter.incrementAndGet(), "GET");
                    request.onResponse(new CompletionHandler<ClientResponse, Void>() {
                        @Override
                        public void completed(final ClientResponse response, final Void attachment) {
                            System.err.println(response.getResponseLine().toString());
                            final StringBuilder builder = new StringBuilder();
                            response.onData(new CompletionHandler<ByteBuffer, Void>() {
                                @Override
                                public void completed(final ByteBuffer result, final Void attachment) {
                                    if(result.hasRemaining()) {
                                        final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                                        builder.append(charBuffer);
                                    }
                                    else {
                                        final String body = builder.toString().trim();
                                        final int number = Integer.parseInt(body.substring(1));
                                        if(counter.get() == number) {
                                            System.err.println("In order receivd " + number);
                                        }
                                        else {
                                            System.err.println("Out of order receivd " + number);
                                        }
                                        lock.countDown();
                                    }
                                }

                                @Override
                                public void failed(final Throwable exc, final Void attachment) {
                                    exc.printStackTrace();
                                }
                            });
                        }

                        @Override
                        public void failed(final Throwable exc, final Void attachment) {
                            exc.printStackTrace();
                        }
                    });

                    request.writeHead(); // parsing starts after writing request line and headers
                    request.end();
                }


            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
            }
        });

        try {
            lock.await(10, TimeUnit.SECONDS);
        }
        catch(InterruptedException ie) {
            ie.printStackTrace();
        }
    }
}
