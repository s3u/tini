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

package org.tini.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * An async HTTP client
 */
public class HttpClient {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private static final ChannelFactory channelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool());

    /**
     * Makes an HTTP request.
     *
     * @param requestHandler  a handler for request
     * @param responseHandler a handler for response
     */
    public final void request(final RequestHandler requestHandler, final ResponseHandler responseHandler) throws URISyntaxException {
        logger.info("Sending request to " + requestHandler.uri());

        String str = requestHandler.uri();
        str = str.startsWith("http://") || str.startsWith("https://") ? str : "http://" + str;
        final URI uri = new URI(str);
        final String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        final String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = uri.getPort();
        if(port == -1) {
            if(scheme.equalsIgnoreCase("http")) {
                port = 80;
            }
            else if(scheme.equalsIgnoreCase("https")) {
                port = 443;
            }
        }
        if(!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            responseHandler.onError(new IllegalArgumentException("Unsupported scheme " + scheme));
            return;
        }

        final ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new HttpClientPipelineFactory(requestHandler, responseHandler));

        str = uri.getPath();
        if(uri.getQuery() != null) {
            str += "?" + uri.getRawQuery();
        }
        if(uri.getFragment() != null) {
            str += "#" + uri.getRawFragment();
        }
        final String requestUri = str;
        final ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(future.getCause() != null) {
                    // Failure - can't proceed
                    logger.error("Failed to connect to " + requestHandler.uri(), future.getCause());
                    responseHandler.onError(future.getCause());
                }
                else {
                    logger.info("Successfully connected to " + requestHandler.uri());
                    if(HttpMethod.CONNECT.equals(requestHandler.method())) {
                        // Just copy the whole thing
                        ChannelFuture writeFuture = future.getChannel().write(requestHandler.body());
                        writeFuture.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                future.getChannel().write(requestHandler.body());
                            }
                        });
                    }
                    else {
                        org.jboss.netty.handler.codec.http.HttpRequest clientRequest = new DefaultHttpRequest(
                            HttpVersion.HTTP_1_1, requestHandler.method(), requestUri);
                        clientRequest.setHeader(HttpHeaders.Names.HOST, host);

                        // Copy headers
                        // TODO Filter
                        Map<String, Iterable<String>> headers = requestHandler.headers();
                        for(String name : headers.keySet()) {
                            clientRequest.setHeader(name, headers.get(name));
                        }

                        // Send the HTTP request.
                        ChannelFuture writeFuture = future.getChannel().write(clientRequest);
                        writeFuture.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if(future.getCause() != null) {
                                    logger.error("Unable to send request to " + requestHandler.uri(), future.getCause());
                                    responseHandler.onError(future.getCause());
                                }
                                else {
                                    logger.error("Successfully sent request to " + requestHandler.uri());
                                    // TODO: Proxy the body when there is a body.
                                    // TODO: But the sender may not have the data yet.
                                    // TODO: So, the client will call us when there is data.
                                    // TODO: But this is very very awkward.
                                    // Now ready to send data

                                    // Alternatively, provide access to the incoming message
                                    future.getChannel().write(requestHandler.body());

                                }
                            }
                        });
                    }
                }
            }

        });
    }

    /**
     * A request handler
     */
    public interface RequestHandler {

        /**
         * Return the name of the HTTP method
         *
         * @return method
         */
        HttpMethod method();

        /**
         * Return the URI
         *
         * @return uri
         */
        String uri();

        /**
         * Return request headers to be sent to the origin
         *
         * @return headers
         */
        Map<String, Iterable<String>> headers();

        ChannelBuffer body();

    }

    public interface ResponseHandler {

        void onError(Throwable t);

        void onStatus(HttpResponseStatus status);

        void onHeader(String name, String value);

        void onBody(ChannelBuffer channelBuffer);

        void done();
    }

    private static class HttpClientPipelineFactory implements ChannelPipelineFactory {
        private final RequestHandler requestHandler;
        private final ResponseHandler responseHandler;

        public HttpClientPipelineFactory(RequestHandler requestHandler, ResponseHandler responseHandler) {
            this.requestHandler = requestHandler;
            this.responseHandler = responseHandler;
        }

        public ChannelPipeline getPipeline() throws Exception {
            // Create a default pipeline implementation.
            final ChannelPipeline pipeline = Channels.pipeline();

            if(HttpMethod.CONNECT.equals(requestHandler.method())) {
                pipeline.addLast("handler", new HttpResponseHandler(responseHandler));
            }
            else {
                pipeline.addLast("codec", new HttpClientCodec());
                pipeline.addLast("handler", new HttpResponseHandler(responseHandler));
            }
            return pipeline;
        }
    }

    private static class HttpResponseHandler extends SimpleChannelUpstreamHandler {

        private final ResponseHandler responseHandler;

        HttpResponseHandler(ResponseHandler responseHandler) {
            super();
            this.responseHandler = responseHandler;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            if(e.getMessage() instanceof org.jboss.netty.handler.codec.http.HttpResponse) {
                org.jboss.netty.handler.codec.http.HttpResponse clientResponse = (org.jboss.netty.handler.codec.http.HttpResponse) e.getMessage();
                responseHandler.onStatus(clientResponse.getStatus());
                for(String name : clientResponse.getHeaderNames()) {
                    responseHandler.onHeader(name, clientResponse.getHeader(name));
                }
            }
            else {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if(chunk.isLast()) {
                    responseHandler.done();

                }
                else {
                    ChannelBuffer channelBuffer = chunk.getContent();
                    responseHandler.onBody(channelBuffer);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            // TODO: Error handling
            e.getCause().printStackTrace();
        }
    }
}
