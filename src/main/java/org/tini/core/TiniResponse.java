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
package org.tini.core;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * HTTP response
 */
public class TiniResponse {

    final private org.jboss.netty.handler.codec.http.HttpResponse nettyResponse;
    final private Channel channel;
    final private AtomicBoolean first = new AtomicBoolean(true);
    final private AtomicBoolean closed = new AtomicBoolean(false);

    public TiniResponse(MessageEvent e) {
        channel = e.getChannel();

        // Initialize with default version and status code
        nettyResponse = new DefaultHttpResponse(HTTP_1_1, OK);
    }

    public void setVersion(HttpVersion version) {
        nettyResponse.setProtocolVersion(version);

    }

    public void setStatus(int status) {
        nettyResponse.setStatus(HttpResponseStatus.valueOf(status));
    }

    public void setStatus(HttpResponseStatus status) {
        nettyResponse.setStatus(status);
    }

    public void setContentType(String type) {
        nettyResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, type);
    }

    public void setContentLength(long length) {
        nettyResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, Long.toString(length));
        nettyResponse.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
    }

    public void addHeader(String name, String value) {
        nettyResponse.addHeader(name, value);
    }

    public void addHeaders(String name, List<String> values) {
        for(String value: values) {
            nettyResponse.addHeader(name, value);
        }
    }

    public void setHeader(String name, String value) {
        nettyResponse.setHeader(name, value);
    }

    public void write(String text) {
        write(text.getBytes(CharsetUtil.UTF_8));
    }

    public void write(byte[] bytes) {
        write(ChannelBuffers.wrappedBuffer(bytes));
    }

    public void write(ChannelBuffer channelBuffer) {
        ensureHead();
        HttpChunk chunk = new DefaultHttpChunk(channelBuffer);

        ChannelFuture future = channel.write(chunk);
        future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        future.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if(!future.isSuccess()) {
                    System.err.println("Got " + future.getCause());
                    future.getChannel().close();
                }
            }
        });
    }

    public synchronized void close() {
        if(!closed.get()) {
            ensureHead();
            HttpChunk last = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
            ChannelFuture f = channel.write(last);
            f.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    channel.close();
                    closed.compareAndSet(false, true);
                }
            });
        }
    }

    private void ensureHead() {
        if(first.get()) {
            if(nettyResponse.getHeader(HttpHeaders.Names.CONNECTION) == null) {
                nettyResponse.addHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
            }
            if(nettyResponse.getHeader(HttpHeaders.Names.TRANSFER_ENCODING) == null) {
                nettyResponse.addHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
                nettyResponse.setChunked(true);
            }


            // Write the initial part - no data yet
            channel.write(nettyResponse);
            first.compareAndSet(true, false);
        }
    }
}
