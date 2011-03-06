/*
 * Copyright (c) 2011 Subbu Allamaraju
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
package org.tini.core;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

class RequestHandler extends SimpleChannelUpstreamHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, Object> handlers;
    private TiniRequest httpRequest;
    private TiniResponse httpResponse;

    public RequestHandler(Map<String, Object> handlers) {
        super();
        this.handlers = handlers;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent event) {
        logger.info("Message received");
        if(event.getMessage() instanceof HttpMessage) {
            httpRequest = new TiniRequest(event);
            httpResponse = new TiniResponse(event);
            invokeApp(event, httpRequest, httpResponse);
        }
        else if(event.getMessage() instanceof HttpChunk) {
            // Just call the handler's onData
//            httpRequest.sendBody((HttpChunk) event.getMessage());
        }

    }

    private void invokeApp(MessageEvent event, TiniRequest simpleHttpRequest, TiniResponse httpResponse) {
        HttpMethod httpMethod = simpleHttpRequest.getMethod();
        Class methodAnnotation = javax.ws.rs.GET.class; // Default
        try {
            methodAnnotation = Class.forName("javax.ws.rs." + httpMethod.getName().toUpperCase());
        }
        catch(ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            httpResponse.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }

        // TODO: Rails style matching

        // Dispatch
        Object handler = handlers.get(simpleHttpRequest.getRequestUri());
        handler = handler == null ? handlers.get(null) : handler;

        if(null == handler) {
            httpResponse.setStatus(HttpResponseStatus.NOT_FOUND);
            return;
        }
        final Method[] methods = handler.getClass().getMethods();
        Method hMethod = null;
        Method fallback = null;
        for(Method method : methods) {
            if(method.getAnnotation(methodAnnotation) != null) {
                hMethod = method;
                break;
            }
            else if(method.getName().equals("service")) {
                fallback = method;
            }
        }
        if(hMethod == null && fallback != null) {
            logger.info("No handler for " + simpleHttpRequest.getMethod().getName() + " found. Using the fallback handler");
            hMethod = fallback;
        }
        if(hMethod != null) {
            hMethod.setAccessible(true);
            try {
                hMethod.invoke(handler, simpleHttpRequest, httpResponse);
                // Immediately send the body if the request is not chunked
                if(event.getMessage() instanceof HttpMessage &&
                    !((HttpMessage) event.getMessage()).isChunked()) {
//                    httpRequest.sendBody((HttpMessage) event.getMessage());
                }
            }
            catch(IllegalAccessException iae) {
                iae.printStackTrace();
                httpResponse.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                httpResponse.close();
            }
            catch(InvocationTargetException ite) {
                ite.printStackTrace();
                httpResponse.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                httpResponse.close();
            }
        }
        else {
            logger.error("No handler for method " + simpleHttpRequest.getMethod().getName() + " found");
            httpResponse.setStatus(HttpResponseStatus.METHOD_NOT_ALLOWED);
            httpResponse.close();
        }
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
    }
}
