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

package org.tini.client;

import org.tini.common.WritableMessage;
import org.tini.common.Sink;
import org.tini.parser.HttpCodecUtil;
import org.tini.parser.ResponseLine;
import org.tini.parser.ResponseParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP client request.
 *
 * @author Subbu Allamaraju
 */
public class ClientRequest extends WritableMessage {

    private final String host;
    private final int port;
    private final String method;
    private final String path;

    private final AsynchronousSocketChannel channel;

    private CompletionHandler<ClientResponse, Void> onResponse;

    /**
     * Creates an HTTP request. Use {@link ClientConnection} to create a new request.
     *
     * @param host    host
     * @param port    port
     * @param path    request uri or path
     * @param method  method
     * @param channel channel
     * @param sink    sink to write response
     */
    ClientRequest(final String host, final int port,
                  final String path,
                  final String method,
                  final AsynchronousSocketChannel channel,
                  final Sink sink) throws InterruptedException {
        super(sink);
        this.host = host;
        this.port = port;
        this.path = path == null || path.equals("") ? "/" : path;
        this.method = method;
        this.channel = channel;
    }

    /**
     * Handler to be invoked on response.
     *
     * @param onResponse handler
     */
    // TODO: Why this handler?
    public void onResponse(final CompletionHandler<ClientResponse, Void> onResponse) {
        this.onResponse = onResponse;
    }

    /**
     * Writes the request line and headers and begins parsing the response.
     */
    // TODO: Rename - this is confusing
    public void writeHead() {
        if(!headers.containsKey("host")) {
            if(port == 80) {
                headers.put("host", host);
            }
            else {
                headers.put("host", host + ":" + port);
            }
        }
        super.writeHead(new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(final Integer result, final Void attachment) {
                final ResponseParser parser = new ResponseParser(channel, 1, TimeUnit.MINUTES);
                parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
                    @Override
                    public void completed(final ResponseLine result, final Void attachment) {
                        final ClientResponse clientResponse = new ClientResponse(parser, result);
                        onResponse.completed(clientResponse, null);
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        onResponse.failed(exc, attachment);
                    }
                });

                parser.readNext();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                // TODO:
                exc.printStackTrace();
            }
        });
    }

    @Override
    protected void writeFirstLine(final ByteArrayOutputStream baos) throws IOException {
        baos.write(method.getBytes(US_ASCII));
        baos.write(HttpCodecUtil.SP);
        baos.write(path.getBytes(US_ASCII));
        baos.write(HttpCodecUtil.SP);
        baos.write("HTTP/1.1".getBytes(US_ASCII));
    }

    @Override
    protected boolean doClose() {
        final String val = headers.get("connection");
        return "close".equalsIgnoreCase(val);
    }
}
