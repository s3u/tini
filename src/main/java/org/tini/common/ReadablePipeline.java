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

package org.tini.common;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * @author Subbu Allamaraju
 */
public class ReadablePipeline {

    protected static final Logger logger = Logger.getLogger("org.tini.common");

    // Channel
    protected final AsynchronousSocketChannel channel;

    // Pending reads
    private final BlockingQueue<ReadableMessage> readablesQueue;

    public ReadablePipeline(final AsynchronousSocketChannel channel) {
        this.channel = channel;
        readablesQueue = new LinkedBlockingQueue<ReadableMessage>();
    }

    /**
     * <p>Push a readable message (i.e, client response or server request) into a queue.</p>
     *
     * @param message message
     * @throws InterruptedException
     */
//    @Override
    public void push(final ReadableMessage message) throws InterruptedException {
        readablesQueue.put(message);
    }

    public ReadableMessage take() throws InterruptedException {
        if(readablesQueue.peek() != null) {
            return readablesQueue.take();
        }
        else {
            return null;
        }
    }


    // TODO?
//    public void onNewRequest(final CompletionHandler<RequestLine, String> handler) {
//        parser.onRequestLine(new CompletionHandler<RequestLine, Void>() {
//            @Override
//            public void completed(final RequestLine result, final Void attachment) {
//
//            }
//
//            @Override
//            public void failed(final Throwable exc, final Void attachment) {
//                //To change body of implemented methods use File | Settings | File Templates.
//            }
//        });
//    }

    // TODO?
    public void handle(String id, ReadableMessage request, WritableMessage response) {

    }

}
