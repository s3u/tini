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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sets up a server to listen to client requests and pass them along to application handlers.
 *
 * @author Subbu Allamaraju
 */
public class HttpServer {

    private static final Logger logger = Logger.getLogger("org.tini.server");

    // Application handlers - registered to process incoming requests
    private final Map<String, Object> handlers;

    // Reads will be timed out after this default interval
    private long readTimeout = 5;
    private TimeUnit readTimeoutUnit = TimeUnit.SECONDS;

    // Idle connections from clients will be closed after this default interval
    private long idleTimeout = 60;
    private TimeUnit idleTimeoutUnit = TimeUnit.SECONDS;

    // Additional options for the channel - see {@SocketOption} for available options
    private final Map<SocketOption, Object> options = new HashMap<SocketOption, Object>();

    // Channel group for open channels
    private AsynchronousChannelGroup channelGroup;

    /**
     * Create and returns a server.
     *
     * @return server
     */
    public static HttpServer createServer() {
        return new HttpServer();
    }

    /**
     * Creates a server instance.
     */
    private HttpServer() {
        handlers = new HashMap<String, Object>();
    }

    /**
     * Sets read timeout. The default value is 5 seconds.
     *
     * @param readTimeout read timeout
     * @param timeUnit    unit unit
     */
    public void setReadTimeout(final long readTimeout, final TimeUnit timeUnit) {
        this.readTimeout = readTimeout;
        this.readTimeoutUnit = timeUnit;
    }

    /**
     * Sets channel idle timeout. The default value is 60 seconds.
     *
     * @param idleTimeout idle timeout
     * @param timeUnit    time unit
     */
    public void setIdleTimeout(final long idleTimeout, final TimeUnit timeUnit) {
        this.idleTimeout = idleTimeout;
        this.idleTimeoutUnit = timeUnit;
    }

    /**
     * See {@code SocketOption} for possible options. These options will be passed onto the socket.
     *
     * @param option option
     * @param value  value
     */
    public void setOption(final SocketOption option, final Object value) {
        options.put(option, value);
    }

    /**
     * Specify a handler for handling requests for a given path. The handler must use JAX-RS
     * annotations to receive HTTP requests.
     *
     * @param path    path
     * @param handler handler
     */
    public void use(final String path, final Object handler) {
        handlers.put(path, handler);
    }

    /**
     * Specify a default handler for handling all requests. The handler must use JAX-RS
     * annotations to receive HTTP requests.
     *
     * @param handler handler
     */
    public void use(final Object handler) {
        handlers.put(null, handler);
    }

    /**
     * Listen to requests at the given port and waits indefinitely.
     *
     * @param port listen port
     * @throws IOException thrown when unable to listen
     */
    public void listen(final int port) throws IOException {

        listen(port, new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                // Hackery to keep the server going
                //noinspection InfiniteLoopStatement
                while(true) {
                    //noinspection ResultOfMethodCallIgnored
                    try {
                        System.in.read();
                    }
                    catch(IOException ioe) {
                        logger.log(Level.WARNING, ioe.getMessage(), ioe);
                    }
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                logger.log(Level.WARNING, exc.getMessage(), exc);
            }
        });
    }

    /**
     * Listen to requests at the given port and invokes the handler on success.
     *
     * @param port listen port
     * @param handler handler
     */
    public void listen(final int port, final CompletionHandler<Void, Void> handler) {
        try {
            final ExecutorService executorService = Executors.newCachedThreadPool();
            channelGroup = AsynchronousChannelGroup.withCachedThreadPool(executorService, 1);
            final AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open(channelGroup);

            // Listen on an address
            server.bind(new InetSocketAddress(port));
            logger.info("Listening on port " + port);

            server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                public void completed(final AsynchronousSocketChannel channel,
                                      final Void attachment) {
                    logger.info("Client connected");
                    server.accept(null, this);

                    // Create pipelines and parser
                    final ServerRequestPipeline requestPipeline = new ServerRequestPipeline(channel, options, handlers,
                        readTimeout, readTimeoutUnit);
                    final ServerResponsePipeline responsePipeline = new ServerResponsePipeline(channel,
                        idleTimeout, idleTimeoutUnit);

                    // Process requests from the writablesQueue
                    requestPipeline.process(responsePipeline);
                }

                public void failed(final Throwable e, final Void attachment) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        logger.info("Shutting down server on port " + port);
                        server.close();
                    }
                    catch(IOException e) {
                        logger.log(Level.WARNING, e.getMessage(), "Error shutting down");
                    }
                }
            });

            handler.completed(null, null);
        }
        catch(IOException ioe) {
            handler.failed(ioe, null);
        }
    }

    /**
     * Shutdown the server.
     *
     * @throws IOException thrown in case of I/O errors
     */
    public void shutdown() throws IOException {
        if(channelGroup != null) {
            channelGroup.shutdownNow();
        }
    }
}
