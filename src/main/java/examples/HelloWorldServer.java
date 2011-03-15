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

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * <p>This example shows how to create a server, and say hello to the client.</p>
 *
 * @author Subbu Allamaraju
 */
public class HelloWorldServer {

    /**
     * Main method to start the server.
     *
     * @param args args
     * @throws IOException thrown in case of IO errors
     */
    public static void main(final String[] args) throws IOException {
        // Create an HTTP server.
        final HttpServer server = HttpServer.createServer();

        // Set the idle timeout - after this timeout, open connections from clients will be closed.
        server.setIdleTimeout(60, TimeUnit.SECONDS);

        // Handle requests with URI path "/no-body"
        server.use("/no-body",
            new Object() {
                @GET
                public void get(final ServerRequest request, final ServerResponse response) {
                    response.setStatus(204, "No Content");
                    response.addHeader("Connection", "close");
                    response.writeHead();
                    response.end();
                }
            }
        );

        // Handle requests with path "/close"
        // Test without ab -k
        server.use("/close",
            new Object() {
                @GET
                public void get(final ServerRequest request, final ServerResponse response) {
                    response.setContentType("text/plain; charset=UTF-8");
                    final byte[] content = "hello world".getBytes(Charset.forName("UTF-8"));
                    response.addHeader("Content-Length", Integer.toString(content.length));
                    response.addHeader("Connection", "close");
                    response.write(content);
                    response.end();
                }
            }
        );
        // This is the default handler for the server
        // Test with ab -k
        server.use(new
            Object() {
                @GET
                @PUT
                @DELETE
                @HEAD
                @POST
                public void get(final ServerRequest request, final ServerResponse response) throws UnsupportedEncodingException {
                    response.setContentType("text/plain;charset=UTF-8");
                    final byte[] content = "hello world".getBytes(Charset.forName("UTF-8"));
                    response.addHeader("Content-Length", Integer.toString(content.length));
                    response.write(content);
                    response.end();
                }
            }

        );

        // Specify the port
        server.listen(3000);
    }
}
