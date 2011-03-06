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
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A HTTP request
 */
public class TiniRequest {

    private final org.jboss.netty.handler.codec.http.HttpRequest nettyRequest;

    protected TiniRequest(MessageEvent e) {
        this.nettyRequest = (org.jboss.netty.handler.codec.http.HttpRequest) e.getMessage();
    }

    public HttpMethod getMethod() {
        return nettyRequest.getMethod();
    }

    public HttpVersion getProtocolVersion() {
        return nettyRequest.getProtocolVersion();
    }

    public String getRequestUri() {
        return this.nettyRequest.getUri();
    }

    public String getHeader(String name) {
        return this.nettyRequest.getHeader(name);
    }

    public List<String> getHeaders(String name) {
        return this.nettyRequest.getHeaders(name);
    }

    public Set<String> getHeaderNames() {
        return this.nettyRequest.getHeaderNames();
    }

    public List<Map.Entry<String, String>> getHeaders() {
        return this.nettyRequest.getHeaders();
    }

    public ChannelBuffer getBody() {
        return nettyRequest.getContent();
    }
}

