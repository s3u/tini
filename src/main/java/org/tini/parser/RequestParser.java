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

package org.tini.parser;

import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * <p>Parses HTTP requests on the server side.</p>
 *
 * @author Subbu Allamaraju
 */
public class RequestParser extends HttpParser {

    private final List<CompletionHandler<RequestLine, Void>> onRequestLine = new ArrayList<CompletionHandler<RequestLine, Void>>(1);

    /**
     * Creates a request parser
     *
     * @param channel channel to request messages from
     * @param timeout read timeout
     * @param timeUnit read timeout unit
     */
    public RequestParser(final AsynchronousSocketChannel channel, final long timeout, final TimeUnit timeUnit) {
        super(channel, timeout, timeUnit);
    }

    /**
     * <p>Registers a handler to receive the request line.</p>
     *
     * @param handler handler
     */
    public void onRequestLine(final CompletionHandler<RequestLine, Void> handler) {
        onRequestLine.add(handler);
    }

    /**
     * Initiates parsing by looking for the first line.
     */
    public synchronized void go() {
        final StringBuilder line = new StringBuilder();

        onLine(line, maxInitialLineLength, new CompletionHandler<StringBuilder, Void>() {
            @Override
            public void completed(final StringBuilder result, final Void attachment) {
                final String[] initialLine = splitInitialLine(line.toString());
                if(initialLine.length == 3) {
                    if(initialLine[0].length() == 0 || initialLine[1].length() == 0 || initialLine[2].length() == 0) {
                        this.failed(new IOException("Malformed request line"), null);
                    }
                    else {
                        final RequestLine requestLine = new RequestLine(initialLine[0], initialLine[1], initialLine[2]);
                        try {
                            for(final CompletionHandler<RequestLine, Void> handler : onRequestLine) {
                                try {
                                    handler.completed(requestLine, null);
                                }
                                catch(Throwable t) {
                                    logger.log(Level.WARNING, t.getMessage(), t);
                                }
                            }
                        }
                        finally {
                            findHeaders(false);
                        }
                    }
                }
                else {
                    this.failed(new IOException("Malformed request line"), null);
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    for(final CompletionHandler<RequestLine, Void> handler : onRequestLine) {
                        try {
                            handler.failed(exc, attachment);
                        }
                        catch(Throwable t) {
                            logger.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                }
                finally {
                    shutdown();
                }
            }
        });
    }
}
