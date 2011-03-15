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

package org.tini.parser;

/**
 * @author Subbu Allamaraju
 */
public class ResponseLine
{
    private final int code;
    private final String status;
    private final String version;

    /**
     * Creates a response line
     *
     * @param version HTTP version
     * @param code response code
     * @param status response status
     */
    ResponseLine(final String version, final int code, final String status) {
        this.code = code;
        this.status = status;
        this.version = version;
    }

    /**
     * Returns response code
     *
     * @return code
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns response status message
     *
     * @return message
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns protocol version
     *
     * @return version
     */
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return code + " " + status + " " + version;
    }
}
