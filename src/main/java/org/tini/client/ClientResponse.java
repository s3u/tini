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
import org.tini.parser.ResponseLine;
import org.tini.parser.ResponseParser;

/**
 * @author Subbu Allamaraju
 */
public class ClientResponse extends ReadableMessage {

    private ResponseLine responseLine;

    /**
     * Creates an HTTP request. Use {@link ClientConnection} to create a new request.
     *
     * @param parser       parser
     */
    ClientResponse(final ResponseParser parser) {
        super(parser);
    }

    void setResponseLine(final ResponseLine responseLine) {
        this.responseLine = responseLine;
    }

    /**
     * Returns response line
     *
     * @return response line
     */
    public ResponseLine getResponseLine() {
        return responseLine;
    }
}
