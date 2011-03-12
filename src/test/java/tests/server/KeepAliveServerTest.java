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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
public class KeepAliveServerTest {

    public static void main(final String[] args) {
        new KeepAliveServerTest().testServer();
    }

    @Test
    public void testServer() {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        final HttpServer server = HttpServer.createServer();

        final List<String> paths = Arrays.asList("/foo", "/bar", "/baz");
        for(final String path : paths) {
            server.use(path, new Handler());
        }

        final CountDownLatch lock = new CountDownLatch(paths.size());

        server.listen(3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                final ClientConnection connection = new ClientConnection();
                connection.connect("localhost", 3000, new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(final Void result, final Void attachment) {
                        System.err.println("---> connected");

                        sendRequest(paths.iterator(), connection, lock);
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
            lock.await(10, TimeUnit.SECONDS);
        }
        catch(InterruptedException ie) {
            fail("Pending tests");
        }
        finally {
            assertEquals(0, lock.getCount());
        }
    }

    private void sendRequest(final Iterator<String> iterator, final ClientConnection connection, final CountDownLatch lock) {
        if(!iterator.hasNext()) {
            return;
        }
        final String path = iterator.next();
        final ClientRequest request = connection.request(path, "GET");
        request.onResponse(new CompletionHandler<ClientResponse, Void>() {
            @Override
            public void completed(final ClientResponse response, final Void attachment) {
                System.err.println("---> got response");
                final StringBuilder resp = new StringBuilder();
                response.onData(new CompletionHandler<ByteBuffer, Void>() {
                    @Override
                    public void completed(final ByteBuffer result, final Void attachment) {
                        System.err.println("---> got data " + result.remaining());
                        if(result.remaining() == 0) {
                            assertEquals(path, resp.toString());
                            lock.countDown();

                            // Now need to start the next request
                            sendRequest(iterator, connection, lock);
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
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
            }
        });
        request.writeHead();
    }

    class Handler {
        public void service(final ServerRequest request, final ServerResponse response) {
            System.err.println("Received: " + request.getRequestLine().getMethod() + " for " + request.getRequestLine().getUri());
            response.setContentType("text/plain; charset=UTF-8");
            response.addHeader("Connection", "keep-alive");
            response.addHeader("Transfer-Encoding", "chunked");
            response.write(request.getRequestLine().getUri());
            response.end();
        }
    }
}
