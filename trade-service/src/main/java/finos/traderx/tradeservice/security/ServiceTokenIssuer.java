package finos.traderx.tradeservice.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Mints short-lived SERVICE tokens (same HS256 shared secret as the resource
 * servers) used by this service when it talks to the trade-feed bus or other
 * TraderX services on its own behalf rather than on behalf of a user.
 */
@Component
public class ServiceTokenIssuer {

	public static final String ISSUER = "traderx";
	public static final String SERVICE_ROLE = "SERVICE";

	@Value("${traderx.auth.secret}")
	private String authSecret;

	@Value("${spring.application.name:trade-service}")
	private String serviceName;

	@Value("${traderx.auth.service-token-ttl-seconds:3600}")
	private long ttlSeconds;

	public String issueToken() {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.subject(serviceName)
			.issuer(ISSUER)
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
			.claim(SecurityConfig.ROLES_CLAIM, List.of(SERVICE_ROLE))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		try {
			jwt.sign(new MACSigner(authSecret.getBytes(StandardCharsets.UTF_8)));
		} catch (JOSEException e) {
			throw new IllegalStateException("Unable to sign service token", e);
		}
		return jwt.serialize();
	}
}
