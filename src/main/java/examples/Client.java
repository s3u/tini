package examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class Client {
    public static void main(String[] args) throws Exception {

//        System.setProperty("http.proxyHost", "localhost");
//        System.setProperty("http.proxyPort", "8888");

        String content = "dash dja djashdajsdh jdashd ajskdhaskd askdh askdhas dkasdh as" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da" +
            "dahsdk asjdhja djas djasdhas dashd asjdh kasdjashd asdas kdjas da";
//        content = "hello world";

//        for(int i = 0; i < 5; i++) {
//            content = content + content;
//        }

//        final AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
//        final AsyncHttpClient.BoundRequestBuilder requestBuilder = asyncHttpClient.preparePost("http://localhost:3000/");
//        requestBuilder.setBody(content);
//        // Netty chokes on this - TODO investigate
////        requestBuilder.setHeader("Transfer-Encoding", "chunked");
//        requestBuilder.execute(new AsyncCompletionHandler<Response>() {
//            @Override
//            public Response onCompleted(Response response) throws Exception {
//                // Do something with the Response
//                // ...
//                asyncHttpClient.close();
//                System.exit(0);
//                return response;
//            }
//
//            @Override
//            public void onThrowable(Throwable t) {
//                // Something wrong happened.
//                t.printStackTrace();
//            }
//        });

        // Try basic client
        URL url = new URL("http://192.168.11.2:3000/");
//        URL url = new URL("http://localhost:3000/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Transfer-Encoding", "chunked");
        connection.setChunkedStreamingMode(128);
        connection.setDoOutput(true);
        connection.connect();

        OutputStreamWriter out = new OutputStreamWriter(
            connection.getOutputStream());
        out.write(content);
        out.close();

        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String decodedString;

        while((decodedString = in.readLine()) != null) {
            System.out.println(decodedString);
        }
        in.close();
        connection.disconnect();
    }
}
