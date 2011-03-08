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

package org.tini.server;

import org.tini.common.WritableStream;
import org.tini.parser.HttpCodecUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.TimeZone;
import java.util.UUID;

/**
 * HTTP 1.1 response
 *
 * @author Subbu Allamaraju
 */
public class ServerResponse extends WritableStream {

    private int status = 200;
    private String message = "OK";

    private ServerRequest request;

    private final String chunkId;

    public final static String HTTP_DATE_PATTERN = "EEE, dd MMM yyyyy HH:mm:ss z";
    static final DateFormat httpDateFormat = new SimpleDateFormat(HTTP_DATE_PATTERN, Locale.US);

    static {
        httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    protected ServerResponse(final RequestPipeline.ChannelWriter sink) {
        super(sink);

        chunkId = UUID.randomUUID().toString();

        this.headers.put("Server", "tini/1.0");
        this.headers.put("Date", httpDateFormat.format(new Date()));
    }

    protected ServerResponse(final RequestPipeline.ChannelWriter sink,
                             final ServerRequest request) {
        this(sink);
        this.request = request;
    }

    /**
     * <p>Set the status code and message</p>
     *
     * @param status  status code
     * @param message status message
     */
    public void setStatus(final int status, final String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * <p>Write some text. The text will be encoded using UTF-8.</p>
     *
     * @param body body
     */
    public void write(final String body) {
        write(body.getBytes(Charset.forName("UTF-8")));
    }

    protected void writeFirstLine(final ByteArrayOutputStream baos) throws IOException {
        baos.write("HTTP/1.1".getBytes(US_ASCII));
        baos.write(HttpCodecUtil.SP);
        baos.write(String.valueOf(this.status).getBytes(US_ASCII));
        baos.write(HttpCodecUtil.SP);
        baos.write(this.message.getBytes(US_ASCII));
    }

    protected boolean doClose() {
        boolean close = false;
        if(request != null) {
            final String val = request.getHeader("connection");
            close = "close".equalsIgnoreCase(val);
        }
        if(!close && !headers.containsKey("connection")) {
            headers.put("connection", "keep-alive");
        }
        else if(close) {
            headers.put("connection", "close");
        }
        return close;
    }
}
