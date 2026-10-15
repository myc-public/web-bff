package ma.myc.web.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import ma.myc.web.bff.client.DonationApiClient;
import ma.myc.web.bff.config.SessionConfig;
import ma.myc.web.bff.client.DonorCreate;
import ma.myc.web.bff.client.DonorView;
import ma.myc.web.bff.security.RefreshLock;

/** Chaine de securite complete par MockMvc (contexte /bff), sans Keycloak ni Redis : SB2, SB3, SB6, SB7, DB8, DB9. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFlowTest {

	private static final String FRONT = "http://localhost";
	private static final String ISSUER = "https://front.example/realms/myc-customers";
	private static final String PROFILE = "{\"lastName\":\"Curie\",\"firstName\":\"Marie\",\"country\":\"MA\"}";

	@Autowired
	MockMvc mvc;

	@Autowired
	ClientRegistrationRepository registrations;

	@Autowired
	SessionRepository<? extends Session> sessions;

	@MockitoBean
	DonationApiClient api;

	@MockitoBean
	OidcSessionRegistry sessionRegistry;

	@MockitoBean
	RefreshLock refreshLock;

	/**
	 * Jeton CSRF comme le front : cookie XSRF-TOKEN renvoye dans l'en-tete X-XSRF-TOKEN. Pas de csrf() de
	 * spring-security-test : il remplace durablement le depot CSRF du contexte partage par un depot en session.
	 */
	private static RequestPostProcessor xsrf() {
		return request -> {
			request.setCookies(new Cookie("XSRF-TOKEN", "jeton-identique"));
			request.addHeader("X-XSRF-TOKEN", "jeton-identique");
			return request;
		};
	}

	private static MockHttpServletRequestBuilder bff(MockHttpServletRequestBuilder builder) {
		return builder.contextPath("/bff");
	}

	private OidcLoginRequestPostProcessor user() {
		ClientRegistration keycloak = registrations.findByRegistrationId("keycloak");
		return oidcLogin().clientRegistration(keycloak)
			.idToken(token -> token.issuer(ISSUER)
				.claim("sid", "sid-test")
				.claim("given_name", "Marie")
				.claim("family_name", "Curie")
				.claim("email", "marie@example.org"));
	}

	// ---------- SB2 / SB3 : connexion ----------

	@Test
	void connexion_redirige_vers_keycloak_public_avec_pkce_s256_et_pose_le_cookie_host() throws Exception {
		MvcResult result = mvc.perform(bff(get("/bff/oauth2/authorization/keycloak")))
			.andExpect(status().isFound())
			.andReturn();

		String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
		assertThat(location).startsWith(ISSUER + "/protocol/openid-connect/auth?")
			.contains("code_challenge_method=S256")
			.contains("code_challenge=")
			.contains("redirect_uri=http://localhost/bff/login/oauth2/code/keycloak");
		String cookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(c -> c.startsWith("__Host-SESSION="))
			.findFirst()
			.orElseThrow();
		assertThat(cookie).contains("Path=/;").contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
	}

	@Test
	void redirect_uri_suit_l_origine_du_front_transmise_par_la_passerelle() throws Exception {
		MvcResult result = mvc.perform(bff(get("/bff/oauth2/authorization/keycloak"))
			.header("X-Forwarded-Proto", "https")
			.header("X-Forwarded-Host", "front.example"))
			.andExpect(status().isFound())
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
			.contains("redirect_uri=https://front.example/bff/login/oauth2/code/keycloak");
	}

	// ---------- SB6 : session, routes, authentification ----------

	@Test
	void session_anonyme() throws Exception {
		mvc.perform(bff(get("/bff/api/session")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(false));
	}

	@Test
	void session_connectee_sans_token_ni_identifiant() throws Exception {
		String body = mvc.perform(bff(get("/bff/api/session")).with(user()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.givenName").value("Marie"))
			.andExpect(jsonPath("$.email").value("marie@example.org"))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);

		assertThat(body).doesNotContainIgnoringCase("token").doesNotContain("sid-test");
	}

	@Test
	void api_sans_session_401_sans_redirection() throws Exception {
		mvc.perform(bff(get("/bff/api/donors/me"))).andExpect(status().isUnauthorized());
	}

	@Test
	void route_non_declaree_404() throws Exception {
		mvc.perform(bff(get("/bff/api/donations")).with(user())).andExpect(status().isNotFound());
	}

	@Test
	void profil_renvoye_par_le_dto() throws Exception {
		given(api.myProfile()).willReturn(new DonorView("Marie", "Curie", "marie@example.org", "MA"));

		mvc.perform(bff(get("/bff/api/donors/me")).with(user()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Marie"))
			.andExpect(jsonPath("$.country").value("MA"));
	}

	// ---------- SB7 / DB8 : Origin + CSRF ----------

	@Test
	void mutation_sans_origin_403() throws Exception {
		mvc.perform(bff(post("/bff/api/donors/me")).with(user()).with(xsrf())
			.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
			.andExpect(status().isForbidden());
		verify(api, never()).register(any());
	}

	@Test
	void mutation_origine_etrangere_403() throws Exception {
		mvc.perform(bff(post("/bff/api/donors/me")).with(user()).with(xsrf())
			.header("Origin", "https://evil.example")
			.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
			.andExpect(status().isForbidden());
		verify(api, never()).register(any());
	}

	@Test
	void mutation_sans_jeton_csrf_403() throws Exception {
		mvc.perform(bff(post("/bff/api/donors/me")).with(user())
			.header("Origin", FRONT)
			.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
			.andExpect(status().isForbidden());
		verify(api, never()).register(any());
	}

	@Test
	void mutation_jeton_csrf_different_du_cookie_403() throws Exception {
		mvc.perform(bff(post("/bff/api/donors/me")).with(user())
			.header("Origin", FRONT)
			.cookie(new Cookie("XSRF-TOKEN", "jeton-du-cookie"))
			.header("X-XSRF-TOKEN", "autre-jeton")
			.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
			.andExpect(status().isForbidden());
		verify(api, never()).register(any());
	}

	@Test
	void mutation_jeton_csrf_egal_au_cookie_acceptee() throws Exception {
		given(api.register(any(DonorCreate.class))).willReturn(new DonorView("Marie", "Curie", "marie@example.org", "MA"));

		mvc.perform(bff(post("/bff/api/donors/me")).with(user())
			.header("Origin", FRONT)
			.cookie(new Cookie("XSRF-TOKEN", "jeton-identique"))
			.header("X-XSRF-TOKEN", "jeton-identique")
			.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.lastName").value("Curie"));
	}

	@Test
	void put_et_delete_proteges_comme_post() throws Exception {
		mvc.perform(bff(put("/bff/api/donors/me")).with(user()).header("Origin", FRONT))
			.andExpect(status().isForbidden());
		mvc.perform(bff(delete("/bff/api/donors/me")).with(user()).header("Origin", FRONT))
			.andExpect(status().isForbidden());
	}

	@Test
	void corps_invalide_400() throws Exception {
		mvc.perform(bff(post("/bff/api/donors/me")).with(user()).with(xsrf())
			.header("Origin", FRONT)
			.contentType(MediaType.APPLICATION_JSON).content("{\"lastName\":\"\",\"firstName\":\"Marie\"}"))
			.andExpect(status().isBadRequest());
		verify(api, never()).register(any());
	}

	// ---------- erreurs de l'API et du refresh ----------

	@Test
	void refresh_refuse_detruit_la_session_et_renvoie_401() throws Exception {
		// Session existante (creee au debut de la connexion : cookie __Host-SESSION), puis refresh refuse par Keycloak
		String cookie = mvc.perform(bff(get("/bff/oauth2/authorization/keycloak")))
			.andReturn()
			.getResponse()
			.getCookie(SessionConfig.COOKIE_NAME)
			.getValue();
		assertThat(sessions.findById(cookie)).isNotNull();
		given(api.myProfile()).willThrow(new ClientAuthorizationException(new OAuth2Error("invalid_grant"), "keycloak"));

		MvcResult result = mvc.perform(bff(get("/bff/api/donors/me")).with(user())
			.cookie(new Cookie(SessionConfig.COOKIE_NAME, cookie)))
			.andExpect(status().isUnauthorized())
			.andReturn();

		assertThat(sessions.findById(cookie)).isNull();
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
			.anySatisfy(header -> assertThat(header).startsWith("__Host-SESSION=;").contains("Max-Age=0"));
	}

	@Test
	void statuts_de_l_api_repris_et_api_injoignable_503() throws Exception {
		willThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null)).given(api).myProfile();
		mvc.perform(bff(get("/bff/api/donors/me")).with(user())).andExpect(status().isNotFound());

		willThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "", null, null, null)).given(api).myProfile();
		mvc.perform(bff(get("/bff/api/donors/me")).with(user())).andExpect(status().isBadGateway());

		willThrow(new ResourceAccessException("connexion refusee")).given(api).myProfile();
		mvc.perform(bff(get("/bff/api/donors/me")).with(user())).andExpect(status().isServiceUnavailable());
	}

	@Test
	void conflit_409_repris() throws Exception {
		given(api.register(any(DonorCreate.class)))
			.willThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "", null, null, null));
		mvc.perform(bff(post("/bff/api/donors/me")).with(user()).with(xsrf())
			.header("Origin", FRONT)
			.contentType(MediaType.APPLICATION_JSON).content(PROFILE))
			.andExpect(status().isConflict());
	}

	// ---------- deconnexion ----------

	@Test
	void deconnexion_vers_end_session_public_et_retour_a_la_racine_du_front() throws Exception {
		MvcResult result = mvc.perform(bff(post("/bff/logout")).with(user()).with(xsrf()).header("Origin", FRONT))
			.andExpect(status().isFound())
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
			.startsWith(ISSUER + "/protocol/openid-connect/logout?")
			.contains("id_token_hint=")
			.contains("post_logout_redirect_uri=http://localhost/");
	}

	@Test
	void deconnexion_sans_csrf_403() throws Exception {
		mvc.perform(bff(post("/bff/logout")).with(user()).header("Origin", FRONT)).andExpect(status().isForbidden());
	}

	// ---------- DB9 : back-channel ----------

	@Test
	void back_channel_hors_origin_et_csrf_jeton_invalide_400() throws Exception {
		mvc.perform(bff(post("/bff/logout/connect/back-channel/keycloak"))
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.content("logout_token=pas-un-jwt"))
			.andExpect(status().isBadRequest());
	}

	// ---------- sondes ----------

	@Test
	void sonde_liveness_publique() throws Exception {
		mvc.perform(bff(get("/bff/management/health/liveness"))).andExpect(status().isOk());
	}
}
