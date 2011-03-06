package org.tini.aio;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final Map<String, Object> handlers;

    /**
     * Create a server
     *
     * @return server
     */
    public static Server createServer() {
        return new Server();
    }

    private Server() {
        handlers = new HashMap<String, Object>();
    }

    /**
     * Specify a handler for handling requests for a given path. The handler must use JAX-RS
     * annotations to receive HTTP requests.
     *
     * @param path    path
     * @param handler handler
     */
    public void use(String path, Object handler) {
        handlers.put(path, handler);
    }

    /**
     * Listen to requests at the given port
     *
     * @param port listen port
     * @throws IOException thrown when unable to listen
     */
    public void listen(int port) throws IOException {

        final ExecutorService executorService = Executors.newCachedThreadPool();
        final AsynchronousChannelGroup threadGroup = AsynchronousChannelGroup.withCachedThreadPool(executorService, 1);
        final AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open(threadGroup);

        // Listen on an address
        server.bind(new InetSocketAddress(port));

        // Attach handler
        server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            public void completed(AsynchronousSocketChannel channel,
                                  Void attachment) {
                HttpRequest request = null;
                HttpResponse response = null;
                try {
                    request = new HttpRequest(channel);
                    response = new HttpResponse(channel);

                    invokeApp(channel, request, response);
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
                finally {
                    try {
                        // The app may not have closed. So close now.
                        // TODO: We can't close since async handlers may still be waiting
                        // TODO: We can only close after we know the app is completely done.
                        // response.close();
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                    }
                    // Offer back to service
                    server.accept(null, this);
                }
            }

            public void failed(Throwable e, Void attachment) {
                e.printStackTrace();
            }
        });

        // Wait
        System.in.read();

        server.close();
        System.err.println("Waiting on " + port);
    }


    private void invokeApp(AsynchronousSocketChannel channel, HttpRequest simpleHttpRequest, HttpResponse httpResponse) {
        String methodName = simpleHttpRequest.getMethod();
        Class methodAnnotation = javax.ws.rs.GET.class; // Default
        try {
            methodAnnotation = Class.forName("javax.ws.rs." + methodName.toUpperCase());
        }
        catch(ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
            httpResponse.setStatus(500, "Internal Server Error");
        }

        // TODO: Rails style matching

        // Dispatch
        Object handler = handlers.get(simpleHttpRequest.getRequestUri());
        handler = handler == null ? handlers.get(null) : handler;

        if(null == handler) {
            httpResponse.setStatus(404, "Not Found");
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
            hMethod = fallback;
        }
        if(hMethod != null) {
            hMethod.setAccessible(true);
            try {
                hMethod.invoke(handler, simpleHttpRequest, httpResponse);
            }
            catch(IllegalAccessException iae) {
                iae.printStackTrace();
                httpResponse.setStatus(500, "Internal Server Error");
                httpResponse.end();
            }
            catch(InvocationTargetException ite) {
                ite.printStackTrace();
                httpResponse.setStatus(500, "Internal Server Error");
                httpResponse.end();
            }
        }
        else {
            System.err.println("No handler for method " + simpleHttpRequest.getMethod() + " found");
            httpResponse.setStatus(405, "Method Not Allowed");
            httpResponse.end();
        }
    }

}
