Tini is an async http server skeleton. It is Java based and uses the Netty framework for I/O. You
can use this to write web apps, HTTP/REST services, or even proxy servers.

Here is how to write an app

    import org.tini.core.HttpResponse;
    import org.tini.core.HttpRequest;
    import org.tini.core.Server;
    import org.jboss.netty.util.CharsetUtil;

    import javax.ws.rs.GET;

    public class HelloWorldApp {

        public static void main(String[] args) {
            Server server = Server.createServer();
            server.use("/hello", new Object() {
                @GET
                public void get(HttpRequest request, HttpResponse response) {
                    response.setVersion(HttpVersion.HTTP_1_1);
                    response.setStatus(HttpResponseStatus.OK);
                    response.setContentType("text/plain; charset=UTF-8");
                    response.write("Hello World".getBytes(CharsetUtil.UTF_8));
                    response.close();
                }
            });
            server.listen(3000);
        }
    }

That's all.