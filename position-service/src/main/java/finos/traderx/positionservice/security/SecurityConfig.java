package finos.traderx.positionservice.security;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every endpoint requires a valid TraderX bearer token (HS256 JWT signed with
 * the shared TRADERX_AUTH_SECRET) unless it is listed in PUBLIC_ENDPOINTS.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	public static final String ROLES_CLAIM = "roles";
	public static final String ROLE_PREFIX = "ROLE_";

	/** Explicitly public: API documentation only. Everything else needs a caller identity. */
	static final String[] PUBLIC_ENDPOINTS = { "/", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**" };

	@Value("${traderx.auth.secret}")
	private String authSecret;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		if (authSecret == null || authSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("TRADERX_AUTH_SECRET must be set and at least 32 bytes long");
		}
		SecretKey key = new SecretKeySpec(authSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::rolesToAuthorities);
		return converter;
	}

	static Collection<GrantedAuthority> rolesToAuthorities(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
		if (roles == null) {
			return List.of();
		}
		return roles.stream()
			.map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
			.collect(Collectors.toList());
	}
}
