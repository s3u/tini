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

import org.tini.common.ReadablePipeline;
import org.tini.common.WritablePipeline;
import org.tini.parser.RequestLine;
import org.tini.parser.RequestParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketOption;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;
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
public class RequestPipeline extends ReadablePipeline {

    private static final Logger logger = Logger.getLogger("org.tini.core");

    // Parser to parse HTTP requests
    private final RequestParser parser;

    RequestPipeline(final AsynchronousSocketChannel channel,
                    final Map<SocketOption, Object> options,
                    final Map<String, Object> handlers,
                    final long idleTimeout,
                    final TimeUnit idleTimeoutUnit,
                    final long readTimeout,
                    final TimeUnit readTimeoutUnit) {

        super(channel, options, handlers, idleTimeout, idleTimeoutUnit, readTimeout, readTimeoutUnit);

        parser = new RequestParser(channel, readTimeout, readTimeoutUnit);
    }

    public void process(final WritablePipeline writablePipeline) {
        // For each request we need to register handlers.
        final Object[] pair = new Object[2];

        parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
            @Override
            public void completed(final RequestLine requestLine, final Void attachment) {
                // Found a new request line
                final ServerRequest request = new ServerRequest(parser, requestLine);
                try {
                final ServerResponse response = new ServerResponse(writablePipeline);
                pair[0] = request;
                pair[1] = response;
                }
                catch(InterruptedException ie) {
                    // TODO Ugly
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                logger.log(Level.SEVERE, exc.getMessage(), exc);
                try {
                final ServerResponse response = new ServerResponse(writablePipeline);
                response.setStatus(400, "Bad Request");
                response.end();
                }
                catch(InterruptedException ie) {
                    // TODO ugly
                    ie.printStackTrace();
                }
            }
        });

        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                final ServerRequest request = (ServerRequest) pair[0];
                final ServerResponse response = (ServerResponse) pair[1];
                if("close".equals(request.getHeader("connection"))) {
                    writablePipeline.closeWhenDone();
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

        parser.readNext();
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
