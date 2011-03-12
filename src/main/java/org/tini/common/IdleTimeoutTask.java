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
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Watches for idle channels, and closes them if they are idle.
 *
 * @author Subbu Allamaraju
 */
// TODO: Revise design - should handle both server and client
final class IdleConnectionWatcher
{
    private static final Logger logger = Logger.getLogger("org.tini.common");

    private final AtomicInteger readers = new AtomicInteger(0);
    private final AtomicInteger writers = new AtomicInteger(0);

    private final AsynchronousSocketChannel channel;

    private volatile long lastTime;
    private final long idleTimeoutMillis;
    private final Timer timer;

    /**
     * Create
     *
     * @param channel channel
     * @param idleTimeoutMills timeout
     */
    IdleConnectionWatcher(final AsynchronousSocketChannel channel,
                          final long idleTimeoutMills) {
        this.channel = channel;
        lastTime = System.currentTimeMillis();
        this.idleTimeoutMillis = idleTimeoutMills;
        timer = new Timer("aio.idle", true);
        timer.schedule(new IdleTimeoutTask(), idleTimeoutMillis);
    }

    /**
     * Call this before writing
     */
    protected void writing() {
        writers.incrementAndGet();
        lastTime = System.currentTimeMillis();
    }

    /**
     * Call this after writing
     */
    protected void doneWriting() {
        writers.decrementAndGet();
        lastTime = System.currentTimeMillis();
    }

    private class IdleTimeoutTask extends TimerTask implements Cloneable {
        @Override
        public void run() {
            final long currentTime = System.currentTimeMillis();
            if(readers.get() == 0 && writers.get() == 0 && currentTime - lastTime > idleTimeoutMillis) {
                // Close now
                try {
                    logger.info("Closing an idle channel");
                    channel.close();
                }
                catch(IOException ioe) {
                    // TODO:
                    ioe.printStackTrace();
                }
            }
            else {
                timer.schedule(new IdleTimeoutTask(), idleTimeoutMillis);
            }
        }
    }
}
