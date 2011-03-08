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
import org.tini.parser.ResponseParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This opens a connection that can be used to send several HTTP requests.
 *
 * @author Subbu Allamaraju
 */
public class ClientConnection {
    private static final Logger logger = Logger.getLogger("org.tini.client");

    private AsynchronousSocketChannel channel = null;
    private AsynchronousChannelGroup channelGroup = null;
    private ExecutorService executorService = null;

    private String host;
    private int port;

    /**
     * Creates a new connection.
     */
    public ClientConnection() {
    }

    /**
     * Opens a connection to the specified host at the specified port, and invokes the completion
     * handler upon success or failure.
     *
     * @param host    host
     * @param port    port
     * @param handler handler
     * @throws IOException
     */
    // TODO: Look from open connections for reuse
    public void connect(final String host, final int port, final CompletionHandler<Void, Void> handler) throws IOException {
        assert host != null;
        assert handler != null;

        this.host = host;
        this.port = port == -1 ? 80 : port;

        final InetSocketAddress socketAddress = new InetSocketAddress(this.host, this.port);

        executorService = Executors.newCachedThreadPool();
        channelGroup = AsynchronousChannelGroup.withCachedThreadPool(executorService, 1);
        channel = AsynchronousSocketChannel.open(channelGroup);
        channel.connect(socketAddress, null, handler);
    }

    /**
     * <p>Creates an HTTP request for the given path (or request URI) and the HTTP method.</p> <p/>
     * <p>Until the {@link ClientRequest#write(byte[])} or {@link
     * org.tini.client.ClientRequest#writeHead()} is called, no data will be written to the
     * connection.</p>
     *
     * @param path   path or request URI
     * @param method HTTP method
     * @return request object
     */
    public ClientRequest request(final String path, final String method) {
        assert method != null;

        final String p = path == null || path.equals("") ? "/" : path;
        return new ClientRequest(host, port, p, method, new ResponseParser(channel, 1, TimeUnit.MINUTES),
            new DirectSink(channel));
    }

    /**
     * <p>Creates an HTTP request for the given path (or request URI) and the HTTP method, and headers. <p/>
     *
     * <p>Until the {@link ClientRequest#write(byte[])} or {@link
     * org.tini.client.ClientRequest#writeHead()} is called, no data will be written to the
     * connection.</p>
     *
     * @param path    path or request URI
     * @param method  HTTP method
     * @param headers
     * @return request object
     */
    public ClientRequest request(final String path, final String method, final Map<String, List<String>> headers) {
        final ClientRequest request = request(path, method);

        // Copy headers to the origin
        for(final String name : headers.keySet()) {
            final List<String> values = headers.get(name);
            for(final String value : values) {
                request.addHeader(name, value);
            }
        }
        return request;
    }

    /**
     * Disconnects the connection.
     */
    public void disconnect() {
        if(channel == null || channelGroup == null) {
            throw new IllegalStateException("Can't close. Not yet connected.");
        }
        try {
            final List<Runnable> pending = executorService.shutdownNow();
            for(final Runnable r : pending) {
                logger.info(r.getClass().getName());
            }

            logger.info("Closing the connection");
            channelGroup.shutdownNow();
        }
        catch(IOException ioe) {
            logger.log(Level.WARNING, ioe.getMessage(), ioe);
        }

    }

    // A sink to write to the channel.
    private class DirectSink implements Sink {
        private final AsynchronousSocketChannel channel;

        DirectSink(final AsynchronousSocketChannel channel) {
            this.channel = channel;
        }

        @Override
        // TODO Write to a queue
        public void write(final ByteBuffer byteBuffer, final CompletionHandler<Integer, Void> handler) {
            if(handler != null) {
                channel.write(byteBuffer, null, handler);
            }
            else {
                try {
                    channel.write(byteBuffer);
                }
                catch(Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        public void end() {
        }

        @Override
        public void closeWhenDone() {
        }
    }
}
