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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Subbu Allamaraju
 */
// TODO: Idle connection closing
public class WritablePipeline implements Sink {

    private static final Logger logger = Logger.getLogger("org.tini.common");

    // Close if explicitly asked for
    protected boolean closeWhenDone = false;


    // Pending responses for pipelined requests.
    protected final BlockingQueue<WritableMessage> pipeline;


    private final AsynchronousSocketChannel channel;

    public WritablePipeline(final AsynchronousSocketChannel channel) {
        pipeline = new LinkedBlockingQueue<WritableMessage>();
        this.channel = channel;
    }

    @Override
    public void push(final WritableMessage source) throws InterruptedException {
        pipeline.put(source);
    }

    private boolean ended = false;

    boolean isBuffering() {
        return buffers.size() > 0;
    }

    List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();

    @Override
    public void write(final WritableMessage message, final ByteBuffer byteBuffer, final CompletionHandler<Integer, Void> handler) {
        byteBuffer.rewind();
        if(message == pipeline.peek()) {
            if(handler == null) {
                final Future f = channel.write(byteBuffer);
                try {
                    f.get();
                }
                catch(ExecutionException ee) {
                    ee.printStackTrace();
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
            else {
                channel.write(byteBuffer, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(final Integer result, final Void attachment) {
                        try {
                            handler.completed(result, attachment);
                        }
                        catch(Throwable t) {
                            logger.log(Level.WARNING, t.getMessage(), t);

                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        logger.log(Level.WARNING, exc.getMessage(), exc);
                        try {
                            handler.failed(exc, attachment);
                        }
                        catch(Throwable t) {
                            logger.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                });
            }
        }
        else {
            // Need to buffer response
            buffers.add(byteBuffer);
            if(handler != null) {
                handler.completed(0, null); // Nothing is written yet
            }
        }
    }

    @Override
    public void closeWhenDone() {
        closeWhenDone = true;
    }

    @Override
    public void end(final WritableMessage message) {
        this.ended = true;
        flush(message);
    }

    void flush(final WritableMessage message) {
        if(message == pipeline.peek()) {
            for(final ByteBuffer byteBuffer : buffers) {
                byteBuffer.rewind();
                channel.write(byteBuffer);
            }

            try {
                // Remove from top
                pipeline.take();
                flushCompleted();
            }
            catch(InterruptedException ie) {
                logger.log(Level.WARNING, ie.getMessage(), ie);
            }
        }
    }


    void flushCompleted() throws InterruptedException {
        WritableMessage top = pipeline.peek();
        // If the top is not buffering, it will go away once it is done. We don't need to worry
        // about it here.
        while(top != null && isBuffering() && ended) {
            logger.info("buffering top found " + top.hashCode() + " " + ended);
            // Flush this
            flush(top);
            try {
                top = pipeline.take();
            }
            catch(InterruptedException ie) {
                logger.log(Level.WARNING, ie.getMessage(), ie);
            }
        }
        if(pipeline.peek() == null && closeWhenDone) {
            try {
                logger.info("Closing the connection");
                channel.close();
            }
            catch(IOException ioe) {
                logger.log(Level.WARNING, ioe.getMessage(), ioe);
            }
        }
    }


}
