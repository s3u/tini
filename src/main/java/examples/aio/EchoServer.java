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

package examples.aio;

import org.tini.aio.HttpRequest;
import org.tini.aio.HttpResponse;
import org.tini.aio.Server;

import java.awt.image.ImagingOpException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

/**
 * Echo app
 */
public class EchoServer {

    public static void main(String[] args) throws Exception {
        Server server = Server.createServer();

        server.use(null, new Object() {
            public void service(final HttpRequest request, final HttpResponse response) {

                response.setContentType("text/html");
                response.addHeader("Connection", "keep-alive");
                response.addHeader("Transger-Encoding", "chunked");

                // Echo headers
                response.write(request.getMethod() + " " + request.getRequestUri() + " " + request.getVersion() + "\n");
                for(String key : request.getHeaders().keySet()) {
                    response.write(key + ": " + request.getHeaders().get(key) + "\r\n");
                }
                response.write("\r\n");

                // Echo body
                request.onData(new CompletionHandler<InputStream, Integer>() {
                    @Override
                    public void completed(InputStream result, Integer count) {
                        // Done
                        if(count.equals(0)) {
                            response.write("<p>Hello World</p>");
                            response.end();
                        }
                        else {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(result));
                            String line;
                            try {
                                while((line = reader.readLine()) != null) {
                                    System.err.println(line);
                                    response.write(line);
                                }
                            }
                            catch(IOException ioe) {
                                ioe.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Integer attachment) {
                        exc.printStackTrace();
                    }
                });
            }
        });
        server.listen(3000);
    }
}
