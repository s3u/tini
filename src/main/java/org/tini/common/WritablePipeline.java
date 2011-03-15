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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Subbu Allamaraju
 */
public abstract class WritablePipeline extends MessagePipeline<WritableMessage> {

    private static final Logger logger = Logger.getLogger("org.tini.common");

    // Channel to write to
    private final AsynchronousSocketChannel channel;

    // Pending writes.
    private final List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();

    // Close if explicitly asked for
    private boolean closeWhenDone = false;
    private boolean ended = false;

    /**
     * Creates a pipeline
     *
     * @param channel channel
     */
    protected WritablePipeline(final AsynchronousSocketChannel channel) {
        super();
        this.channel = channel;
    }

    /**
     * Writes remaining() number of bytes from the start of the byte buffer. If the given is message
     * is not the current, the data will be buffered.
     *
     * @param message current message
     * @param byteBuffer source
     * @param handler completion handler
     */
    public void write(final WritableMessage message, final ByteBuffer byteBuffer, final CompletionHandler<Integer, Void> handler) {
        byteBuffer.rewind();
        if(message == peek()) {
            beginWriting();
            channel.write(byteBuffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(final Integer result, final Void attachment) {
                    endWriting();
                    if(handler != null) {
                        try {
                            handler.completed(result, attachment);
                        }
                        catch(Throwable t) {
                            logger.log(Level.WARNING, t.getMessage(), t);

                        }
                    }
                }

                @Override
                public void failed(final Throwable exc, final Void attachment) {
                    endWriting();
                    logger.log(Level.WARNING, exc.getMessage(), exc);
                    if(handler != null) {
                        try {
                            handler.failed(exc, attachment);
                        }
                        catch(Throwable t) {
                            logger.log(Level.WARNING, t.getMessage(), t);
                        }
                    }
                }
            });
        }
        else {
            // Need to buffer response
            buffers.add(byteBuffer);
            if(handler != null) {
                handler.completed(0, null); // Nothing is written yet
            }
        }
    }

    /**
     * Closes the connection after writing is completed.
     */
    public void closeWhenDone() {
        closeWhenDone = true;
    }

    /**
     * Flush any pending buffers and end the message.
     *
     * @param message
     */
    public void end(final WritableMessage message) {
        this.ended = true;
        flush(message);
    }

    /**
     * Return true if there are any buffers opened - happens in the case of head-of-line blocking.
     * @return boolean
     */
    private boolean isBuffering() {
        return buffers.size() > 0;
    }

    /**
     * Flush buffers to the channel, if the message is the current.
     *
     * @param message current message
     */
    private void flush(final WritableMessage message) {
        if(message == peek()) {
            for(final ByteBuffer byteBuffer : buffers) {
                byteBuffer.rewind();
                beginWriting();
                channel.write(byteBuffer, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(final Integer result, final Void attachment) {
                        endWriting();
                    }

                    @Override
                    public void failed(final Throwable exc, final Void attachment) {
                        endWriting();
                    }
                });
            }

            try {
                // Remove from top
                poll();
                flushCompleted();
            }
            catch(InterruptedException ie) {
                logger.log(Level.WARNING, ie.getMessage(), ie);
            }
        }
    }

    private void flushCompleted() throws InterruptedException {
        WritableMessage top = peek();
        // If the top is not buffering, it will go away once it is done. We don't need to worry
        // about it here.
        while(top != null && isBuffering() && ended) {
            logger.info("buffering top found " + top.hashCode() + " " + ended);
            // Flush this
            flush(top);
            top = poll();
        }
        if(peek() == null && closeWhenDone) {
            try {
                logger.info("Closing the connection");
                channel.close();
            }
            catch(IOException ioe) {
                logger.log(Level.WARNING, ioe.getMessage(), ioe);
            }
        }
    }

    /**
     * Pre-filter
     */
    abstract protected void beginWriting();

    /**
     * Post-filter
     */
    abstract protected void endWriting();
}
