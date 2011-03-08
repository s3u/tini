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
import org.tini.server.ServerRequest;
import org.tini.server.ServerResponse;
import org.tini.server.HttpServer;

import java.nio.channels.CompletionHandler;

import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
// TODO
public class KeepAliveServerTest {

    @Test
    public void testServer() {
        if("Darwin".equals(System.getProperty("os.name"))) {
            // Not supported on windows
            return;
        }

        final HttpServer server = HttpServer.createServer();

        server.use(null, new Object() {
            public void service(final ServerRequest request, final ServerResponse response) {
                System.err.println("Received: " + request.getRequestLine().getMethod() + " for " + request.getRequestLine().getUri());
                response.setContentType("text/plain; charset=UTF-8");
                response.addHeader("Connection", "keep-alive");
                response.addHeader("Transfer-Encoding", "chunked");

                // Purposefully blocking to avoid concurrent handling of requests from an open
                // connection.
                final int id = Integer.parseInt(request.getRequestLine().getUri().substring(1));
                try {
                    Thread.sleep(1000/id);
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }

                response.write(request.getRequestLine().getUri());
                response.end();
            }
        });

        server.listen(3000, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                // TODO Send requests using the async client API
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
                fail();
            }
        });

    }


}
