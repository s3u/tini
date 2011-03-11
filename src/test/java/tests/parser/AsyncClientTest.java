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

package tests.parser;

import org.junit.Test;
import org.tini.client.ClientConnection;
import org.tini.client.ClientRequest;
import org.tini.client.ClientResponse;
import org.tini.server.ServerRequest;
import org.tini.server.ServerResponse;
import org.tini.server.HttpServer;

import javax.ws.rs.GET;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
public class AsyncClientTest {

    public static void main(final String[] args) {
        new AsyncClientTest().testGet();
    }

    @Test
    public void testGet() {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        final CountDownLatch lock = new CountDownLatch(5);
        final HttpServer server = HttpServer.createServer();
        server.use(null,
            new Object() {
                @GET
                public void get(final ServerRequest request, final ServerResponse response) throws UnsupportedEncodingException {
                    response.setContentType("text/plain;charset=UTF-8");
                    final byte[] content = "hello world".getBytes(Charset.forName("UTF-8"));
                    response.addHeader("Content-Length", Integer.toString(content.length));
                    response.write(content);
                    response.end();
                }
            });
        server.listen(3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                lock.countDown();

                final ClientConnection client = new ClientConnection();
                final StringBuilder resp = new StringBuilder();
                client.connect("localhost", 3000, new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(final Void result, final Void attachment) {
                        System.err.println("Connected");
                        // Send a request
                        final ClientRequest request = client.request("/", "GET");
                        request.onResponse(new CompletionHandler<ClientResponse, Void>() {
                            @Override
                            public void completed(final ClientResponse response, final Void attachment) {
                                System.err.println(response.getResponseLine().getCode());
                                assertEquals(200, response.getResponseLine().getCode());
                                lock.countDown();

                                response.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                                    @Override
                                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                                        System.err.println(result.size());
                                        assertEquals(5, result.size());
                                        lock.countDown();
                                    }

                                    @Override
                                    public void failed(final Throwable exc, final Void attachment) {
                                        exc.printStackTrace();
                                        fail();
                                    }
                                });
                                response.onData(new CompletionHandler<ByteBuffer, Void>() {
                                    @Override
                                    public void completed(final ByteBuffer result, final Void attachment) {
                                        final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                                        resp.append(charBuffer.toString());
                                        if(result.remaining() == 0) {
                                            assertEquals("hello world", resp.toString());
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
                        request.addHeader("Host", "localhost");
                        request.writeHead();
                        request.end();
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
            try {
                server.shutdown();
            }
            catch(IOException ioe) {
                ioe.printStackTrace();
            }
            assertEquals(0, lock.getCount());
        }
    }

    //@Test
    public void testGetChunked() {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        final CountDownLatch lock = new CountDownLatch(6);
        final HttpServer server = HttpServer.createServer();
        server.use("/",
            new Object() {
                @GET
                public void get(final ServerRequest request, final ServerResponse response) {
                    response.setContentType("text/plain;charset=UTF-8");
                    response.write("hello".getBytes(Charset.forName("UTF-8")));
                    response.write("world".getBytes(Charset.forName("UTF-8")));
                    response.end();

                }
            });
        server.listen(3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                lock.countDown();

                final ClientConnection client = new ClientConnection();
                client.connect("localhost", 3000, new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(final Void result, final Void attachment) {
                        // Send a request
                        final ClientRequest request = client.request("/", "GET");
                        request.onResponse(new CompletionHandler<ClientResponse, Void>() {
                            @Override
                            public void completed(final ClientResponse response, final Void attachment) {
                                response.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                                    @Override
                                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                                        assertEquals(5, result.size());
                                        assertEquals(1, result.get("transfer-encoding").size());
                                        assertEquals("chunked", result.get("transfer-encoding").get(0));
                                        lock.countDown();
                                    }

                                    @Override
                                    public void failed(final Throwable exc, final Void attachment) {
                                        exc.printStackTrace();
                                        fail();
                                    }
                                });
                                response.onData(new CompletionHandler<ByteBuffer, Void>() {
                                    @Override
                                    public void completed(final ByteBuffer result, final Void attachment) {
                                        // TODO
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

                        request.addHeader("Host", "localhost");
                        request.writeHead();
                        request.end();

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
            try {
                server.shutdown();
            }
            catch(IOException ioe) {
                ioe.printStackTrace();
            }
            assertEquals(0, lock.getCount());
        }
    }
}
