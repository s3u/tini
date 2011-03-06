/*
 * Copyright (c) 2011 Subbu Allamaraju
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

import org.tini.core.TiniResponse;
import org.tini.core.TiniRequest;
import org.tini.core.Server;
import org.jboss.netty.util.CharsetUtil;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import java.util.Map;

/**
 * A simple app that prints Hello World
 */
public class HelloWorldServer {

    public static void main(String[] args) {
        Server server = Server.createServer();
        server.use("/users", new Object() {
            @GET
            public void get(TiniRequest request, TiniResponse response) {
                response.setContentType("text/plain; charset=UTF-8");
                for(int i = 0; i < 100; i++) {
                    response.write("Hello World".getBytes(CharsetUtil.UTF_8));
                }

                for(int i = 0; i < 100; i++) {
                    response.write("Hello World".getBytes(CharsetUtil.UTF_8));

                }
                response.close();
            }
        });
        // This is the default handler for the server
        server.use(null, new Object() {
            @GET
            @PUT
            @DELETE
            @HEAD
            @POST
            public void get(TiniRequest request, TiniResponse response) {
                response.setContentType("text/html");
                response.write("<p>Hello World</p>");
                response.close();
            }

        });
        server.listen(3000);
    }
}
