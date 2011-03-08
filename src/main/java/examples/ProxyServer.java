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

package examples;

import org.tini.client.ClientConnection;
import org.tini.client.ClientRequest;
import org.tini.server.HttpServer;
import org.tini.server.ServerRequest;
import org.tini.server.ServerResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;

/**
 * A transparent proxy server - does not support CONNECT.
 *
 * @author Subbu Allamaraju
 */
public class ProxyServer {
    public static void main(final String[] args) throws Exception {
        final HttpServer server = HttpServer.createServer();

        server.use(new Object() {
            public void service(final ServerRequest request, final ServerResponse response) {
                try {
                    final URI uri = new URI(request.getRequestUri());
                    final ClientConnection connection = new ClientConnection();
                    connection.connect(uri.getHost(), uri.getPort(), new CompletionHandler<Void, Void>() {
                        @Override
                        public void completed(final Void result, final Void attachment) {
                            final ClientRequest clientRequest = connection.request(uri.getPath(), request.getMethod());

                            // Copy headers to the origin
                            final Map<String, List<String>> headers = request.getHeaders();
                            for(final String name : headers.keySet()) {
                                final List<String> values = headers.get(name);
                                for(final String value : values) {
                                    clientRequest.addHeader(name, value);
                                }
                            }
                            clientRequest.writeHead();

                            // Send request data to the origin
                            request.onData(new CompletionHandler<ByteBuffer, Void>() {
                                @Override
                                public void completed(final ByteBuffer result, final Void count) {
                                    clientRequest.write(result);
                                }

                                @Override
                                public void failed(final Throwable exc, final Void attachment) {
                                    exc.printStackTrace();
                                }
                            });

                            // Copy resposne from the origin to the client
                            clientRequest.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                                @Override
                                public void completed(final Map<String, List<String>> result, final Void attachment) {
                                    for(final String name : result.keySet()) {
                                        final List<String> values = result.get(name);
                                        for(final String value : values) {
                                            response.addHeader(name, value);
                                        }
                                    }
                                }

                                @Override
                                public void failed(final Throwable exc, final Void attachment) {
                                    exc.printStackTrace();
                                }
                            });

                            // Copy response data from the origin to the client
                            clientRequest.onData(new CompletionHandler<ByteBuffer, Void>() {
                                @Override
                                public void completed(final ByteBuffer result, final Void attachment) {
                                    response.write(result);
                                    if(!result.hasRemaining()) {
                                        response.end();
                                        connection.disconnect();
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
                }
                catch(URISyntaxException use) {
                    use.printStackTrace();
                }
                catch(IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });

        // This blocks
        server.listen(3000);
    }
}
