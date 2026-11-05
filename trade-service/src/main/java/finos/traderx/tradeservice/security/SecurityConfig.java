package finos.traderx.tradeservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    private static final String[] PUBLIC_ROUTES = {
            "/", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            "/actuator/health", "/actuator/health/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${trade.security.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${trade.security.jwt.issuer:}") String issuer,
            @Value("${trade.security.jwt.audience:}") String audience) {
        if (jwkSetUri.isBlank()) {
            throw new IllegalStateException("trade.security.jwt.jwk-set-uri (TRADE_SERVICE_JWT_JWK_SET_URI) must be configured");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtValidator(issuer, audience));
        return decoder;
    }

    OAuth2TokenValidator<Jwt> jwtValidator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> validator = issuer.isBlank()
                ? JwtValidators.createDefault() : JwtValidators.createDefaultWithIssuer(issuer);
        if (!audience.isBlank()) {
            validator = new DelegatingOAuth2TokenValidator<>(validator,
                    new JwtClaimValidator<java.util.List<String>>("aud",
                            audiences -> audiences != null && audiences.contains(audience)));
        }
        return validator;
    }
}
