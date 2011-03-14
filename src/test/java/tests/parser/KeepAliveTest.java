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
import org.tini.parser.ResponseLine;
import org.tini.parser.ResponseParser;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
public class KeepAliveTest {

    public static void main(final String[] args) {
        new KeepAliveTest().testChunkedResponses();
    }

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

    @Test
    public void testResponses() {
        final String req = "HTTP/1.1 200 OK\r\n" +
            "Connection: keep-alive\r\n" +
            "Content-Length: 5\r\n" +
            "\r\n" +
            "hello\r\n" +
            "HTTP/1.1 200 OK\r\n" +
            "Content-Length: 5\r\n" +
            "\r\n" +
            "HELLO";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(4);

        parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
            @Override
            public void completed(final ResponseLine result, final Void attachment) {
                lock.countDown();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
                fail();
            }
        });

        parser.onData(new CompletionHandler<ByteBuffer, Void>() {
            @Override
            public void completed(final ByteBuffer result, final Void attachment) {
                final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                if(lock.getCount() == 3) {
                    assertEquals("hello", charBuffer.toString());
                    lock.countDown();
                }
                else if(lock.getCount() == 1) {
                    assertEquals("HELLO", charBuffer.toString());
                    lock.countDown();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                fail();
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

    @Test
    public void testChunkedResponses() {
        final String req = "HTTP/1.1 200 OK\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "5\r\n" +
            "11111\r\n" +
            "a\r\n" +
            "2222222222\r\n" +
            "0\r\n" +
            "\r\n" +
            "HTTP/1.1 200 OK\r\n" +
            "Content-Length: 5\r\n" +
            "\r\n" +
            "HELLO";

        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(6);

        parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
            @Override
            public void completed(final ResponseLine result, final Void attachment) {
                lock.countDown();
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                fail();
            }
        });

        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                if(lock.getCount() == 5) {
                    assertEquals(1, result.get("transfer-encoding").size());
                    assertEquals("chunked", result.get("transfer-encoding").get(0));
                    lock.countDown();
                }
                else if(lock.getCount() == 2) {
                    assertEquals(0, result.get("transfer-encoding").size());
                    lock.countDown();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                fail();
            }
        });

        parser.onData(new CompletionHandler<ByteBuffer, Void>() {
            @Override
            public void completed(final ByteBuffer result, final Void attachment) {
                final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                if(lock.getCount() == 4) {
                    assertEquals("11111", charBuffer.toString());
                    lock.countDown();
                }
                else if(lock.getCount() == 3) {
                    assertEquals("2222222222", charBuffer.toString());
                    lock.countDown();
                }
                else if(lock.getCount() == 1) {
                    assertEquals("HELLO", charBuffer.toString());
                    lock.countDown();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                fail();
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
