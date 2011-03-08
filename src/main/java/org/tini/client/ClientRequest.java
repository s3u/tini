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

import org.tini.common.MessageSerializer;
import org.tini.common.Sink;
import org.tini.parser.HttpCodecUtil;
import org.tini.parser.ResponseLine;
import org.tini.parser.ResponseParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;

/**
 * An HTTP client request.
 *
 * @author Subbu Allamaraju
 */
// TODO: add onStatus methods for different response codes
public class ClientRequest extends MessageSerializer {

    private final String host;
    private final int port;
    private final String method;
    private final String path;

    // Response parser
    private final ResponseParser parser;

    /**
     * Creates an HTTP request. Use {@link ClientConnection} to create a new request.
     *
     * @param host   host
     * @param port   port
     * @param path   request uri or path
     * @param method method
     * @param parser response
     * @param sink   sink to write response
     */
    ClientRequest(final String host, final int port,
                  final String path, final String method,
                  final ResponseParser parser, final Sink sink) {
        super(sink);
        this.host = host;
        this.port = port;
        this.path = path == null || path.equals("") ? "/" : path;
        this.method = method;
        this.parser = parser;
    }

    /**
     * Registers a handler when the client receives the response line.
     *
     * @param handler handler
     */
    public void onResponseLine(final CompletionHandler<ResponseLine, Void> handler) {
        assert handler != null;
        parser.onResponseLine(handler);
    }

    /**
     * Registers a handler when the client receives headers.
     *
     * @param handler handler
     */
    public void onHeaders(final CompletionHandler<Map<String, List<String>>, Void> handler) {
        assert handler != null;
        parser.onHeaders(handler);
    }

    /**
     * Registers a handler when the client receives data. This method may be called several times.
     *
     * @param handler handler
     */
    public void onData(final CompletionHandler<ByteBuffer, Void> handler) {
        assert handler != null;
        parser.onData(handler);
    }

    /**
     * Registers a handler when the client receives trailers.
     *
     * @param handler handler
     */
    public void onTrailers(final CompletionHandler<Map<String, List<String>>, Void> handler) {
        assert handler != null;
        parser.onTrailers(handler);
    }

    /**
     * Writes the request line and headers.
     */
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
                parser.readNext();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
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
