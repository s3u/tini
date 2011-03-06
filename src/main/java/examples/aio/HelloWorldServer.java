package examples.aio;

import org.jboss.netty.util.CharsetUtil;
import org.tini.aio.HttpRequest;
import org.tini.aio.HttpResponse;
import org.tini.aio.Server;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import java.util.concurrent.ExecutionException;

public class HelloWorldServer {

    public static void main(String[] args) throws Exception {
        Server server = Server.createServer();
        server.use("/users", new Object() {
            @GET
            public void get(HttpRequest request, HttpResponse response) throws InterruptedException, ExecutionException  {
                response.setContentType("text/plain; charset=UTF-8");
                for(int i = 0; i < 100; i++) {
                    response.write("Hello World".getBytes(CharsetUtil.UTF_8));
                }

                for(int i = 0; i < 100; i++) {
                    response.write("Hello World".getBytes(CharsetUtil.UTF_8));

                }
                response.end();
            }
        });
        // This is the default handler for the server
        server.use(null, new Object() {
            @GET
            @PUT
            @DELETE
            @HEAD
            @POST
            public void get(HttpRequest request, HttpResponse response) throws InterruptedException, ExecutionException {
                response.setContentType("text/html");
                response.write("<p>Hello World</p>");
                response.end();
            }

        });
        server.listen(3000);
    }
}
