package ma.myc.web.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ma.myc.web.bff.config.BffProperties;

/** SB4 et points de vigilance : un seul refresh par session, attente et relecture, delai du verrou, erreur reseau. */
class LockingRefreshTokenProviderTest {

	private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
	private static final String SESSION_ID = "session-1";
	private static final String LOCK_KEY = "web-bff-test:lock:" + SESSION_ID;
	private static final BffProperties.Refresh SETTINGS = new BffProperties.Refresh(Duration.ofSeconds(30),
			Duration.ofSeconds(5), Duration.ofMillis(5), Duration.ofMillis(50));
	private static final ClientRegistration KEYCLOAK = ClientRegistration.withRegistrationId("keycloak")
		.clientId("web-bff")
		.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
		.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
		.authorizationUri("https://kc/auth")
		.tokenUri("https://kc/token")
		.build();

	private final FakeLock lock = new FakeLock();
	private final MapSessionRepository sessions = new MapSessionRepository(new ConcurrentHashMap<>());
	private final AtomicInteger delegateCalls = new AtomicInteger();
	private MockHttpSession httpSession;

	@BeforeEach
	void requestWithSession() {
		httpSession = new MockHttpSession(null, SESSION_ID);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(httpSession);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		sessions.save(new MapSession(SESSION_ID));
	}

	@AfterEach
	void reset() {
		RequestContextHolder.resetRequestAttributes();
	}

	private static OAuth2AuthorizedClient client(String value, Instant expiresAt) {
		return new OAuth2AuthorizedClient(KEYCLOAK, "user",
				new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, expiresAt.minusSeconds(300), expiresAt),
				new OAuth2RefreshToken("refresh-" + value, expiresAt.minusSeconds(300)));
	}

	private static OAuth2AuthorizationContext context(OAuth2AuthorizedClient client) {
		return OAuth2AuthorizationContext.withAuthorizedClient(client)
			.principal(new TestingAuthenticationToken("user", null))
			.build();
	}

	private LockingRefreshTokenProvider provider(OAuth2AuthorizedClientProvider delegate) {
		return new LockingRefreshTokenProvider(ctx -> {
			delegateCalls.incrementAndGet();
			return delegate.authorize(ctx);
		}, lock, sessions, SETTINGS, "web-bff-test:", Clock.fixed(NOW, java.time.ZoneOffset.UTC));
	}

	private void storeInRedis(OAuth2AuthorizedClient client) {
		MapSession session = sessions.findById(SESSION_ID);
		Map<String, OAuth2AuthorizedClient> clients = new HashMap<>();
		clients.put("keycloak", client);
		session.setAttribute(LockingRefreshTokenProvider.AUTHORIZED_CLIENTS, clients);
		sessions.save(session);
	}

	@Test
	void token_encore_valide_aucun_refresh() {
		OAuth2AuthorizedClient valid = client("a", NOW.plusSeconds(120));

		assertThat(provider(ctx -> client("b", NOW.plusSeconds(300))).authorize(context(valid))).isNull();
		assertThat(delegateCalls).hasValue(0);
	}

	@Test
	void token_expirant_verrou_libre_un_refresh_ecrit_en_session_puis_verrou_libere() {
		OAuth2AuthorizedClient expiring = client("a", NOW.plusSeconds(10));
		OAuth2AuthorizedClient refreshed = client("b", NOW.plusSeconds(300));

		OAuth2AuthorizedClient result = provider(ctx -> refreshed).authorize(context(expiring));

		assertThat(result).isSameAs(refreshed);
		assertThat(delegateCalls).hasValue(1);
		@SuppressWarnings("unchecked")
		Map<String, OAuth2AuthorizedClient> written = (Map<String, OAuth2AuthorizedClient>) httpSession
			.getAttribute(LockingRefreshTokenProvider.AUTHORIZED_CLIENTS);
		assertThat(written).containsEntry("keycloak", refreshed);
		assertThat(lock.held).doesNotContainKey(LOCK_KEY);
	}

	@Test
	void verrou_pris_par_une_autre_requete_attente_puis_relecture_sans_refresh() {
		lock.held.put(LOCK_KEY, "autre-requete");
		OAuth2AuthorizedClient fresh = client("b", NOW.plusSeconds(300));
		storeInRedis(fresh); // ecrit par la requete qui tient le verrou

		OAuth2AuthorizedClient result = provider(ctx -> client("c", NOW.plusSeconds(300)))
			.authorize(context(client("a", NOW.plusSeconds(10))));

		assertThat(result.getAccessToken().getTokenValue()).isEqualTo("b");
		assertThat(delegateCalls).hasValue(0);
	}

	@Test
	void verrou_obtenu_mais_token_deja_rafraichi_relecture_sans_refresh() {
		storeInRedis(client("b", NOW.plusSeconds(300)));

		OAuth2AuthorizedClient result = provider(ctx -> client("c", NOW.plusSeconds(300)))
			.authorize(context(client("a", NOW.plusSeconds(10))));

		assertThat(result.getAccessToken().getTokenValue()).isEqualTo("b");
		assertThat(delegateCalls).hasValue(0);
		assertThat(lock.held).doesNotContainKey(LOCK_KEY);
	}

	@Test
	void verrou_jamais_libere_ni_token_relu_echec_controle_apres_le_delai() {
		lock.held.put(LOCK_KEY, "requete-bloquee");

		assertThatThrownBy(() -> provider(ctx -> client("c", NOW.plusSeconds(300)))
			.authorize(context(client("a", NOW.plusSeconds(10)))))
			.isInstanceOf(ClientAuthorizationException.class)
			.hasMessageContaining("refresh_lock_timeout");
		assertThat(delegateCalls).hasValue(0);
	}

	@Test
	void erreur_reseau_pendant_le_refresh_verrou_libere() {
		assertThatThrownBy(() -> provider(ctx -> {
			throw new ResourceAccessException("Keycloak injoignable");
		}).authorize(context(client("a", NOW.plusSeconds(10))))).isInstanceOf(ResourceAccessException.class);

		assertThat(lock.held).doesNotContainKey(LOCK_KEY);
	}

	/** Verrou en memoire, meme contrat que RedisRefreshLock. */
	static final class FakeLock implements RefreshLock {

		final Map<String, String> held = new ConcurrentHashMap<>();

		@Override
		public boolean tryLock(String key, String owner, Duration ttl) {
			return held.putIfAbsent(key, owner) == null;
		}

		@Override
		public void unlock(String key, String owner) {
			held.remove(key, owner);
		}
	}
}
