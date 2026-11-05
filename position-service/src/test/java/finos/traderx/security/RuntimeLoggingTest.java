package finos.traderx.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Properties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class RuntimeLoggingTest {
    @Test
    void D2_sqlTracingIsDisabledUnlessExplicitlyEnabled() throws Exception {
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            properties.load(stream);
        }
        StandardEnvironment env = new StandardEnvironment();
        assertThat(env.resolvePlaceholders(properties.getProperty("spring.jpa.show-sql", "missing"))).isEqualTo("false");
        for (String key : new String[] {"logging.level.org.hibernate.SQL", "logging.level.org.hibernate.type.descriptor.sql.BasicBinder",
                "logging.level.org.hibernate.orm.jdbc.bind", "logging.level.org.hibernate.orm.jdbc.extract"}) {
            assertThat(env.resolvePlaceholders(properties.getProperty(key, "missing"))).isEqualTo("OFF");
        }
        env.getPropertySources().addFirst(new MapPropertySource("opt-in", Map.of(
            "JPA_SHOW_SQL", "true", "HIBERNATE_SQL_LOG_LEVEL", "DEBUG", "HIBERNATE_BIND_LOG_LEVEL", "TRACE")));
        assertThat(env.resolvePlaceholders(properties.getProperty("spring.jpa.show-sql", "missing"))).isEqualTo("true");
        assertThat(env.resolvePlaceholders(properties.getProperty("logging.level.org.hibernate.SQL", "missing"))).isEqualTo("DEBUG");
        assertThat(env.resolvePlaceholders(properties.getProperty("logging.level.org.hibernate.orm.jdbc.bind", "missing"))).isEqualTo("TRACE");
    }

}
