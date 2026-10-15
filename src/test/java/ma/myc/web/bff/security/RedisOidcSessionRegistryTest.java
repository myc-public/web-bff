package ma.myc.web.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.SessionRepository;

/** SB5 : enregistrement par sid, suppression ciblee au back-channel, rejeu jti sans effet. */
class RedisOidcSessionRegistryTest {

	private static final String P = "web-bff-test:oidc:";

	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> values = mock(ValueOperations.class);
	@SuppressWarnings("unchecked")
	private final SetOperations<String, String> sets = mock(SetOperations.class);
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final SessionRepository<?> sessions = mock(SessionRepository.class);
	private RedisOidcSessionRegistry registry;

	@BeforeEach
	void setUp() {
		given(redis.opsForValue()).willReturn(values);
		given(redis.opsForSet()).willReturn(sets);
		registry = new RedisOidcSessionRegistry(redis, sessions, "web-bff-test:", Duration.ofHours(10),
				Duration.ofMinutes(5));
	}

	private static OidcLogoutToken logoutToken(String jti, String sid) {
		return OidcLogoutToken.withTokenValue("jwt")
			.jti(jti)
			.sessionId(sid)
			.issuer("https://kc")
			.audience(java.util.List.of("web-bff"))
			.issuedAt(Instant.now())
			.build();
	}

	@Test
	void connexion_enregistre_la_session_sous_son_sid() {
		OidcIdToken idToken = OidcIdToken.withTokenValue("id").subject("u").claim("sid", "sid-1").build();
		registry.saveSessionInformation(new OidcSessionInformation("session-1", java.util.Map.of(),
				new DefaultOidcUser(java.util.List.of(), idToken)));

		verify(sets).add(P + "sid:sid-1", "session-1");
		verify(values).set(P + "session:session-1", "sid-1", Duration.ofHours(10));
	}

	@Test
	void back_channel_supprime_seulement_les_sessions_du_sid() {
		given(values.setIfAbsent(eq(P + "jti:jti-1"), eq("1"), any(Duration.class))).willReturn(true);
		given(sets.members(P + "sid:sid-1")).willReturn(Set.of("session-1", "session-2"));

		assertThat(registry.removeSessionInformation(logoutToken("jti-1", "sid-1"))).isEmpty();

		verify(sessions).deleteById("session-1");
		verify(sessions).deleteById("session-2");
		verify(redis).delete(P + "sid:sid-1");
	}

	@Test
	void rejeu_du_meme_logout_token_sans_effet() {
		given(values.setIfAbsent(eq(P + "jti:jti-1"), eq("1"), any(Duration.class))).willReturn(false);

		assertThat(registry.removeSessionInformation(logoutToken("jti-1", "sid-1"))).isEmpty();

		verify(sets, never()).members(anyString());
		verify(sessions, never()).deleteById(anyString());
	}
}
