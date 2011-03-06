package org.tini.core;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.tini.client.HttpClient;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyHandler extends SimpleChannelUpstreamHandler {

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final String origin;

    public ProxyHandler(String origin) {
        this.origin = origin;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext context, final MessageEvent event) throws Exception {

        if(event.getMessage() instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) event.getMessage();
            if(HttpMethod.CONNECT.equals(request.getMethod())) {
                // Tunnel the traffic

                // The first line is already read - so get the host:port
                String requestUri = request.getUri();
                String host;
                int port;
                int pos = requestUri.indexOf(":");
                if(pos != -1) {
                    host = requestUri.substring(0, pos);
                    // TODO: parse exception
                    port = Integer.parseInt(requestUri.substring(pos + 1));
                }
                else {
                    host = requestUri;
                    port = 80;
                }

                // Now connect
                final ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(
                    pool, pool));
                final ChannelPipelineFactory pipelineFactory = new ChannelPipelineFactory() {
                    @Override
                    public ChannelPipeline getPipeline() throws Exception {
                        final ChannelPipeline pipeline = Channels.pipeline();
                        pipeline.addLast("handler", new ConnectTunnelHandler(event.getChannel()));
                        return pipeline;
                    }
                };
                bootstrap.setPipelineFactory(pipelineFactory);
                final ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
                // Once the connection is established,
                future.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if(future.isSuccess()) {
                            // make the request channel writable
                            event.getChannel().setReadable(false);

                            // relay the response to the client
                            HttpResponse nettyResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "Connection Established"));
                            nettyResponse.addHeader("Connection", "keep-alive");
                            nettyResponse.addHeader("Proxy-Connection", "keep-alive");
                            nettyResponse.addHeader("Via", "ImMe");
                            event.getChannel().write(nettyResponse);
                            // and then make the request channel readonly
                            event.getChannel().setReadable(true);

                            // From and relay the traffic
                            // But before that remove the handlers that Proxy added before
                            context.getPipeline().remove("encoder");
                            context.getPipeline().remove("decoder");
                            context.getPipeline().remove("handler");
                            // Then add the tunnel
                            context.getPipeline().addLast("handler",
                                new ConnectTunnelHandler(future.getChannel()));
                        }
                    }
                });
            }
            else {
                // Make a request to the origin
                repeatRequest(context, event);
            }

        }
    }

    private void repeatRequest(ChannelHandlerContext context, MessageEvent event) throws Exception {
        final TiniRequest request = new TiniRequest(event);
        final TiniResponse response = new TiniResponse(event);

        HttpClient client = new HttpClient();
        try {
            client.request(new HttpClient.RequestHandler() {
                @Override
                public HttpMethod method() {
                    return request.getMethod();
                }

                @Override
                public String uri() {
                    if(origin == null) {
                        // Assume forward proxy mode
                        return request.getRequestUri();
                    }
                    else {
                        return origin + request.getRequestUri();
                    }
                }

                @Override
                public Map<String, Iterable<String>> headers() {
                    Map<String, Iterable<String>> headers = new HashMap<String, Iterable<String>>();
                    for(String name : request.getHeaderNames()) {
                        List<String> values = request.getHeaders(name);
                        headers.put(name, values);
                    }
                    return headers;
                }

                @Override
                public ChannelBuffer body() {
                    return request.getBody();
                }
            },
                new HttpClient.ResponseHandler() {
                    @Override
                    public void onError(Throwable t) {
                        if(t instanceof URISyntaxException) {
                            response.setStatus(HttpResponseStatus.BAD_REQUEST);
                        }
                        else if(t instanceof ConnectException) {
                            response.setStatus(HttpResponseStatus.GATEWAY_TIMEOUT);
                        }
                        else {
                            response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                        }
                        response.close();
                    }

                    @Override
                    public void onStatus(HttpResponseStatus status) {
                        response.setStatus(status);
                    }

                    @Override
                    public void onHeader(String name, String value) {
                        response.addHeader(name, value);
                    }

                    @Override
                    public void onBody(ChannelBuffer channelBuffer) {
                        response.write(channelBuffer);
                    }

                    @Override
                    public void done() {
                        response.close();
                    }
                });
        }
        catch(URISyntaxException use) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            response.close();
        }

    }

    private class ConnectTunnelHandler extends SimpleChannelUpstreamHandler {
        final private Channel destChannel;

        private ConnectTunnelHandler(Channel channel) {
            destChannel = channel;
        }

        @Override
        public void messageReceived(final ChannelHandlerContext context, MessageEvent event) {
            ChannelFuture future = destChannel.write(event.getMessage());
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(!future.isSuccess()) {
                        // TODO: Error handling
                        System.err.println("Error writing");
                    }
                }
            });
        }

    }
}
