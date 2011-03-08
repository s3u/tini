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

/**
 * Test pipelining
 */
public class PipeliningServer {

    public static void main(final String[] args) throws Exception {
        final HttpServer server = HttpServer.createServer();

        server.use(new Object() {
            public void service(final ServerRequest request, final ServerResponse response) {
                System.err.println("Received: " + request.getRequestLine().getMethod() + " for " + request.getRequestLine().getUri());
                response.setContentType("text/plain; charset=UTF-8");
                response.addHeader("Connection", "keep-alive");
                response.addHeader("Transfer-Encoding", "chunked");

                // Don't block the request thread
                final Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        final int id = Integer.parseInt(request.getRequestLine().getUri().substring(1));
                        try {
                            Thread.sleep(1000/id);
                        }
                        catch(InterruptedException ie) {
                            ie.printStackTrace();
                        }

                        response.write(request.getRequestLine().getUri());
                        System.err.println("Ending " + request.getRequestLine().getUri());
                        response.end();
                    }
                };
                final Thread t = new Thread(runnable);
                t.start();
            }
        });

        // This blocks
        server.listen(3000);
    }
}
