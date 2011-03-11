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
import org.tini.parser.ResponseLine;
import org.tini.parser.ResponseParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Subbu Allamaraju
 */
public class ResponseParserTest {

    @Test
    public void testResponseLine() {
        final String req = "HTTP/1.1 200 OK\r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(2);

        parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
            @Override
            public void completed(final ResponseLine result, final Void attachment) {
                assertEquals(200, result.getCode());
                assertEquals("OK", result.getStatus());
                assertEquals("HTTP/1.1", result.getVersion());
                lock.countDown();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
            }
        });

        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                assertEquals(0, result.size());
                lock.countDown();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
            }
        });
        parser.readNext();
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

    @Test
    public void testMalformedResponseLine() {
        final String req = "HTTP/1.1 Hello OK\r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(1);

        parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
            @Override
            public void completed(final ResponseLine result, final Void attachment) {
                fail("Should fail");
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                lock.countDown();
            }
        });

        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                fail("Should fail");
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                fail("Should fail");
            }
        });
        parser.readNext();
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

    @Test
    public void testRequestLineWithHeaders() {
        final String req = "HTTP/1.1 200 OK\r\n" +
            "H1: v1\r\n" +
            "H2: v2\r\n" +
            "H1: v11\r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(2);

        parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
            @Override
            public void completed(final ResponseLine result, final Void attachment) {
                assertEquals(200, result.getCode());
                assertEquals("OK", result.getStatus());
                assertEquals("HTTP/1.1", result.getVersion());
                lock.countDown();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
            }
        });

        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                assertEquals(2, result.size());
                assertEquals(2, result.get("h1").size());
                assertEquals("v1", result.get("h1").get(0));
                assertEquals("v11", result.get("h1").get(1));
                assertEquals(1, result.get("h2").size());
                assertEquals("v2", result.get("h2").get(0));
                lock.countDown();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
            }
        });
        parser.readNext();
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
