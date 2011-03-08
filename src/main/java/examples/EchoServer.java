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

import org.tini.server.ServerRequest;
import org.tini.server.ServerResponse;
import org.tini.server.HttpServer;

import java.net.StandardSocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;

/**
 * Echo app
 */
public class EchoServer {

    public static void main(final String[] args) throws Exception {
        final HttpServer server = HttpServer.createServer();

        // TODO: Test
        server.setOption(StandardSocketOption.SO_KEEPALIVE, true);
        server.setOption(StandardSocketOption.TCP_NODELAY, true);

        server.use(new Object() {
            public void service(final ServerRequest request, final ServerResponse response) {

                response.setContentType("text/html");
                response.addHeader("Connection", "keep-alive");
                response.addHeader("Transfer-Encoding", "chunked");

                // Echo request line - by now it has been read
                response.write(request.getRequestLine().getMethod() + " " + request.getRequestLine().getUri() + " " + request.getRequestLine().getVersion() + "\n");

                // Echo headers
                final Map<String, List<String>> headers = request.getHeaders();
                for(final String name : headers.keySet()) {
                    final List<String> values = headers.get(name);
                    for(final String value : values) {
                        response.write(name + ": " + value + "\n");
                    }
                }
                response.write("\r\n");

                // Echo body
                request.onData(new CompletionHandler<ByteBuffer, Void>() {
                    @Override
                    public void completed(final ByteBuffer result, final Void count) {
                        response.write(result);
                        if(!result.hasRemaining()) {
                            response.end();
                        }
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
