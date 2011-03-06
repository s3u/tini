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
import org.tini.core.Server;
import org.tini.core.TiniResponse;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.stream.ChunkedFile;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * A simple app that serves a file
 *
 * This is not media type aware. Not meant for serious use.
 */
public class FileServer {

    public static void main(String[] args) {
        Server server = Server.createServer();
        server.use(null, new Object() {
            @GET @POST
            public void get(final TiniRequest request, final TiniResponse response) {
                long count = 0;

                response.setContentType("application/octet-stream");
                response.setHeader("Connection", "close");

                final String path = sanitizeUri(request.getRequestUri());
                if(path == null) {
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                    return;
                }

                File file = new File(path);
                if(file.isHidden() || !file.exists()) {
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                    response.close();
                    return;
                }
                if(!file.isFile()) {
                    response.setStatus(HttpResponseStatus.FORBIDDEN);
                    response.close();
                    return;
                }

                RandomAccessFile raf = null;
                try {
                    raf = new RandomAccessFile(file, "r");

                    long fileLength = raf.length();

                    // Write the content.
                    ChunkedFile chunkedFile = new ChunkedFile(raf, 0, fileLength, 8192);
                    // This is blocking code - NIO does not support non-blocking file IO
                    while(chunkedFile.hasNextChunk()) {
                        ChannelBuffer chunk = (ChannelBuffer) chunkedFile.nextChunk();
                        count += chunk.readableBytes();
                        response.write(chunk);
                    }
                }
                catch(FileNotFoundException fnfe) {
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                    response.close();
                    return;
                }
                catch(Exception ioe) {
                    response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    response.close();
                    return;
                }
                finally {
                    try {
                        if(raf != null) {
                            raf.close();
                        }
                    }
                    catch(IOException ioe) {
                        ioe.printStackTrace();
                    }
                    response.close();
                }
            }
        });
        server.listen(3001);
    }

    // The following is from Netty distribution.
    /*
     * Copyright 2009 Red Hat, Inc.
     *
     * Red Hat licenses this file to you under the Apache License, version 2.0
     * (the "License"); you may not use this file except in compliance with the
     * License.  You may obtain a copy of the License at:
     *
     *    http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
     * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
     * License for the specific language governing permissions and limitations
     * under the License.
     */
    private static String sanitizeUri(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        }
        catch(UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            }
            catch(UnsupportedEncodingException e1) {
                throw new Error();
            }
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if(uri.contains(File.separator + ".") ||
            uri.contains("." + File.separator) ||
            uri.startsWith(".") || uri.endsWith(".")) {
            return null;
        }

        // Convert to absolute path.
        return System.getProperty("user.dir") + File.separator + uri;
    }
}
