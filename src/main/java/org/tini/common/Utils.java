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

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

/**
 * @author Subbu Allamaraju
 */
public class Utils {

    /**
     * Copies data as and when it arrives from source to dest
     *
     * @param source source
     * @param dest dest
     */
    public static void pump(final ReadableStream source, final WritableStream dest) {
        source.onData(new CompletionHandler<ByteBuffer, Void>() {
            @Override
            public void completed(final ByteBuffer result, final Void count) {
                dest.write(result);
            }

            @Override
            public void failed(final Throwable exc, final Void attachment) {
                exc.printStackTrace();
            }
        });
    }
}
