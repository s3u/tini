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

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Proxy {
    private String origin;

    /**
     * Create a server
     *
     * @return server
     */
    public static Proxy createProxy() {
        return new Proxy();
    }

    private Proxy() {
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    /**
     * Listen to requests at the given port
     *
     * @param port listen port
     */
    public void listen(int port) {
        RequestLoop requestLoop = new RequestLoop(origin);
        requestLoop.start(port);
        System.err.println("Waiting on " + port);
    }

    private static class RequestLoop {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private final ServerBootstrap bootstrap;
        private String origin;
        private static final boolean DEBUG = false;

        public RequestLoop(String origin) {
            this.origin = origin;
            Executor executor = Executors.newCachedThreadPool();
            bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor));
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(final Thread t, final Throwable e) {
                    log.error("Uncaught exception", e);
                }
            });
        }

        public void start(int port) {
            // Set up the event pipeline factory
            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                public ChannelPipeline getPipeline() throws Exception {
                    // Create a default pipeline
                    ChannelPipeline pipeline = Channels.pipeline();

                    pipeline.addLast("decoder", new HttpRequestDecoder());
                    pipeline.addLast("encoder", new HttpResponseEncoder());

                    // In the absence of HttpChunkAggregator, RequestHandler would get called several
                    // times when the request has body is chunked transfer encoded. In order to
                    // maintain a simple programming experience, for now let's use the
                    // HttpChunkAggregator - this is not efficient for proxies.
//                    pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
    //                pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());

                    // Event handler
                    pipeline.addLast("handler", new ProxyHandler(origin));
                    return pipeline;
                }
            });

            bootstrap.bind(new InetSocketAddress(port));
        }
    }
}
