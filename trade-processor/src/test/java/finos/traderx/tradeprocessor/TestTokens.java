package finos.traderx.tradeprocessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Mints HS256 tokens signed with the test secret, mirroring what people-service issues. */
final class TestTokens {

	static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes";

	private TestTokens() {
	}

	static String bearer(String subject, String... roles) throws Exception {
		return "Bearer " + token(subject, SECRET, roles);
	}

	static String token(String subject, String secret, String... roles) throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.subject(subject)
			.issuer("traderx")
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plusSeconds(300)))
			.claim("roles", List.of(roles))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
		return jwt.serialize();
	}
}
