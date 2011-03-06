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
import org.tini.core.AsyncTask;
import org.tini.core.Server;

import javax.ws.rs.GET;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Take two numbers of the web, add them and write to output
 */
public class Adder {
    public static void main(String[] args) {

        final Server server = Server.createServer();

        // This is the default handler for the server
        server.use(null, new Object() {

            /**
             * This method fetches two random numbers, adds them up, and writes the result to the
             * client.
             *
             * @param request request
             * @param response response
             */
            @GET
            public void get(final TiniRequest request, final TiniResponse response) {
                // Create tasks
                final AsyncTask t1 = new GetRandomNumberTask("t1", "http://www.random.org/cgi-bin/randbyte?nbytes=1&format=d");
                final AsyncTask t2 = new GetRandomNumberTask("t2", "http://www.random.org/cgi-bin/randbyte?nbytes=1&format=d");
                final AsyncTask t3 = new AsyncTask("t3") {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    private int sum = 0;

                    @Override
                    public void take(String name, Object result, Throwable error) {
                        counter.addAndGet(1);
                        if(error != null) {
                            // Received error
                            error.printStackTrace();
                        }
                        else {
                            sum += ((Integer) result).intValue();
                        }
                        if(counter.get() == 2) {
                            response.setContentType("text/plan");
                            response.write(Integer.toString(sum));
                            response.close();
                        }
                    }
                };

                t3.dependsOn(t1, t2);

                // Start the flow
                t1.start();
                t2.start();
            }
        });
        server.listen(3000);
    }
}

