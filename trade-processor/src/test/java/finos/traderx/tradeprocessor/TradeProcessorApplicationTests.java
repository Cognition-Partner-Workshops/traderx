package finos.traderx.tradeprocessor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:upgrade-test;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TradeProcessorApplicationTests {
    @Value("${local.server.port}")
    int port;

    private HttpResponse<String> request(String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body != null) {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void openApiDocumentsOrderEndpoint() throws Exception {
        HttpResponse<String> response = request("/v3/api-docs", null);
        assertEquals(200, response.statusCode());
        assertTrue(new JSONObject(response.body()).getJSONObject("paths").has("/tradeservice/order"));
    }

    @Test
    void orderSettlesAndUpdatesPosition() throws Exception {
        HttpResponse<String> response = request("/tradeservice/order",
            "{\"id\":\"test-order\",\"accountId\":4410,\"security\":\"TEST\",\"side\":\"Buy\",\"quantity\":5}");
        assertEquals(200, response.statusCode());
        JSONObject result = new JSONObject(response.body());
        assertEquals("Settled", result.getJSONObject("trade").getString("state"));
        assertEquals(5, result.getJSONObject("position").getInt("quantity"));
        assertEquals(4410, result.getJSONObject("trade").getInt("accountId"));
        assertInstanceOf(String.class, result.getJSONObject("trade").get("created"));
    }

    @Test
    void emptyRequestBodyIsRejected() throws Exception {
        assertEquals(400, request("/tradeservice/order", "").statusCode());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        assertEquals(400, request("/tradeservice/order", "{invalid").statusCode());
    }
}
