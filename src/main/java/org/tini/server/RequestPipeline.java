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

import org.tini.common.Sink;
import org.tini.parser.RequestLine;
import org.tini.parser.RequestParser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pipeline is associated with a channel/connection. Since a client can send one or several
 * requests on a given connection, an instance of this class represents all the requests and
 * responses made on that connection.
 * <p/>
 * This class also manages connection lifecycle such as termination, and pipelining.
 *
 * @author Subbu Allamaraju
 */
public class RequestPipeline {

    private static final Logger logger = Logger.getLogger("org.tini.core");

    // Channel
    private final AsynchronousSocketChannel channel;

    // Parser to parse HTTP requests
    private final RequestParser parser;

    // Pending responses for pipelined requests.
    private final BlockingQueue<ChannelWriter> pipeline;

    // Watch for idle connections
    private final IdleConnectionWatcher idleWatcher;

    // Close if explicitly asked for
    private boolean closeWhenDone = false;

    // Handlers
    private final Map<String, Object> handlers;

    RequestPipeline(final AsynchronousSocketChannel channel,
                    final Map<SocketOption, Object> options,
                    final Map<String, Object> handlers,
                    final long idleTimeout,
                    final TimeUnit idleTimeoutUnit,
                    final long readTimeout,
                    final TimeUnit readTimeoutUnit) {

        this.channel = channel;
        this.handlers = handlers;
        pipeline = new LinkedBlockingQueue<ChannelWriter>();
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

    /**
     * Process the request pipeline. The pipeline may contain several HTTP requests.
     */
    public void process() {
        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                // For each request we need to register handlers.
                final Object[] pair = new Object[2];
                parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
                    @Override
                    public void completed(final RequestLine result, final Void attachment) {
                        final ServerRequest request = new ServerRequest(parser, result);
                        final ChannelWriter sink = new ChannelWriter();
                        final ServerResponse response = new ServerResponse(sink, request);
                        pair[0] = request;
                        pair[1] = response;
                        try {
                            pipeline.put(sink);
                        }
                        catch(InterruptedException ie) {
                            logger.log(Level.WARNING, ie.getMessage(), ie);
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        logger.log(Level.SEVERE, exc.getMessage(), exc);
                        final ChannelWriter sink = new ChannelWriter();
                        try {
                            pipeline.put(sink);
                        }
                        catch(InterruptedException ie) {
                            logger.log(Level.WARNING, ie.getMessage(), ie);
                            return;
                        }
                        final ServerResponse response = new ServerResponse(sink);
                        response.setStatus(400, "Bad Request");
                        response.end();
                    }
                });

                parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        final ServerRequest request = (ServerRequest) pair[0];
                        final ServerResponse response = (ServerResponse) pair[1];
                        if("close".equals(request.getHeader("connection"))) {
                            closeWhenDone = true;
                        }
                        request.setHeaders(result);

                        // Invoke the app
                        invokeApp(request, response, handlers);
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        final ServerResponse response = (ServerResponse) pair[1];
                        logger.log(Level.SEVERE, exc.getMessage(), exc);
                        response.setStatus(500, "Internal Server Error");
                        response.end();
                    }
                });
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
            }
        });
        parser.readNext();
    }

    void flushCompleted() throws InterruptedException {
        ChannelWriter top = pipeline.peek();
        // If the top is not buffering, it will go away once it is done. We don't need to worry
        // about it here.
        while(top != null && top.isBuffering() && top.ended) {
            logger.info("buffering top found " + top.hashCode() + " " + top.ended);
            // Flush this
            top.flush();
            try {
                top = pipeline.take();
            }
            catch(InterruptedException ie) {
                logger.log(Level.WARNING, ie.getMessage(), ie);
            }
        }
        if(pipeline.peek() == null && closeWhenDone) {
            try {
                logger.info("Closing the connection");
                channel.close();
            }
            catch(IOException ioe) {
                logger.log(Level.WARNING, ioe.getMessage(), ioe);
            }
        }
    }

    private void invokeApp(final ServerRequest httpRequest, final ServerResponse httpResponse,
                           final Map<String, Object> handlers) {
        final String methodName = httpRequest.getMethod();
        final Class methodAnnotation;
        try {
            methodAnnotation = Class.forName("javax.ws.rs." + methodName.toUpperCase());
        }
        catch(ClassNotFoundException cnfe) {
            logger.log(Level.SEVERE, "Unable to find annotation for method [" + methodName + "]", cnfe);
            httpResponse.setStatus(500, "Internal Server Error");
            httpResponse.end();
            return;
        }

        // TODO: Rails style matching

        // Dispatch
        Object handler = handlers.get(httpRequest.getRequestUri());
        handler = handler == null ? handlers.get(null) : handler;

        if(null == handler) {
            logger.warning("Handler for " + httpRequest.getRequestUri() + " not found");
            httpResponse.setStatus(404, "Not Found");
            return;
        }
        final Method[] methods = handler.getClass().getMethods();
        Method hMethod = null;
        Method fallback = null;
        for(final Method method : methods) {
            if(method.getAnnotation(methodAnnotation) != null) {
                hMethod = method;
                break;
            }
            else if(method.getName().equals("service")) {
                fallback = method;
            }
        }
        if(hMethod == null && fallback != null) {
            hMethod = fallback;
        }
        if(hMethod != null) {
            hMethod.setAccessible(true);
            try {
                hMethod.invoke(handler, httpRequest, httpResponse);
            }
            catch(IllegalAccessException iae) {
                logger.log(Level.SEVERE, iae.getMessage(), iae);
                httpResponse.setStatus(500, "Internal Server Error");
                httpResponse.end();
            }
            catch(InvocationTargetException ite) {
                logger.log(Level.SEVERE, ite.getMessage(), ite);
                httpResponse.setStatus(500, "Internal Server Error");
                httpResponse.end();
            }
            catch(Throwable t) {
                // Catch-all
                logger.log(Level.WARNING, t.getMessage(), t);
                httpResponse.setStatus(500, "Internal Server Error");
                httpResponse.end();
            }
        }
        else {
            logger.warning("No handler for method " + httpRequest.getMethod() + " found");
            httpResponse.setStatus(405, "Method Not Allowed");
            httpResponse.end();
        }
    }


    class ChannelWriter implements Sink {
        ChannelWriter() {
        }

        private boolean ended = false;

        boolean isBuffering() {
            return buffers.size() > 0;
        }

        List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();

        @Override
        public void write(final ByteBuffer byteBuffer, final CompletionHandler<Integer, Void> handler) {
            byteBuffer.rewind();
            if(this == pipeline.peek()) {
                idleWatcher.writing();
                if(handler == null) {
                    final Future f = channel.write(byteBuffer);
                    try {
                        f.get();
                    }
                    catch(ExecutionException ee) {
                        ee.printStackTrace();
                    }
                    catch(InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
                else {
                    channel.write(byteBuffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(final Integer result, final Void attachment) {
                            idleWatcher.doneWriting();
                            try {
                                handler.completed(result, attachment);
                            }
                            catch(Throwable t) {
                                logger.log(Level.WARNING, t.getMessage(), t);

                            }
                        }

                        @Override
                        public void failed(final Throwable exc, final Void attachment) {
                            logger.log(Level.WARNING, exc.getMessage(), exc);
                            try {
                                handler.failed(exc, attachment);
                            }
                            catch(Throwable t) {
                                logger.log(Level.WARNING, t.getMessage(), t);
                            }
                        }
                    });
                }
            }
            else {
                // Need to buffer response
                buffers.add(byteBuffer);
                if(handler != null) {
                    handler.completed(0, null); // Nothing is written yet
                }
            }
        }

        @Override
        public void closeWhenDone() {
            closeWhenDone = true;
        }

        @Override
        public void end() {
            this.ended = true;
            flush();
        }

        void flush() {
            if(this == pipeline.peek()) {
                for(final ByteBuffer byteBuffer : buffers) {
                    byteBuffer.rewind();
                    channel.write(byteBuffer);
                }

                try {
                    // Remove from top
                    pipeline.take();
                    flushCompleted();
                }
                catch(InterruptedException ie) {
                    logger.log(Level.WARNING, ie.getMessage(), ie);
                }
            }
        }

    }
}
