package examples.netty;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NettyPipeliningClient {

    private String host;
    private int port;
    private ChannelFactory factory;
    private ClientHandler handler;

    public NettyPipeliningClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean init() {
        this.factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
        this.handler = new ClientHandler();
        ClientBootstrap bootstrap = new ClientBootstrap(this.factory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("codec", new HttpClientCodec());
                pipeline.addLast("aggregator", new HttpChunkAggregator(10000));
                pipeline.addLast("handler", handler);
                return pipeline;
            }
        });

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(this.host, this.port));
        return future.awaitUninterruptibly().isSuccess();
    }

    public void terminate() {
        if(this.handler != null) {
            this.handler.terminate();
        }

        if(this.factory != null) {
            this.factory.releaseExternalResources();
        }
    }

    public void sendRequests(int requests, long interval) {
        for(int i = 0; i < requests; i++) {
            if(interval > 0) {
                try {
                    Thread.sleep(interval);
                }
                catch(InterruptedException e) {
                    Thread.interrupted();
                    return;
                }
            }
            this.handler.sendRequest(i >= requests - 1);
        }
    }

    private final class ClientHandler extends SimpleChannelUpstreamHandler {
        private Channel channel;
        private final AtomicInteger uriGenerator;
        private AtomicInteger lastReceived;

        private ClientHandler() {
            this.uriGenerator = new AtomicInteger();
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx,
                                     ChannelStateEvent e) throws Exception {
            this.channel = e.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            HttpResponse response = (HttpResponse) e.getMessage();
            String val = response.getHeader("connection");
            String body = response.getContent().toString(CharsetUtil.UTF_8).trim();
            int number = Integer.parseInt(body.substring(1));

            if(this.lastReceived != null) {
                if(number <= this.lastReceived.getAndIncrement()) {
                    System.err.println(">> OUT OF ORDER! Expecting " +
                        (this.lastReceived.get() - 1) + " but got " + number);
                    this.lastReceived.set(number);
                }
                else {
                    System.out.println(">> " + number + " (IN ORDER) " + val);
                }
            }
            else {
                this.lastReceived = new AtomicInteger(number);
                System.out.println(">> " + number + " (FIRST, IN ORDER)");
            }
        }

        public void sendRequest(boolean close) {
            if((this.channel == null) || !this.channel.isConnected()) {
                System.err.println("sendRequest() not yet connected!");
                return;
            }

            String uri = "/" + this.uriGenerator.incrementAndGet();
            final HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, uri);
            request.addHeader("Host", "localhost:8080");
            request.addHeader("Connection", close ? "close" : "keep-alive");
            ChannelFuture future = this.channel.write(request);
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(future.isSuccess()) {
                        System.out.println("<< " + request.getMethod() + " " + request.getUri());
                    }
                }
            });
        }

        public void terminate() {
            if((this.channel != null) && this.channel.isConnected()) {
                this.channel.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        final NettyPipeliningClient client = new NettyPipeliningClient("localhost", 3000);
        if(!client.init()) {
            System.err.println("Failed to initialise client.");
            return;
        }

        // Send requests, with intervals of 1ms
        client.sendRequests(4, 1);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                client.terminate();
            }
        });
    }
}