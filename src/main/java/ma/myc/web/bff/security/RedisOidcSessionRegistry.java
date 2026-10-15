package ma.myc.web.bff.security;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.SessionRepository;

/**
 * DB9 : registre sid Keycloak -> sessions BFF dans Redis (le registre par defaut est en memoire, faux des deux
 * replicas). Au back-channel logout, supprime directement les sessions concernees et ne renvoie rien a deconnecter
 * par HTTP. Un logout_token deja vu (jti) est ignore : reponse 200 sans effet (traitement idempotent, ADR du 13/10).
 * Les entrees d'une session detruite autrement (inactivite) disparaissent avec leur TTL.
 */
public class RedisOidcSessionRegistry implements OidcSessionRegistry {

	private static final Logger log = LoggerFactory.getLogger(RedisOidcSessionRegistry.class);

	private final StringRedisTemplate redis;
	private final SessionRepository<?> sessions;
	private final String prefix;
	private final Duration ttl;
	private final Duration jtiTtl;

	public RedisOidcSessionRegistry(StringRedisTemplate redis, SessionRepository<?> sessions, String redisPrefix,
			Duration ttl, Duration jtiTtl) {
		this.redis = redis;
		this.sessions = sessions;
		this.prefix = redisPrefix + "oidc:";
		this.ttl = ttl;
		this.jtiTtl = jtiTtl;
	}

	@Override
	public void saveSessionInformation(OidcSessionInformation info) {
		String sid = info.getPrincipal() instanceof OidcUser user ? user.getIdToken().getClaimAsString("sid") : null;
		if (sid == null) {
			log.warn("registre.sans.sid session={}", LogIds.hash(info.getSessionId()));
			return;
		}
		redis.opsForSet().add(sidKey(sid), info.getSessionId());
		redis.expire(sidKey(sid), ttl);
		redis.opsForValue().set(sessionKey(info.getSessionId()), sid, ttl);
		log.info("registre.ajout session={} sid={}", LogIds.hash(info.getSessionId()), sid);
	}

	@Override
	public OidcSessionInformation removeSessionInformation(String clientSessionId) {
		String sid = redis.opsForValue().getAndDelete(sessionKey(clientSessionId));
		if (sid != null) {
			redis.opsForSet().remove(sidKey(sid), clientSessionId);
		}
		return null;
	}

	@Override
	public Iterable<OidcSessionInformation> removeSessionInformation(OidcLogoutToken token) {
		if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(prefix + "jti:" + token.getId(), "1", jtiTtl))) {
			log.warn("backchannel.rejeu jti={}", token.getId());
			return List.of();
		}
		String sid = token.getSessionId();
		if (sid == null) {
			log.warn("backchannel.sans.sid jti={}", token.getId());
			return List.of();
		}
		Set<String> ids = redis.opsForSet().members(sidKey(sid));
		int deleted = 0;
		if (ids != null) {
			for (String id : ids) {
				sessions.deleteById(id);
				redis.delete(sessionKey(id));
				deleted++;
			}
		}
		redis.delete(sidKey(sid));
		log.info("backchannel.ok sid={} sessionsSupprimees={}", sid, deleted);
		return List.of();
	}

	private String sidKey(String sid) {
		return prefix + "sid:" + sid;
	}

	private String sessionKey(String sessionId) {
		return prefix + "session:" + sessionId;
	}
}
