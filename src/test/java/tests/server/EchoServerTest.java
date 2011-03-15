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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tini.client.ClientConnection;
import org.tini.client.ClientRequest;
import org.tini.client.ClientResponse;
import org.tini.server.HttpServer;
import org.tini.server.ServerRequest;
import org.tini.server.ServerResponse;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
public class EchoServerTest {

    public static void main(final String[] args) throws Exception {
        new EchoServerTest().testEcho();
    }

    private final CountDownLatch serverStart = new CountDownLatch(1);
    private final CountDownLatch testLock = new CountDownLatch(3);

    private HttpServer server;
    // Collect all the echo text, and compare it from the client side
    private final List<String> echoed = new ArrayList<String>();

    private void echo(final ServerResponse response, final String line) {
        response.write(line);
        echoed.add(line);
    }

    private void echo(final ServerResponse response, final ByteBuffer bytes) {
        final CharBuffer charBuffer = Charset.forName("UTF-8").decode(bytes);
        final String line = charBuffer.toString();
        echo(response, line);
    }

    @Before
    public void startServer() throws Exception {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        server = HttpServer.createServer();
        server.use(new Object() {
            public void service(final ServerRequest request, final ServerResponse response) {
                response.setContentType("text/plain");
                response.addHeader("Connection", "keep-alive");
                response.addHeader("Transfer-Encoding", "chunked");

                echo(response, request.getRequestLine().toString() + "\n");
                final Map<String, List<String>> headers = request.getHeaders();
                for(final String name : headers.keySet()) {
                    final List<String> values = headers.get(name);
                    for(final String value : values) {
                        echo(response, name + ": " + value + "\n");
                    }
                }
                echo(response, "\r\n");

                // Echo body
                request.onData(new CompletionHandler<ByteBuffer, Void>() {
                    @Override
                    public void completed(final ByteBuffer result, final Void count) {
                        echo(response, result);
                        if(!result.hasRemaining()) {
                            response.end();
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        exc.printStackTrace();
                    }
                });

                // Echo trailers
                request.onTrailers(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        for(final String name : result.keySet()) {
                            final List<String> values = result.get(name);
                            for(final String value : values) {
                                echo(response, name + ": " + value + "\n");
                            }
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        exc.printStackTrace();
                    }
                });
            }
        });

        server.listen(3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                serverStart.countDown();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
            }
        });
    }

    @After
    public void stopServer() throws Exception {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        assertNotNull(server);
        // Wait for tests to finish
        try {
            testLock.await(20, TimeUnit.SECONDS);
            server.shutdown();
        }
        catch(InterruptedException ie) {
            fail("Tests did not finish");
        }
    }

    @Test
    public void testEcho() throws Exception {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        // Wait for the server to start
        try {
            serverStart.await(10, TimeUnit.SECONDS);
        }
        catch(InterruptedException ie) {
            fail("Server did not start");
        }

        final ClientConnection connection = new ClientConnection();
        connection.connect("localhost", 3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                final ClientRequest request = connection.request("/", "GET");
                request.onResponse(new CompletionHandler<ClientResponse, Void>() {
                    @Override
                    public void completed(final ClientResponse response, final Void attachment) {
                        final StringBuilder resp = new StringBuilder();

                        response.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                            @Override
                            public void completed(final Map<String, List<String>> result, final Void attachment) {
                                assertNotNull(result.containsKey("connection"));
                                assertEquals("keep-alive", result.get("connection").get(0));
                                assertNotNull(result.containsKey("content-type"));
                                assertEquals("text/plain", result.get("content-type").get(0));
                                testLock.countDown();
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
                                    final StringBuilder expected = new StringBuilder();
                                    for(final String line : echoed) {
                                        expected.append(line);
                                    }
                                    assertEquals(expected.toString(), resp.toString());
                                    testLock.countDown();
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
                                testLock.countDown();
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

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
            }
        });
    }
}
