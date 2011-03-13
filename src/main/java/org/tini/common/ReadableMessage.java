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

import org.tini.parser.HttpParser;

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

    protected ReadableMessage(final HttpParser parser) {
        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                for(final CompletionHandler<Map<String, List<String>>, Void> handler : onHeaders) {
                    handler.completed(result, attachment);
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                for(final CompletionHandler<Map<String, List<String>>, Void> handler : onHeaders) {
                    handler.failed(exc, attachment);
                }
            }
        });
        parser.onData(new CompletionHandler<ByteBuffer, Void>() {
            @Override
            public void completed(final ByteBuffer result, final Void attachment) {
                if(onData != null) {
                    onData.completed(result, attachment);
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                if(onData != null) {
                    onData.failed(exc, attachment);
                }
            }
        });
        parser.onTrailers(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                for(final CompletionHandler<Map<String, List<String>>, Void> handler : onTrailers) {
                    handler.completed(result, attachment);
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                for(final CompletionHandler<Map<String, List<String>>, Void> handler : onTrailers) {
                    handler.failed(exc, attachment);
                }
            }
        });
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
}
