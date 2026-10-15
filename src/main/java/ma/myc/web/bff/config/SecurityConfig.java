package ma.myc.web.bff.config;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.AuthorizationCodeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.DelegatingOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import ma.myc.web.bff.security.AbsoluteTimeoutFilter;
import ma.myc.web.bff.security.LockingRefreshTokenProvider;
import ma.myc.web.bff.security.LogIds;
import ma.myc.web.bff.security.OriginFilter;
import ma.myc.web.bff.security.RedisOidcSessionRegistry;
import ma.myc.web.bff.security.RedisRefreshLock;
import ma.myc.web.bff.security.RefreshLock;

/**
 * Securite du BFF (ADR K6 : DB4 a DB9, precisions du spike du 13/10).
 * <ul>
 * <li>connexion : {@code GET /bff/oauth2/authorization/keycloak} (code + PKCE S256, actif par defaut), retour sur
 * {@code /} de l'origine du front ;</li>
 * <li>deconnexion : {@code POST /bff/logout} avec jeton CSRF, puis end_session Keycloak et retour sur {@code /} ;</li>
 * <li>back-channel : {@code POST /bff/logout/connect/back-channel/keycloak}, URL interne jamais routee par APISIX.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	public static final String BACK_CHANNEL_PATH = "/logout/connect/back-channel/";

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	OAuth2AuthorizedClientRepository authorizedClientRepository() {
		return new HttpSessionOAuth2AuthorizedClientRepository();
	}

	@Bean
	RefreshLock refreshLock(StringRedisTemplate redis) {
		return new RedisRefreshLock(redis);
	}

	/** DB6 : refresh 30 s avant expiration, serialise par le verrou Redis de la session (obligatoire). */
	@Bean
	OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
			OAuth2AuthorizedClientRepository authorizedClients, RefreshLock refreshLock,
			SessionRepository<? extends Session> sessions, BffProperties props, Clock clock) {
		RefreshTokenOAuth2AuthorizedClientProvider refresh = new RefreshTokenOAuth2AuthorizedClientProvider();
		refresh.setClockSkew(props.refresh().clockSkew());
		refresh.setClock(clock);
		LockingRefreshTokenProvider locking = new LockingRefreshTokenProvider(refresh, refreshLock, sessions,
				props.refresh(), props.redisPrefix(), clock);
		DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(registrations,
				authorizedClients);
		manager.setAuthorizedClientProvider(
				new DelegatingOAuth2AuthorizedClientProvider(new AuthorizationCodeOAuth2AuthorizedClientProvider(), locking));
		return manager;
	}

	/** DB9 : registre sid -> sessions partage par les replicas ; anti-rejeu jti 5 min. */
	@Bean
	OidcSessionRegistry oidcSessionRegistry(StringRedisTemplate redis, SessionRepository<? extends Session> sessions,
			BffProperties props) {
		return new RedisOidcSessionRegistry(redis, sessions, props.redisPrefix(), props.session().absoluteTimeout(),
				Duration.ofMinutes(5));
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository registrations,
			BffProperties props, Clock clock) throws Exception {
		// Redirections vers l'origine du front (hors contexte /bff) ou vers Keycloak (URL absolue)
		OidcClientInitiatedLogoutSuccessHandler logoutSuccess = new OidcClientInitiatedLogoutSuccessHandler(registrations);
		logoutSuccess.setPostLogoutRedirectUri("{baseScheme}://{baseHost}{basePort}/");
		logoutSuccess.setDefaultTargetUrl("/");
		logoutSuccess.setRedirectStrategy((request, response, url) -> response.sendRedirect(url));

		// DB8 : jeton CSRF dans un cookie lisible par le front (Path=/), renvoye dans l'en-tete X-XSRF-TOKEN
		// (ou le champ _csrf d'un formulaire). Charge a chaque requete : le cookie est toujours present.
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").secure(true).sameSite("Lax"));
		CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
		csrfHandler.setCsrfRequestAttributeName(null);

		http
			.authorizeHttpRequests(authorize -> authorize
				// /error : sinon un 403 (Origin, CSRF) redevient 401 au renvoi vers la page d'erreur (spike)
				.requestMatchers("/api/session", "/management/health/**", "/management/info", "/error").permitAll()
				.requestMatchers(BACK_CHANNEL_PATH + "*").permitAll()
				.anyRequest().authenticated())
			.oauth2Login(login -> login
				// Pas de page de connexion generee : le front envoie directement vers Keycloak
				.loginPage("/oauth2/authorization/" + KeycloakRegistrationConfig.REGISTRATION_ID)
				.successHandler((request, response, authentication) -> {
					String sid = authentication.getPrincipal() instanceof OidcUser user
							? user.getIdToken().getClaimAsString("sid") : null;
					log.info("connexion.ok session={} sid={}", LogIds.hash(request.getSession().getId()), sid);
					response.sendRedirect("/");
				})
				.failureHandler((request, response, exception) -> {
					log.warn("connexion.echec cause={}", exception.getMessage());
					response.sendRedirect("/?connexion=echec");
				}))
			.oauth2Client(client -> { })
			.oidcLogout(logout -> logout.backChannel(backChannel -> { }))
			.logout(logout -> logout.logoutSuccessHandler(logoutSuccess))
			.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
				.csrfTokenRequestHandler(csrfHandler)
				.ignoringRequestMatchers(BACK_CHANNEL_PATH + "*"))
			// API JSON : jamais de redirection vers Keycloak, 401 et le front envoie vers la connexion
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.addFilterBefore(new OriginFilter(new HashSet<>(props.allowedOrigins()), BACK_CHANNEL_PATH), CsrfFilter.class)
			.addFilterBefore(new AbsoluteTimeoutFilter(props.session().absoluteTimeout(), clock),
					SecurityContextHolderFilter.class);
		return http.build();
	}
}
