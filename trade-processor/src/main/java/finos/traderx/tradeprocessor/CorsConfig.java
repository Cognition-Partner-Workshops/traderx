package finos.traderx.tradeprocessor;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin access is limited to the comma-separated origins in CORS_ALLOWED_ORIGINS.
 * A "*" entry is ignored so the service can never be opened to every origin.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	public static final String DEFAULT_ALLOWED_ORIGINS = "http://localhost:8080,http://localhost:18093,http://localhost:18094";

	private final List<String> allowedOrigins;

	public CorsConfig(@Value("${CORS_ALLOWED_ORIGINS:" + DEFAULT_ALLOWED_ORIGINS + "}") String allowedOrigins) {
		this.allowedOrigins = parseOrigins(allowedOrigins);
	}

	static List<String> parseOrigins(String origins) {
		return Arrays.stream(origins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty() && !origin.equals("*"))
				.toList();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins(allowedOrigins.toArray(String[]::new))
				.allowedMethods("GET", "HEAD", "POST", "PUT", "OPTIONS");
	}
}
