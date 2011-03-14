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

package tests.server;

import org.junit.Test;
import org.tini.client.ClientConnection;
import org.tini.client.ClientRequest;
import org.tini.client.ClientResponse;
import org.tini.server.ServerRequest;
import org.tini.server.ServerResponse;
import org.tini.server.HttpServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
public class KeepAliveServerTest {

    public static void main(final String[] args) {
        new KeepAliveServerTest().testSendInSequence();
    }

    @Test
    public void testSendInSequence() {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        final HttpServer server = HttpServer.createServer();
        final List<String> paths = Arrays.asList("/foo", "/bar");
        for(final String path : paths) {
            server.use(path, new Handler());
        }

        final CountDownLatch lock = new CountDownLatch(3 * paths.size());

        server.listen(3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                // After the server started, connect to the server, and send requests
                final ClientConnection connection = new ClientConnection();
                connection.connect("localhost", 3000, new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(final Void result, final Void attachment) {
                        sendRequest(paths.iterator(), connection, lock, true);
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
                fail();
            }
        });

        try {
            lock.await(1000, TimeUnit.SECONDS);
            try {
                server.shutdown();
            }
            catch(IOException ioe) {
                fail();
            }
        }
        catch(InterruptedException ie) {
            fail("Pending tests");
        }
        finally {
            assertEquals(0, lock.getCount());
        }
    }


    @Test
    // Just write them in order without waiting for the previous request to finish
    public void testSend() {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        final HttpServer server = HttpServer.createServer();
        final List<String> paths = Arrays.asList("/foo", "/bar", "/baz");
        for(final String path : paths) {
            server.use(path, new Handler());
        }

        final CountDownLatch lock = new CountDownLatch(3 * paths.size());

        server.listen(3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                // After the server started, connect to the server, and send requests
                final ClientConnection connection = new ClientConnection();
                connection.connect("localhost", 3000, new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(final Void result, final Void attachment) {
                        final Iterator iterator = paths.iterator();
                        while(iterator.hasNext()) {
                            sendRequest(iterator, connection, lock, false);
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
                fail();
            }
        });

        try {
            lock.await(1000, TimeUnit.SECONDS);
            try {
                server.shutdown();
            }
            catch(IOException ioe) {
                fail();
            }
        }
        catch(InterruptedException ie) {
            fail("Pending tests");
        }
        finally {
            assertEquals(0, lock.getCount());
        }
    }

    private void sendRequest(final Iterator<String> iterator, final ClientConnection connection, final CountDownLatch lock,
                             final boolean doContinue) {
        if(!iterator.hasNext()) {
            return;
        }
        final String path = iterator.next();
        final ClientRequest request = connection.request(path, "GET");
        request.onResponse(new CompletionHandler<ClientResponse, Void>() {
            @Override
            public void completed(final ClientResponse response, final Void attachment) {
                final StringBuilder resp = new StringBuilder();

                response.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        assertEquals(5, result.size());
                        lock.countDown();
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        exc.printStackTrace();
                    }
                });
                response.onData(new CompletionHandler<ByteBuffer, Void>() {
                    @Override
                    public void completed(final ByteBuffer result, final Void attachment) {
                        if(result.remaining() == 0) {
                            assertEquals(path, resp.toString());
                            lock.countDown();

                            if(doContinue) {
                                sendRequest(iterator, connection, lock, doContinue);
                            }
                        }
                        else {
                            final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                            resp.append(charBuffer);
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        exc.printStackTrace();
                    }
                });
                response.onTrailers(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        assertEquals(0, result.size());
                        lock.countDown();
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
        // TODO: Not clean
        request.writeHead();
        request.end();
    }

    class Handler {
        public void service(final ServerRequest request, final ServerResponse response) {
            response.setContentType("text/plain; charset=UTF-8");
            response.addHeader("Connection", "keep-alive");
            response.addHeader("Transfer-Encoding", "chunked");
            response.write(request.getRequestLine().getUri());
            response.end();
        }
    }
}
