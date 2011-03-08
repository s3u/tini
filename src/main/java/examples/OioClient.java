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

package examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

// Used for some testing
public class OioClient {
    public static void main(final String[] args) throws Exception {

        final String content = "dash dja djashdajsdh jdashd ajskdhaskd askdh askdhas dkasdh as" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da";

        final URL url = new URL("http://localhost:3000/");
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Transfer-Encoding", "chunked");
        connection.setChunkedStreamingMode(128);
        connection.setDoOutput(true);
        connection.connect();

        final OutputStreamWriter out = new OutputStreamWriter(
            connection.getOutputStream());
        out.write(content);
        out.close();

        final BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String decodedString;

        while((decodedString = in.readLine()) != null) {
            System.out.println(decodedString);
        }
        in.close();
        connection.disconnect();
    }
}
