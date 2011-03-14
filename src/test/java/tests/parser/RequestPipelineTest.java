/*
 * Copyright (c) 2011 CONTRIBUTORS
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

package tests.parser;

import org.junit.Test;
import org.tini.parser.RequestLine;
import org.tini.parser.RequestParser;

import java.io.ByteArrayInputStream;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
public class RequestPipelineTest {
    @Test
    public void testBodylessRequests() {
        final String req = "GET /0 HTTP/1.1\r\n" +
            "Connection: keep-alive\r\n" +
            "\r\n" +
            "GET /1 HTTP/1.1\r\n" +
            "Connection: keep-alive\r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final RequestParser parser = new RequestParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(2);

        parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
            @Override
            public void completed(final RequestLine result, final Void attachment) {
                if(lock.getCount() == 2) {
                    assertEquals("/0", result.getUri());
                    lock.countDown();
                }
                else if(lock.getCount() == 1) {
                    assertEquals("/1", result.getUri());
                    lock.countDown();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
            }
        });
        parser.go();
        try {
            lock.await(10, TimeUnit.SECONDS);
        }
        catch(InterruptedException ie) {
            fail("Pending tests");
        }
        finally {
            assertEquals(0, lock.getCount());
        }
    }
}
