package finos.traderx.tradeservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;

class OpenApiConfigTest {

    @Test
    void advertisesProxyFriendlyAndLocalServerUrlsForConfiguredPort() {
        OpenApiConfig config = new OpenApiConfig();
        ReflectionTestUtils.setField(config, "port", 4321);

        OpenAPI api = config.config();

        assertThat(api.getServers()).extracting(Server::getUrl).containsExactly("", "http://localhost:4321");
        assertThat(api.getInfo().getTitle()).isEqualTo("FINOS TraderX Trading Service");
    }
}
