package ma.myc.web.bff.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.FlushMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

import ma.myc.web.bff.security.SessionStoreUnavailableFilter;

/**
 * DB5 : sessions dans Redis, sous le prefixe de l'instance. Ecriture immediate : le verrou de refresh (DB6) relit
 * la session dans Redis. Profil "test" : sessions en memoire (tests sans Redis).
 */
@Configuration(proxyBeanMethods = false)
public class SessionConfig {

	public static final String COOKIE_NAME = "__Host-SESSION";

	@Profile("!test")
	@Configuration(proxyBeanMethods = false)
	@EnableRedisHttpSession(redisNamespace = "${bff.instance}:session", flushMode = FlushMode.IMMEDIATE)
	static class Redis {

		@Bean
		SessionRepositoryCustomizer<RedisSessionRepository> idleTimeout(BffProperties props) {
			return repository -> repository.setDefaultMaxInactiveInterval(props.session().idleTimeout());
		}
	}

	/** SEC-BFF-12 : Redis plein ou injoignable -> 503 ; enregistre juste avant SessionRepositoryFilter (autour de lui). */
	@Bean
	FilterRegistrationBean<SessionStoreUnavailableFilter> sessionStoreUnavailableFilter() {
		FilterRegistrationBean<SessionStoreUnavailableFilter> registration =
				new FilterRegistrationBean<>(new SessionStoreUnavailableFilter());
		registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
		return registration;
	}

	/** Cookie __Host-SESSION : Path=/ malgre le contexte /bff, Secure, HttpOnly, Lax, identifiant brut (pas de base64). */
	@Bean
	CookieSerializer cookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseSecureCookie(true);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseBase64Encoding(false);
		return serializer;
	}
}
