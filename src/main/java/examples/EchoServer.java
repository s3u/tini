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

import org.tini.core.TiniRequest;
import org.tini.core.TiniResponse;
import org.tini.core.Server;

import java.util.Map;

/**
 * Echo app
 */
public class EchoServer {

    public static void main(String[] args) {
        Server server = Server.createServer();

        server.use(null, new Object() {
            public void service(TiniRequest request, TiniResponse response) {
                response.write(request.getMethod().getName() + " " + request.getRequestUri() + " " + request.getProtocolVersion() + "\n");
                for(Map.Entry<String, String> h : request.getHeaders()) {
                    response.write(h.getKey() + ": " + h.getValue() + "\r\n");
                }
                response.write("\r\n");

                // Dump the body
                response.write(request.getBody());

                // Done
                response.close();
            }

        });
        server.listen(3000);
    }
}
