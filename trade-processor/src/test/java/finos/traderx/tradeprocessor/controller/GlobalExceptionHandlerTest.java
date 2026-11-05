package finos.traderx.tradeprocessor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class GlobalExceptionHandlerTest {
    @RestController
    static class FailingController {
        @GetMapping("/failure")
        String fail() { throw new IllegalStateException("SQL internal-host\r\nFORGED"); }

        @PostMapping("/number")
        int number(@RequestBody Integer number) { return number; }
    }

    @Test
    void D1_uncaughtFailuresAreCorrelatedAndDoNotDiscloseDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        var result = mvc.perform(get("/failure")).andExpect(status().isInternalServerError()).andReturn();
        var response = result.getResponse();
        assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
        assertThat(response.getContentAsString())
            .isEqualTo("Internal server error. Reference: " + response.getHeader("X-Correlation-ID"))
            .doesNotContain("SQL", "internal-host", "FORGED");
        mvc.perform(post("/number").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
    }
}
