package ma.myc.web.bff.security;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ma.myc.web.bff.config.BffProperties;

/**
 * DB6 : refresh serialise par session (spike SB4). Keycloak revoque le refresh token a chaque usage : deux refresh
 * simultanes avec le meme token coupent la chaine et deconnectent l'utilisateur. Une seule requete rafraichit et
 * ecrit le nouveau client dans la session (ecriture immediate dans Redis) avant de liberer le verrou ; les autres
 * attendent et relisent la session dans Redis.
 */
public class LockingRefreshTokenProvider implements OAuth2AuthorizedClientProvider {

	/** Attribut de session de HttpSessionOAuth2AuthorizedClientRepository (clients autorises par registration). */
	static final String AUTHORIZED_CLIENTS = HttpSessionOAuth2AuthorizedClientRepository.class.getName()
			+ ".AUTHORIZED_CLIENTS";

	private static final Logger log = LoggerFactory.getLogger(LockingRefreshTokenProvider.class);

	private final OAuth2AuthorizedClientProvider delegate;
	private final RefreshLock lock;
	private final SessionRepository<? extends Session> sessions;
	private final BffProperties.Refresh settings;
	private final String lockPrefix;
	private final Clock clock;

	public LockingRefreshTokenProvider(OAuth2AuthorizedClientProvider delegate, RefreshLock lock,
			SessionRepository<? extends Session> sessions, BffProperties.Refresh settings, String redisPrefix,
			Clock clock) {
		this.delegate = delegate;
		this.lock = lock;
		this.sessions = sessions;
		this.settings = settings;
		this.lockPrefix = redisPrefix + "lock:";
		this.clock = clock;
	}

	@Override
	public OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context) {
		OAuth2AuthorizedClient current = context.getAuthorizedClient();
		if (current == null || current.getRefreshToken() == null || !expiring(current)) {
			return null;
		}
		HttpSession session = currentSession();
		String registrationId = current.getClientRegistration().getRegistrationId();
		String key = lockPrefix + session.getId();
		String owner = UUID.randomUUID().toString();
		long attempts = Math.max(1, settings.maxWait().toMillis() / settings.waitStep().toMillis());
		for (long i = 0; i < attempts; i++) {
			if (lock.tryLock(key, owner, settings.lockTtl())) {
				try {
					OAuth2AuthorizedClient stored = stored(session.getId(), registrationId);
					if (stored != null && !expiring(stored)) {
						return stored; // rafraichi par une autre requete entre-temps
					}
					return refresh(context, session, registrationId);
				}
				finally {
					lock.unlock(key, owner);
				}
			}
			pause();
			OAuth2AuthorizedClient stored = stored(session.getId(), registrationId);
			if (stored != null && !expiring(stored)) {
				log.info("refresh.attente.relu session={}", LogIds.hash(session.getId()));
				return stored;
			}
		}
		log.warn("refresh.verrou.delai session={}", LogIds.hash(session.getId()));
		throw new ClientAuthorizationException(new OAuth2Error("refresh_lock_timeout"), registrationId);
	}

	private OAuth2AuthorizedClient refresh(OAuth2AuthorizationContext context, HttpSession session,
			String registrationId) {
		OAuth2AuthorizedClient refreshed = delegate.authorize(context);
		if (refreshed != null) {
			log.info("refresh.ok session={}", LogIds.hash(session.getId()));
			@SuppressWarnings("unchecked")
			Map<String, OAuth2AuthorizedClient> clients = (Map<String, OAuth2AuthorizedClient>) session
				.getAttribute(AUTHORIZED_CLIENTS);
			Map<String, OAuth2AuthorizedClient> copy = clients == null ? new HashMap<>() : new HashMap<>(clients);
			copy.put(registrationId, refreshed);
			session.setAttribute(AUTHORIZED_CLIENTS, copy); // ecrit avant la liberation du verrou
		}
		return refreshed;
	}

	private OAuth2AuthorizedClient stored(String sessionId, String registrationId) {
		Session session = sessions.findById(sessionId);
		if (session == null) {
			return null;
		}
		Map<String, OAuth2AuthorizedClient> clients = session.getAttribute(AUTHORIZED_CLIENTS);
		return clients == null ? null : clients.get(registrationId);
	}

	private boolean expiring(OAuth2AuthorizedClient client) {
		Instant expiresAt = client.getAccessToken().getExpiresAt();
		return expiresAt != null && expiresAt.minus(settings.clockSkew()).isBefore(clock.instant());
	}

	private static HttpSession currentSession() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
			.getRequest();
		return request.getSession();
	}

	private void pause() {
		try {
			Thread.sleep(settings.waitStep());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ClientAuthorizationException(new OAuth2Error("refresh_interrupted"), "keycloak", e);
		}
	}
}
