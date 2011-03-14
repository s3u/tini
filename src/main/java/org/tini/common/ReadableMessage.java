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

package org.tini.common;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A readable message consisting of headers, followed by body, followed by trailers.
 *
 * @author Subbu Allamaraju
 */
public class ReadableMessage {

    private final List<CompletionHandler<Map<String, List<String>>, Void>> onHeaders =
        new ArrayList<CompletionHandler<Map<String, List<String>>, Void>>(1);
    private CompletionHandler<ByteBuffer, Void> onData;
    private final List<CompletionHandler<Map<String, List<String>>, Void>> onTrailers =
        new ArrayList<CompletionHandler<Map<String, List<String>>, Void>>(1);

    protected ReadableMessage() {
    }

    /**
     * Registers a handler when the client receives headers.
     *
     * @param handler handler
     */
    public void onHeaders(final CompletionHandler<Map<String, List<String>>, Void> handler) {
        assert handler != null;
        onHeaders.add(handler);
    }

    /**
     * Registers a handler when the client receives data. This method may be called several times.
     *
     * @param handler handler
     */
    public void onData(final CompletionHandler<ByteBuffer, Void> handler) {
        assert handler != null;
        onData = handler;
    }

    /**
     * Registers a handler when the client receives trailers.
     *
     * @param handler handler
     */
    public void onTrailers(final CompletionHandler<Map<String, List<String>>, Void> handler) {
        assert handler != null;
        onTrailers.add(handler);
    }

    // TODO: protect all these below
    public void headers(final Map<String, List<String>> headers) {
       for(final CompletionHandler<Map<String, List<String>>, Void> handler : onHeaders) {
           handler.completed(headers, null);
       }
    }

    public void data(final ByteBuffer data) {
        if(onData != null) {
            onData.completed(data, null);
        }
    }

    public void trailers(final Map<String, List<String>> trailers) {
        for(final CompletionHandler<Map<String, List<String>>, Void> handler : onTrailers) {
            handler.completed(trailers, null);
        }
    }

    public void failure(final Throwable failure) {
        // TODO
    }
}
