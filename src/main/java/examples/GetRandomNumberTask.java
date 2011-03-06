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

package examples;

import org.tini.core.AsyncTask;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;

import static org.jboss.netty.channel.Channels.pipeline;

/**
 * Fetche a random number from a given origin and notifies dependent tasks if there are any.
 */
public class GetRandomNumberTask extends AsyncTask {
    private String origin;
    private static final ChannelFactory channelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool());

    public GetRandomNumberTask(String name, String origin) {
        super(name);
        this.origin = origin;
    }

    public void start() {
        try {
            final URI uri = new URI(origin);
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
                // Escalate error to dependents
                notifyDone(null, new IllegalArgumentException("Unsupported scheme - " + scheme));
                return;
            }

            final ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
            bootstrap.setOption("connectTimeoutMillis", 10000);
            bootstrap.setPipelineFactory(new HttpClientPipelineFactory());

            final ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    HttpRequest clientRequest = new DefaultHttpRequest(
                        HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath() + "?" + uri.getQuery());
                    clientRequest.setHeader(HttpHeaders.Names.HOST, host);

                    // Send the HTTP request.
                    future.getChannel().write(clientRequest);
                }
            });

        }
        catch(URISyntaxException use) {
            use.printStackTrace();
            notifyDone(null, use);
        }
    }

    private class HttpClientPipelineFactory implements ChannelPipelineFactory {
        public ChannelPipeline getPipeline() throws Exception {
            final ChannelPipeline pipeline = pipeline();
            pipeline.addLast("codec", new HttpClientCodec());
            pipeline.addLast("handler", new HttpResponseHandler());
            return pipeline;
        }
    }

    // This is stateful as it maintains a buffer
    private class HttpResponseHandler extends SimpleChannelUpstreamHandler {

        // Hold the result as it is being read
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            if(e.getMessage() instanceof HttpMessage) {
                HttpMessage message = (HttpMessage) e.getMessage();
                if(!message.isChunked()) {
                    int i = message.getContent().readableBytes();
                    byte[] b = new byte[i];
                    message.getContent().readBytes(b);
                    buf.append(new String(b, CharsetUtil.UTF_8));
                    notifyDone(Integer.parseInt(buf.toString().trim()), null);
                }
            }
            else if(e.getMessage() instanceof HttpChunk) {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if(chunk.isLast()) {
                    notifyDone(Integer.parseInt(buf.toString().trim()), null);
                }
                else {
                    int i = chunk.getContent().readableBytes();
                    byte[] b = new byte[i];
                    chunk.getContent().readBytes(b);
                    buf.append(new String(b, CharsetUtil.UTF_8));
                }
            }
        }
    }
}
