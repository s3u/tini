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

import org.tini.parser.RequestLine;
import org.tini.parser.RequestParser;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Subbu Allamaraju
 */
public class ReadablePipeline {

    private static final Logger logger = Logger.getLogger("org.tini.core");

    // Channel
    protected final AsynchronousSocketChannel channel;

    // Watch for idle connections
    // TODO
    private final IdleConnectionWatcher idleWatcher;

    // Handlers
    protected final Map<String, Object> handlers;

    private final RequestParser parser;

    public ReadablePipeline(final AsynchronousSocketChannel channel,
                            final Map<SocketOption, Object> options,
                            final Map<String, Object> handlers,
                            final long idleTimeout,
                            final TimeUnit idleTimeoutUnit,
                            final long readTimeout,
                            final TimeUnit readTimeoutUnit) {

        this.channel = channel;
        this.handlers = handlers;
        idleWatcher = new IdleConnectionWatcher(channel, idleTimeoutUnit.toMillis(idleTimeout));

        try {
            for(final SocketOption option : options.keySet()) {
                channel.setOption(option, options.get(option));
            }
        }
        catch(IOException ioe) {
            logger.log(Level.SEVERE, ioe.getMessage(), ioe);
            try {
                channel.close();
            }
            catch(IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }

        parser = new RequestParser(channel, readTimeout, readTimeoutUnit);
    }

    // TODO?
    public void onNewRequest(final CompletionHandler<RequestLine, String> handler) {
        parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
            @Override
            public void completed(final RequestLine result, final Void attachment) {

            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });
    }

    // TODO?
    public void handle(String id, ReadableMessage request, WritableMessage response) {

    }

}
