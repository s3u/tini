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

import org.tini.common.IdleConnectionWatcher;
import org.tini.common.ReadableMessage;
import org.tini.common.ReadablePipeline;
import org.tini.common.WritablePipeline;
import org.tini.parser.RequestLine;
import org.tini.parser.RequestParser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>A writablesQueue is associated with a channel/connection. Since a client can send one or several
 * requests on a given connection, an instance of this class represents all the requests and
 * responses made on that connection.<p/>
 * <p/>
 * <p>This class also manages connection lifecycle such as termination, and pipelining.</p>
 *
 * @author Subbu Allamaraju
 */
public class ServerRequestPipeline extends ReadablePipeline {

    // Watch for idle connections
    // TODO
    private final IdleConnectionWatcher idleWatcher;

    private static final Logger logger = Logger.getLogger("org.tini.server");
    private final RequestParser parser;

    // Handlers
    private final Map<String, Object> handlers;

    ServerRequestPipeline(final AsynchronousSocketChannel channel,
                          final Map<SocketOption, Object> options,
                          final Map<String, Object> handlers,
                          final long idleTimeout,
                          final TimeUnit idleTimeoutUnit,
                          final long readTimeout,
                          final TimeUnit readTimeoutUnit) {

        super(channel);
        this.handlers = handlers;

        parser = new RequestParser(channel, readTimeout, readTimeoutUnit);

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
    }

    /**
     * <p>Start processing the request writablesQueue and fill up the response writablesQueue.</p>
     *
     * @param writablePipeline response writablesQueue
     */
    public void process(final WritablePipeline writablePipeline) {
        // Find a new request line
        parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
            @Override
            public void completed(final RequestLine requestLine, final Void attachment) {
                final ServerRequest request = new ServerRequest(requestLine);
                final ServerResponse response = new ServerResponse(writablePipeline);
                try {
                    push(request);
                    writablePipeline.push(response);
                }
                catch(InterruptedException ie) {
                    // TODO
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                logger.log(Level.SEVERE, exc.getMessage(), exc);
                final ServerResponse response = new ServerResponse(writablePipeline);
                response.setStatus(400, "Bad Request");
                response.end();
            }
        });

        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                try {
                    final ServerRequest request = (ServerRequest) peek();
                    request.setHeaders(result);
                    final ServerResponse response = (ServerResponse) writablePipeline.peek();
                    if("close".equals(request.getHeader("connection"))) {
                        writablePipeline.closeWhenDone();
                    }

                    // Invoke the app
                    invokeApp(request, response, handlers);
                }
                catch(InterruptedException ie) {
                    // TODO
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    final ServerResponse response = (ServerResponse) writablePipeline.poll();
                    logger.log(Level.SEVERE, exc.getMessage(), exc);
                    response.setStatus(500, "Internal Server Error");
                    response.end();
                }
                catch(InterruptedException ie) {
                    // TODO
                }

            }
        });

        parser.onData(new CompletionHandler<ByteBuffer, Void>() {
            @Override
            public void completed(final ByteBuffer result, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = peek();
                    if(readableMessage != null) {
                        readableMessage.data(result);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = poll();
                    if(readableMessage != null) {
                        readableMessage.failure(exc);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        });

        parser.onTrailers(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = poll();
                    if(readableMessage != null) {
                        readableMessage.trailers(result);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = poll();
                    if(readableMessage != null) {
                        readableMessage.failure(exc);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        });

        parser.go();
    }

    private void invokeApp(final ServerRequest httpRequest, final ServerResponse httpResponse,
                           final Map<String, Object> handlers) {
        final String methodName = httpRequest.getRequestLine().getMethod();
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
        Object handler = handlers.get(httpRequest.getRequestLine().getUri());
        handler = handler == null ? handlers.get(null) : handler;

        if(null == handler) {
            logger.warning("Handler for " + httpRequest.getRequestLine().getUri() + " not found");
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
            logger.warning("No handler for method " + httpRequest.getRequestLine().getMethod() + " found");
            httpResponse.setStatus(405, "Method Not Allowed");
            httpResponse.end();
        }
    }
}
