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

package org.tini.server;

import org.tini.common.ReadableMessage;
import org.tini.parser.HttpParser;
import org.tini.parser.RequestLine;

import java.nio.channels.CompletionHandler;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>HTTP 1.1 request. This is not like typical request and response objects in the Java Servlet
 * API or other Java frameworks. See the flow below to understand how to access request data.</p>
 *
 * <p>When the server receives a request, it parses the request line, and finds a handler that can
 * process the request. Handlers implement the {@link CompletionHandler} interface.</p>
 *
 * <p>If it finds a handler, it will invoke the handler with an HttpRequest and HttpResponse object.
 * Like in any event-based API, registering a handler does not immediately cause it it be
 * invoked.</p>
 *
 * <p>If the handler needs access to request headers, body, or trailers, it must register {@code
 * CompletionHandler}s by calling #onHeaders, #onData and #onTrailers.</p>
 *
 * @author Subbu Allamaraju
 */
public class ServerRequest extends ReadableMessage {

    private final RequestLine requestLine;
    private Map<String, List<String>> headers = Collections.EMPTY_MAP;

    protected ServerRequest(final HttpParser parser, final RequestLine requestLine) {
        super();
        this.requestLine = requestLine;
    }

    void setHeaders(final Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public RequestLine getRequestLine() {
        return requestLine;
    }

    /**
     * <p>Returns the value of the first header with the given name, or <code>null</code> if not
     * found.</p>
     *
     * @param name name
     * @return value
     */
    public String getHeader(final String name) {
        final List<String> val = headers.get(name);
        if(val != null && val.size() > 0) {
            return val.get(0);
        }
        else {
            return null;
        }
    }

    /**
     * <p>Returns request headers.</p>
     *
     * @return headers
     */
    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
}
