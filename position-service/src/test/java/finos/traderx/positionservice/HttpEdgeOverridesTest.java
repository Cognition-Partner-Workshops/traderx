package finos.traderx.positionservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:httpEdgeOverrides;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SPRINGDOC_ENABLED=true",
        "SERVER_MAX_HTTP_REQUEST_HEADER_SIZE=128KB"
})
@AutoConfigureMockMvc
class HttpEdgeOverridesTest extends HttpEdgeTestSupport {
    @Autowired
    MockMvc mvc;

    @Test
    void documentationCanBeEnabled() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void headerLimitCanBeIncreased() throws Exception {
        assertThat(statusWithHeader(100 * 1024)).isEqualTo(302);
    }

    @Test
    void overrideStillBoundsHeaders() throws Exception {
        assertThat(statusWithHeader(160 * 1024)).isIn(400, 431);
    }
}
