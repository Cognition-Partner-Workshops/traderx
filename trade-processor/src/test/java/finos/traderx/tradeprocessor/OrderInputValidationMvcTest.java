package finos.traderx.tradeprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import finos.traderx.tradeprocessor.controller.TradeServiceController;
import finos.traderx.tradeprocessor.service.TradeService;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = TradeServiceController.class, properties = {
    "reference.data.service.url=http://reference.test", "account.service.url=http://account.test"})
class OrderInputValidationMvcTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean TradeService downstream;

    @ParameterizedTest
    @CsvSource(value = {
        "quantity|0", "quantity|-5", "quantity|null", "side|null", "side|Invalid",
        "accountId|null", "accountId|0", "accountId|-1", "security|null",
        "security|../health", "security|IBM?x=1", "security|A/B", "security|A#B",
        "security|A%B", "security|A..B", "security|A B", "security|''",
        "security|ABCDEFGHIJKLM"
    }, delimiter = '|')
    void rejectsInvalidInput(String field, String value) throws Exception {
        ObjectNode order = (ObjectNode) mapper.readTree(
            "{\"quantity\":10,\"side\":\"Buy\",\"accountId\":22,\"security\":\"IBM\"}");
        if (value == null || value.equals("null")) order.putNull(field);
        else if (field.equals("quantity") || field.equals("accountId")) order.put(field, Integer.parseInt(value));
        else order.put(field, value);
        reject(order.toString());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        reject("{");
    }

    @Test
    void rejectsEmptyBody() throws Exception {
        reject("");
    }

    private void reject(String body) throws Exception {
        mvc.perform(post("/tradeservice/order").contentType("application/json").content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Invalid trade order"));
        verifyNoInteractions(downstream);
    }
}
