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

import org.tini.common.Sink;
import org.tini.parser.ResponseLine;
import org.tini.parser.ResponseParser;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;

/**
 * @author Subbu Allamaraju
 */
public class ClientResponse {

    // Response parser
    private final ResponseParser parser;

    /**
     * Creates an HTTP request. Use {@link ClientConnection} to create a new request.
     *
     * @param parser response
     */
    ClientResponse(final ResponseParser parser) {
//        super(sink);
//        this.host = host;
//        this.port = port;
//        this.path = path == null || path.equals("") ? "/" : path;
//        this.method = method;
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


}
