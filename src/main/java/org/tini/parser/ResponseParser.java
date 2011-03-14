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
import java.util.logging.Logger;

/**
 * <p>Reads responses from a channel, and hands each response to a readable pipeline.</p>
 *
 * @author Subbu Allamaraju
 */
public class ResponseParser extends HttpParser {
    private static final Logger logger = Logger.getLogger("org.tini.parser");

    // Response messages parsed from the channel will be pumped into messages found in this pipeline.
    // In this implementation, each message will be removed from the pipeline.
    private final List<CompletionHandler<ResponseLine, Void>> onResponseLine = new ArrayList<CompletionHandler<ResponseLine, Void>>(1);

    public ResponseParser(final AsynchronousSocketChannel channel,
                          final long timeout,
                          final TimeUnit timeUnit) {
        super(channel, timeout, timeUnit);
    }

    /**
     * Register a handler to process the response line
     *
     * @param handler handler
     */
    public void onResponseLine(final CompletionHandler<ResponseLine, Void> handler) {
        onResponseLine.add(handler);
    }

    /**
     * Read the next response message
     */
    public synchronized void go() {
        findResponseLine();
    }

    private void findResponseLine() {
        final StringBuilder line = new StringBuilder();

        onLine(new CompletionHandler<StringBuilder, Void>() {
            @Override
            public void completed(final StringBuilder result, final Void attachment) {
                final String[] initialLine = splitInitialLine(line.toString());
                if(initialLine.length == 3) {
                    if(initialLine[0].length() == 0 || initialLine[1].length() == 0 || initialLine[2].length() == 0) {
                        this.failed(new IOException("Malformed response line - " + line.toString()), null);
                    }
                    else {
                        try {
                            final int status = Integer.parseInt(initialLine[1]);
                            final ResponseLine responseLine = new ResponseLine(initialLine[0], status, initialLine[2]);

                            try {
                                for(final CompletionHandler<ResponseLine, Void> handler : onResponseLine) {
                                    handler.completed(responseLine, null);
                                }
                            }
                            finally {
                                findHeaders(false);
                            }
                        }
                        catch(NumberFormatException nfe) {
                            this.failed(new IOException("Bad response line", nfe), null);
                        }
                    }
                }
                else {
                    this.failed(new IOException("Bad response line"), null);
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    logger.log(Level.SEVERE, exc.getLocalizedMessage(), exc);

                    try {
                        for(final CompletionHandler<ResponseLine, Void> handler : onResponseLine) {
                            handler.failed(exc, null);
                        }
                    }
                    catch(Throwable t) {
                        logger.log(Level.WARNING, t.getLocalizedMessage(), t);
                    }
                }
                finally {
                    shutdown();
                }
            }
        }, line, maxInitialLineLength);
    }
}
