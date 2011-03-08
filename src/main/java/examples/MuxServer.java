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

import javax.ws.rs.GET;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

/**
 * Test multiplexing - TODO - does not multiplex yet
 */
public class MuxServer {

    public static void main(final String[] args) throws Exception {
//        final HttpServer server = HttpServer.createServer();
//
//        // Waits for upto 5 secs before responding
//        server.use("/r1", new Object() {
//            @GET
//            public void delayResponse(final ServerRequest request, final ServerResponse response) {
//                response.setContentType("text/plain; charset=UTF-8");
//                final Thread t = new Thread(new Wait(request, response));
//                t.start();
//            }
//        });
//
//        // Serves a large file
//        server.use("/r2", new Object() {
//            @GET
//            public void getAFile(final ServerRequest request, final ServerResponse response) throws IOException {
//                final Path path = FileSystems.getDefault().getPath("data/large.log");
//                final AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
//                copyFile(channel, 0, response);
//            }
//        });
//
//        // Fetches a large resource from somewhere else - like a proxy
//        server.use("/r3", new Object() {
//            @GET
//            public void getSomeResource(final ServerRequest request, final ServerResponse response) throws IOException {
//                // Get some data from somewhere else
//                final ClientConnection connection = new ClientConnection();
//                connection.connect("www.example.org", 80, new CompletionHandler<Void, Void>() {
//                    @Override
//                    public void completed(final Void result, final Void attachment) {
//                        response.setStatus(200, "OK");
//                        response.setContentType("text/plain; charset=UTF-8");
//                        final ClientRequest clientRequest = connection.request("/somethinglarge", "GET");
//                        clientRequest.onData(new CompletionHandler<ByteBuffer, Void>() {
//                            @Override
//                            public void completed(final ByteBuffer result, final Void attachment) {
//                                final int size = result.remaining();
//                                response.write(result);
//                                if(size == 0) {
//                                    response.end();
//                                }
//                            }
//
//                            @Override
//                            public void failed(final Throwable exc, final Void attachment) {
//                                // Some error handling
//                            }
//                        });
//                        clientRequest.writeHead();
//                    }
//
//                    @Override
//                    public void failed(final Throwable exc, final Void attachment) {
//                        response.setStatus(500, "Internal Server Error");
//                        response.end();
//                    }
//                });
//            }
//        });
//
//        server.listen(3000);
    }

    static void copyFile(final AsynchronousFileChannel channel, final int start, final ServerResponse response) {
        final ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.read(buffer, start, null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(final Integer result, final Object attachment) {
                if(result > 0) {
                    buffer.rewind();
                    response.write(buffer);
                    copyFile(channel, start + result, response);
                }
                else {
                    response.end();
                    try {
                        channel.close();
                    }
                    catch(IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }

            @Override
            public void failed(final Throwable exc, final Object attachment) {
                response.setStatus(404, "File Not Found");
                response.end();
            }
        });
    }

    static class Wait implements Runnable {
        final ServerRequest request;
        final ServerResponse response;

        Wait(final ServerRequest request, final ServerResponse response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public void run() {
            final int id = Integer.parseInt(request.getRequestUri().substring(1));
            try {
                // Sleep for some 0-5 sec
                Thread.sleep(new Random().nextInt(5000));
            }
            catch(InterruptedException ie) {
                ie.printStackTrace();
            }

            response.write(request.getRequestUri());
            response.end();
        }
    }
}