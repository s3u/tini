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

package org.tini.client;

import org.tini.common.ReadableMessage;
import org.tini.common.ReadablePipeline;
import org.tini.parser.ResponseLine;
import org.tini.parser.ResponseParser;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.Map;

/**
 * @author Subbu Allamaraju
 */
public class ClientResponsePipeline extends ReadablePipeline {

    public ClientResponsePipeline(final AsynchronousSocketChannel channel) {
        super(channel);
    }

    /**
     * Binds this pipeline to the parser to receive events.
     *
     * @param parser parser
     */
    void bind(final ResponseParser parser) {
        parser.onResponseLine(new CompletionHandler<ResponseLine, Void>() {
            @Override
            public void completed(final ResponseLine result, final Void attachment) {
                try {
                    // Ask the pipeline for the next request - since the request and response
                    // pipelines may be cleared at different points, ask the response pipeline and
                    // then pick its request.
                    final ClientResponse clientResponse = (ClientResponse) peek();

                    // Set the response line
                    clientResponse.setResponseLine(result);
                    final ClientRequest clientRequest = clientResponse.getRequest();

                    // Call request.onResponse.completed()
                    clientRequest.response(clientResponse);
                }
                catch(InterruptedException ie) {
                    // TODO
                    // onResponse.failed(ie, null);
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
//                onResponse.failed(exc, attachment);
                // TODO
                exc.printStackTrace();
            }
        });

        parser.onHeaders(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = peek();
                    if(readableMessage != null) {
                        readableMessage.headers(result);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = take();
                    if(readableMessage != null) {
                        readableMessage.failure(exc);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        });
        parser.onData(new CompletionHandler<ByteBuffer, Void>() {
            @Override
            public void completed(final ByteBuffer result, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = peek();
                    if(readableMessage != null) {
                        readableMessage.data(result);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = take();
                    if(readableMessage != null) {
                        readableMessage.failure(exc);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        });
        parser.onTrailers(new CompletionHandler<Map<String, List<String>>, Void>() {
            @Override
            public void completed(final Map<String, List<String>> result, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = take();
                    if(readableMessage != null) {
                        readableMessage.trailers(result);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                try {
                    final ReadableMessage readableMessage = take();
                    if(readableMessage != null) {
                        readableMessage.failure(exc);
                    }
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        });

        parser.go();
    }

}
