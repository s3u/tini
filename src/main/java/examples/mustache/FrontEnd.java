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

package examples.mustache;

import com.sampullara.mustache.Mustache;
import com.sampullara.mustache.MustacheCompiler;
import com.sampullara.mustache.MustacheException;
import com.sampullara.mustache.Scope;
import org.jboss.netty.util.CharsetUtil;
import org.tini.core.TiniRequest;
import org.tini.core.Server;
import org.tini.core.TiniResponse;

import javax.ws.rs.GET;

import java.io.File;
import java.io.StringWriter;

public class FrontEnd {
    public static void main(String[] args) {
        Server server = Server.createServer();

        // This is the default handler for the server
        Object handler = new Object() {
            @GET
            public void get(TiniRequest request, TiniResponse response) {
                response.setHeader("Connection", "keep-alive");
                File root = new File("./src/main/java/examples/mustache/");

                try {
                    MustacheCompiler c = new MustacheCompiler(root);
                    c.setOutputDirectory("target/classes");
                    Mustache m = c.parseFile("index.mustache");

                    StringWriter sw = new StringWriter();
                    m.execute(sw, new Scope(new Object() {
                        String name = "Subbu";
                    }));
                    sw.flush();
                    byte[] content = sw.toString().getBytes(CharsetUtil.UTF_8);
                    response.setContentLength(content.length);
                    response.write(content);
                }
                catch(MustacheException me) {
                    me.printStackTrace();
                }

                // Done
                response.close();
            }

        };
        server.use(null, handler);
        server.listen(3000);
    }

    public class Context {
        String name() {
            return "Subbu";
        }
    }
}
