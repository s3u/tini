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

/**
 * Simple file server
 */
public class FileServer {

    public static void main(final String[] args) throws Exception {
        final HttpServer server = HttpServer.createServer();

        // Serves a file - relative to the root dir
        server.use(new Object() {
            @GET
            public void getAFile(final ServerRequest request, final ServerResponse response) throws IOException {
                final Path path = FileSystems.getDefault().getPath(request.getRequestUri().substring(1)); // Trim the slash
                final AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
                copyFile(channel, 0, response);
            }
        });

        server.listen(3000);
    }

    private static void copyFile(final AsynchronousFileChannel channel, final int start, final ServerResponse response) {
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
}