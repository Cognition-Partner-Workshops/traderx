package finos.traderx.accountservice;

import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.test.web.server.LocalServerPort;

abstract class HttpEdgeTestSupport {
    @LocalServerPort
    int port;


    int statusWithHeader(int bytes) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10000);
            String request = "GET / HTTP/1.1\r\nHost: localhost\r\nX-Test: "
                    + "a".repeat(bytes) + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String status = new java.io.BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }
}
