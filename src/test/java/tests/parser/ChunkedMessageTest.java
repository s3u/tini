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

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Subbu Allamaraju
 */
public class ChunkedMessageTest {

    public static void main(final String[] args) {
        new ChunkedMessageTest().testChunkedBody();
    }

    @Test
    public void testChunkedBody() {
        final String req = "HTTP/1.1 200 OK\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "5\r\n" +
            "11111\r\n" +
            "a\r\n" +
            "2222222222\r\n" +
            "0\r\n\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(2);

        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {
                parser.onData(new CompletionHandler<ByteBuffer, Void>() {
                    @Override
                    public void completed(final ByteBuffer result, final Void attachment) {
                        final CharBuffer charBuffer = Charset.forName("UTF-8").decode(result);
                        if(lock.getCount() == 2) {
                            assertEquals("11111", charBuffer.toString());
                            lock.countDown();
                        }
                        else if(lock.getCount() == 1) {
                            assertEquals("2222222222", charBuffer.toString());
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
    public void testTrailers() {
        final String req = "HTTP/1.1 200 OK\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "Trailer: connection\r\n" +
            "\r\n" +
            "5\r\n" +
            "11111\r\n" +
            "a\r\n" +
            "2222222222\r\n" +
            "0\r\n" +
            "Connection: close\r\n" +
            "\r\n";
        final ByteArrayInputStream bais = new ByteArrayInputStream(req.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(5);

        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {

                parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
                    @Override
                    public void completed(final ResponseLine result, final Void attachment) {
                        assertEquals(200, result.getCode());
                        assertEquals("OK", result.getStatus());
                        assertEquals("HTTP/1.1", result.getVersion());
                        synchronized(lock) {
                            lock.countDown();
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                    }
                });

                parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        assertEquals(2, result.size());
                        assertEquals(1, result.get("transfer-encoding").size());
                        assertEquals("chunked", result.get("transfer-encoding").get(0));
                        assertEquals(1, result.get("trailer").size());
                        assertEquals("connection", result.get("trailer").get(0));
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
                        if(lock.getCount() == 3) {
                            assertEquals("11111", charBuffer.toString());
                            lock.countDown();
                        }
                        else if(lock.getCount() == 2) {
                            assertEquals("2222222222", charBuffer.toString());
                            lock.countDown();
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                    }
                });

                parser.onTrailers(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        assertEquals(1, result.size());
                        assertEquals(1, result.get("connection").size());
                        assertEquals("close", result.get("connection").get(0));
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
    public void testLargeChunk() {
        final String content = "HTTP/1.1 200 OK\r\n" +
            "X-Robots-Tag: noarchive\r\n" +
            "Expires: Thu, 17 Feb 2011 16:03:18 GMT\r\n" +
            "Date: Thu, 17 Feb 2011 15:58:18 GMT\r\n" +
            "Content-Type: text/html; charset=UTF-8\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "X-Frame-Options: SAMEORIGIN\r\n" +
            "X-XSS-Protection: 1; mode=block\r\n" +
            "Server: GSE\r\n" +
            "Cache-Control: public, max-age=300v\n" +
            "Age: 261\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "\r\n" +
            "1000\r\n" +
            "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3" +
            ".org/TR/html4/strict.dtd\"><html><head><title>AFP: US reporter be" +
            "aten covering Bahrain unrest</title>.<meta http-equiv=\"Content-T" +
            "ype\" content=\"text/html; charset=UTF-8\">.<meta http-equiv=\"Conte" +
            "nt-Language\" content=\"en\">.<meta name=\"googlebot\" content=\"noarc" +
            "hive\">..<link rel=\"Stylesheet\" type=\"text/css\" media=\"screen\" hr" +
            "ef=\"/hostednews/css/2338391134-screen.css\">.<link rel=\"Styleshee" +
            "t\" type=\"text/css\" media=\"print\" href=\"/hostednews/css/343495019" +
            "3-print.css\">..<script type=\"text/javascript\" src=\"/hostednews/j" +
            "s/2671202462-slideshow.js\"></script>.</head>.<body><script type=" +
            "\"text/javascript\">.      var gaJsHost = ((\"https:\" == document.l" +
            "ocation.protocol) ? \"https://ssl.\" : \"http://www.\");.      docum" +
            "ent.write(unescape(\"%3Cscript src='\" + gaJsHost + \"google-analyt" +
            "ics.com/ga.js' type='text/javascript'%3E%3C/script%3E\"));.    </" +
            "script>.<script type=\"text/javascript\">.      _gaq.push(['_" +
            "setAccount', \"UA-2467371-6\"]);.      _gaq.push(['_setCookiePath'" +
            ", '/hostednews/']);.      _gaq.push(['_trackPageview']);.    </s" +
            "cript>.<div class=\"g-doc\"><div class=\"g-section\" id=\"hn-header\">" +
            "<div class=\"g-unit\"><form class=\"search-form\" action=\"http://new" +
            "s.google.com/news?hl=en\" method=\"get\"><span class=\"hn-attr\"><spa" +
            "n class=\"hn-hosted-by\">Hosted by</span>.<img src=\"/hostednews/im" +
            "g/small-google-logo.gif\" alt=\"Google\"></span>.<input name=\"hl\" v" +
            "alue=\"en\" type=\"hidden\">.<input name=\"ned\" value=\"\" type=\"hidden" +
            "\">.<input class=\"hn-search-field\" size=\"30\" maxlength=\"2048\" nam" +
            "e=\"q\" tabindex=\"1\" value=\"\" type=\"text\">.<input type=\"submit\" ta" +
            "bindex=\"2\" value=\"Search News\">.<a href=\"http://news.google.com/" +
            "news?hl=en\" id=\"hn-news-link\" onclick=\"pageTracker._trackPagevie" +
            "w(&#39;/outgoing/google_news&#39;)\">Back to Google News</a></for" +
            "m></div></div></div>.<div class=\"g-doc-800\"><div class=\"g-sectio" +
            "n hn-article\"><div class=\"g-unit g-first\"><div class=\"hn-copy\"><" +
            "div class=\"g-section\"><!-- google_ad_section_start(name=article)" +
            " -->.<div id=\"hn-headline\">US reporter beaten covering Bahrain u" +
            "nrest</div>.<p class=\"hn-byline\">.(AFP).&ndash;.<span class=\"hn-" +
            "date\">5 hours ago</span></p>.<p>WASHINGTON &#x2014; A US reporte" +
            "r for ABC News was beaten by thugs armed with clubs early Thursd" +
            "ay while covering the unrest in Bahrain, the US network reported" +
            ".</p><p>Correspondent Miguel Marquez was caught in the crowd and" +
            " attacked while covering protests in Manama, ABC said.</p><p>Mar" +
            "quez, who said he was not badly injured, was clubbed while he wa" +
            "s on the phone with his headquarters in New York describing the " +
            "scene as riot police stormed through a Manama square in the dark" +
            " in a harsh crackdown on anti-regime protesters.</p><p>\"No! No! " +
            "No! Hey! I'm a journalist here!\" he yelled while still on the ph" +
            "one. \"I'm going! I'm going! I'm going! I'm going! ... I'm hit.\"<" +
            "/p><p>He said that the thugs pulled his camera out of his hands." +
            "</p><p>\"I just got beat rather badly by a gang of thugs,\" Marque" +
            "z said in a later call to ABC headquarters. \"I'm now in a market" +
            "place near our hotel where people are cowering in buildings.\"</p" +
            "><p>Witnesses and opposition said that four people were killed a" +
            "nd up to 95 wounded when police launched the operation in the ic" +
            "onic Pearl Square without warning at around 3:00 am (midnight GM" +
            "T), sending protesters fleeing in panic.</p><p>CBS News said its" +
            " top foreign correspondent Lara Logan suffered a brutal sexual a" +
            "ssault at the hands of a mob in Egypt while covering the downfal" +
            "l of president Hosni Mubarak last week.</p><p>The New York-based" +
            " Committee to Protect Journalists said Wednesday that it was con" +
            "cerned \"about the continued assaults on journalists covering ant" +
            "i-government demonstrations in the Middle East.\"</p><p>\"In recen" +
            "t days, journalists have been obstructed, assaulted, or detained" +
            " in Libya, Bahrain, Iran, and Yemen,\" the watchdog group said.</" +
            "p><p>It also said the Bahrain government \"has selectively reduce" +
            "d the speed of Internet connections inside the country for the p" +
            "ast two days.\"</p><p>The Internet is being slowed down \"in newsp" +
            "aper offices, hotels, and homes but not in governmental institut" +
            "ions\"\r\n" +
            "1000\r\n" +
            ", and the video-sharing website Bambuser had been blocked.</p><p" +
            ">The CPJ also said that Bahrain interior ministry officials summ" +
            "oned a photographer for The Associated Press, Hassan Jamali, for" +
            " questioning after he took \"pictures of people injured in anti-g" +
            "overnment demonstrations\" and ordered him \"not take additional p" +
            "ictures of the injured.\"</p>.<!-- google_ad_section_end(name=art" +
            "icle) -->...<p id=\"hn-distributor-copyright\"><span>Copyright &co" +
            "py;  2011   AFP. All rights reserved..<a href=\"/hostednews/afp/c" +
            "opyright?hl=en\">More &raquo;</a></span></p></div>.<div class=\"g-" +
            "section\"><div id=\"rn-section\"><h4 id=\"rn-header\">Related article" +
            "s</h4>.<ul><li><a href=\"http://www.newser.com/story/112255/migue" +
            "l-marquez-abc-reporter-beaten-in-bahrain.html\" onclick=\"pageTrac" +
            "ker._trackPageview(&#39;/outgoing/related_news&#39;);\">Miguel Ma+" +
            "rquez: ABC Reporter Beaten in Bahrain</a>.<br>.<span class=\"sour+" +
            "ce\">Newser</span>.-.4 hours ago</li> <li><a href=\"http://bikyama" +
            "sr.com/wordpress/?p=27475\" onclick=\"pageTracker._trackPageview(&" +
            "#39;/outgoing/related_news&#39;);\">ABC reporter beaten in Bahrai" +
            "n</a>.<br>.<span class=\"source\">Bikya Masr</span>.-.9 hours ago<" +
            "/li> <li><a href=\"http://newsinfo.inquirer.net/breakingnews/worl" +
            "d/view/20110217-320811/US-reporter-beaten-covering-Bahrain-unres" +
            "t\" onclick=\"pageTr" +
            "acker._trackPageview(&#39;/outgoing/related_news&#39;);\">US repo" +
            "rter beaten covering Bahrain unrest</a>.<br>.<span class=\"source" +
            "\">Inquirer.net</span>.-.5 hours ago</li>.<li id=\"rn-more\"><a hre" +
            "f=\"http://news.google.com/news/story?ncl=drZKNY4t0zfNmdMjUgg0_Aj" +
            "TPIRSM&amp;hl=en&amp;ned=us\" onclick=\"pageTracker._trackPageview" +
            "(&#39;/outgoing/full_coverage&#39;);\">More coverage.(1) &raquo;<" +
            "/a></li></ul></div>.<script type=\"text/javascript\">.    function" +
            " adsIframeHtml() {.      return ['<!DOCTYPE html><html><body sty" +
            "le=\"margin: 0pt; padding: 0pt;\"><script type=\"text/javascript\">'" +
            ",.          'google_ad_client = \"ca-google-hostednews-afp\";',.. " +
            "         .            'google_page_url = \"http://www.google.com/" +
            "hostednews/afp/article/ALeqM5gjbkV9uYKA_QJtwvfbx8guFl04Hg?docId\\" +
            "x3dCNG.68e525354daffd868eac000986513f10.151\";',.          ..    " +
            "      'google_language = \"en\";',.          'google_ad_section = " +
            "\"article\";',.          'google_ad_format = \"300x250_as\";',.     " +
            "     'google_ui_version = 1;',.          'google_ad_type = \"text" +
            "\";',.          'google_safe = \"high\";',.          'google_ad_hei" +
            "ght = \"250\";',.          'google_ad_width = \"300\";',.          '" +
            "google_color_bg = \"FFFFFF\";',.          'google_color_border = \"" +
            "0066CC\";',.          'google_color_line = \"FFFFFF\";',.          " +
            "'google_color_link = \"003399\";',.          'google_color_text = " +
            "\"000000\";',.          'google_color_url = \"008000\";',.          " +
            "'<\\/script><script src=\"http://pagead2.googlesyndication.com/pag" +
            "ead/show_ads.js\"><\\/script><\\/body><\\/html>'].join('\');.    }." +
            "  </script>.<iframe class=\"hn-ads\" frameborder=\"0\" scrolling=\"no" +
            "\" src=\"javascript:parent.adsIframeHtml()\"></iframe></div>.<div c" +
            "lass=\"g-section\"><div class=\"igoogle-promo\"><a href=\"http://www." +
            "google.com/ig/add?feedurl=http://news.google.com/news?num%3D10%2" +
            "6output%3Datom%26hl%3Den&amp;hl=en\"><img src=\"/hostednews/img/ig" +
            "oogle-pill.gif\" alt=\"Add News to your iGoogle Homepage\"></a>.<a " +
            "href=\"http://www.google.com/ig/add?feedurl=http://news.google.co" +
            "m/news?num%3D10%26output%3Datom%26hl%3Den&amp;hl=en\">Add News to" +
            " your Google Homepage</a></div></div></div></div>.<div class=\"g-" +
            "unit\"><div class=\"g-section\"><img id=\"hn-logo\" src=\"/hostednews/" +
            "img/afp_logo.gif?hl=en\" alt=\"AFP\"></div>.<div id=\"ss-section\" cl" +
            "ass=\"g-section\"><noscript><table id=\"ss\"><tr><td><div id=\"ss-ima" +
            "ge-container\"><img id=\"ss-image\" alt=\"\" src=\"http://www.google.c" +
            "om/hostednews/afp/media/ALeqM5hZuH4qd5oCaDZjTj9XZ1SlPR6I0A?docId" +
            "=photo_1297953248163-1-0&amp;size=s2\"></div></td></tr>.<tr><td i" +
            "d=\"ss-caption\"><p>A general view shows Pearl Square in Manama</p" +
            "></td></tr></table></noscript></div>.<div id=\"rm-section\" style=" +
            "\"display:none\"><div id=\"rm-message\">Map</div>.<div id=\"rm-map-co" +
            "ntainer\" class=\"g-section\"></div></div></div></div>.<script type" +
            "=\"text/javascript\">.          .            ..\r\n" +
            "87f\r\n" +
            "           getElement('ss-section').innerHTML = '';.            " +
            "var images = [];.            .              images.push(new Slid" +
            "eshowImage(.                \"http://www.google.com/hostednews/af" +
            "p/media/ALeqM5hZuH4qd5oCaDZjTj9XZ1SlPR6I0A?docId\\x3dphoto_129795" +
            "3248163-1-0\",.                \"A general view shows Pearl Square" +
            " in Manama\".                ));.            .            var sho" +
            "w = new Slideshow(getElement('ss-section'),.              [getEl" +
            "ement('hn-content'), getElement('hn-footer')], images,.         " +
            "     \"/hostednews/afp/slideshow/ALeqM5gjbkV9uYKA_QJtwvfbx8guFl04" +
            "Hg\",.              false,.              0,.              \"\",.   " +
            "           \"CNG.68e525354daffd868eac000986513f10.151\".          " +
            "    );.          .        </script></div>.<div class=\"g-doc\" id=" +
            "\"hn-footer\"><div class=\"g-unit g-first\"><span id=\"hn-footer-copy" +
            "right\">&copy;2011  Google</span>.<span class=\"links\">-.<a href=\"" +
            "http://news.google.com/intl/en/about_google_news.html\">About Goo" +
            "gle News</a>.-.<a href=\"http://googlenewsblog.blogspot.com/\">Blo" +
            "g</a>.-.<a href=\"http://www.google.com/support/news/?hl=en\">Help" +
            " Center</a>.-.<a href=\"http://www.google.com/support/news_pub/?h" +
            "l=en\">Help for Publishers</a>.-.<a href=\"http://news.google.com/" +
            "intl/en/terms_google_news.html\">Terms of Use</a>.-.<a href=\"http" +
            "://www.google.com/i" +
            "ntl/en/privacy.html\">Privacy Policy</a>.-.<a href=\"http://www.go" +
            "ogle.com/webhp?hl=en\">Google Home</a></span></div></div>.<script" +
            " type=\"text/javascript\" src=\"http://www.google.com/jsapi?key=ABQ" +
            "IAAAA4nur-ime_GQysVNAB3EOPBSsTL4WIgxhMZ0ZK_kHjwHeQuOD4xTtIvaBhsv" +
            "7I_yMlYRReNzvEBSUcQ&amp;client=google-hostednews\">.        </scr" +
            "ipt>.<script type=\"text/javascript\">.          function mapsLoad" +
            "edCallback() {.            HNS_initializeMap(getElement('rm-sect" +
            "ion'),.                getElement('rm-map-container'),.         " +
            "       \"WASHINGTON\", 5,.                \"en\", 186, 186,.        " +
            "        \"ABQIAAAA4nur-ime_GQysVNAB3EOPBSsTL4WIgxhMZ0ZK_kHjwHeQuO" +
            "D4xTtIvaBhsv7I_yMlYRReNzvEBSUcQ\");.          }..          google" +
            ".load(\"maps\", \"2\", {\"locale\": \"en\",.              \"callback\": ma" +
            "psLoadedCallback,.              \"other_params\":\"client=google-ho" +
            "stednews\"});.        </script></body></html>\r\n" +
            "0\r\n" +
            "\r\n";

        final ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes(Charset.forName("US-ASCII")));
        final MockAsyncSocketChannel channel = new MockAsyncSocketChannel(bais);

        final ResponseParser parser = new ResponseParser(channel, 100, TimeUnit.SECONDS);

        final CountDownLatch lock = new CountDownLatch(4);

        final AtomicInteger size = new AtomicInteger(0);

        parser.beforeReadNext(new CompletionHandler<Void, Void>() {
            @Override
            public void completed(final Void result, final Void attachment) {

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
                    }
                });

                parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        assertEquals(11, result.size());
                        lock.countDown();
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                    }
                });

                parser.onData(new CompletionHandler<ByteBuffer, Void>() {
                    @Override
                    public void completed(final ByteBuffer result, final Void attachment) {
                        size.addAndGet(result.remaining());
                        if(result.remaining() == 0) {
                            assertEquals(10367, size.get());
                            lock.countDown();
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                    }
                });

                parser.onTrailers(new CompletionHandler<Map<String, List<String>>, Void>() {
                    @Override
                    public void completed(final Map<String, List<String>> result, final Void attachment) {
                        assertEquals(0, result.size());
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
