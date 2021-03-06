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
import org.tini.client.ClientResponse;
import org.tini.common.Utils;
import org.tini.server.HttpServer;
import org.tini.server.ServerRequest;
import org.tini.server.ServerResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;

/**
 * A demo transparent proxy server - does not support CONNECT.
 *
 * @author Subbu Allamaraju
 */
public class ProxyServer {
    public static void main(final String[] args) throws Exception {
        final HttpServer server = HttpServer.createServer();
        server.use(new Object() {
            public void service(final ServerRequest request, final ServerResponse response) throws URISyntaxException, IOException {
                final URI uri = new URI(request.getRequestLine().getUri());
                final ClientConnection connection = new ClientConnection();
                connection.connect(uri.getHost(), uri.getPort(), new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(final Void result, final Void attachment) {
                        // Connected, now send a request.
                        final ClientRequest clientRequest = connection.request(uri.getPath(), request.getRequestLine().getMethod(), request.getHeaders());
                        clientRequest.onResponse(new CompletionHandler<ClientResponse, Void>() {
                            @Override
                            public void completed(final ClientResponse clientResponse, final Void attachment) {
                                // Copy headers
                                clientResponse.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                                    @Override
                                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                                        response.addHeaders(result);
                                    }

                                    @Override
                                    public void failed(final Throwable exc, final Void attachment) {
                                        exc.printStackTrace();
                                    }
                                });

                                // Copy response data from the origin to the client
                                Utils.pump(clientResponse, response);
                            }

                            @Override
                            public void failed(final Throwable exc, final Void attachment) {
                                exc.printStackTrace();
                            }
                        });
                        // Copy data to the origin
                        Utils.pump(request, clientRequest);
                        clientRequest.writeHead();
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        exc.printStackTrace();
                    }
                });
            }
        });
        server.listen(3000);
    }
}
