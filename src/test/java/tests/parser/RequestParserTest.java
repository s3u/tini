package tests.parser;

import org.junit.Test;
import org.tini.parser.RequestLine;
import org.tini.parser.RequestParser;

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


public class RequestParserTest {


    public static void main(final String[] args) {
        new RequestParserTest().testFixedBody();
    }

    @Test
    public void testRequestLine() {
        final String req = "GET /foo HTTP/1.1\r\n" +
            "Host: foo.com\r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));

        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final RequestParser parser = new RequestParser(channel, 100, TimeUnit.SECONDS);
        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {

            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {

            }
        });
    }

    @Test
    public void testMalformedRequestLine() {
        final String req = "GET \r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final RequestParser parser = new RequestParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(1);

        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
                    @Override
                    public void completed(final RequestLine result, final Void attachment) {
                        fail("Should fail");
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        synchronized(lock) {
                            lock.countDown();
                        }
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
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
            }
        });
        parser.readNext();
        synchronized(lock) {
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

    @Test
    public void testRequestLineWithHeaders() {
        final String req = "GET / HTTP/1.1\r\n" +
            "H1: v1\r\n" +
            "H2: v2\r\n" +
            "H1: v11\r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final RequestParser parser = new RequestParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(2);

        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
                    @Override
                    public void completed(final RequestLine result, final Void attachment) {
                        assertEquals("GET", result.getMethod());
                        assertEquals("/", result.getUri());
                        assertEquals("HTTP/1.1", result.getVersion());
                        synchronized(lock) {
                            lock.countDown();
                        }
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
                        synchronized(lock) {
                            lock.countDown();
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                    }
                });
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
            }
        });
        parser.readNext();
        synchronized(lock) {
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

    @Test
    public void testFixedBody() {
        final String req = "GET / HTTP/1.1\r\n" +
            "Content-Length: 5\r\n" +
            "\r\n" +
            "hello";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final RequestParser parser = new RequestParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(3);

        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
                    @Override
                    public void completed(final RequestLine result, final Void attachment) {
                        assertEquals("GET", result.getMethod());
                        assertEquals("/", result.getUri());
                        assertEquals("HTTP/1.1", result.getVersion());
                        synchronized(lock) {
                            lock.countDown();
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        exc.printStackTrace();
                    }
                });

                parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        assertEquals(1, result.size());
                        assertEquals(1, result.get("content-length").size());
                        assertEquals("5", result.get("content-length").get(0));
                        synchronized(lock) {
                            lock.countDown();
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                    }
                });

                parser.onData(new CompletionHandler<ByteBuffer, Void>() {
                    @Override
                    public void completed(final ByteBuffer result, final Void attachment) {
                        final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                        assertEquals("hello", charBuffer.toString());
                        lock.countDown();
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                    }
                });
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
            }
        });
        parser.readNext();
        synchronized(lock) {
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
}
