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

import org.tini.common.WritablePipeline;

import java.nio.channels.AsynchronousSocketChannel;

/**
 * @author Subbu Allamaraju
 */
// TODO: Currently, a placeholder
public class ServerResponsePipeline extends WritablePipeline {
    public ServerResponsePipeline(final AsynchronousSocketChannel channel) {
        super(channel);
    }
}