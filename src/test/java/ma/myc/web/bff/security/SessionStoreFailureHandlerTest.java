package ma.myc.web.bff.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** SEC-BFF-12 : echec de la redirection vers Keycloak. */
class SessionStoreFailureHandlerTest {

	private static MockHttpServletRequest request() {
		return new MockHttpServletRequest("GET", "/bff/oauth2/authorization/keycloak");
	}

	@Test
	void redis_plein_donne_503() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationException failure = new OAuth2AuthenticationException(new OAuth2Error("redirection"),
				new RedisSystemException("Error in execution", new RuntimeException("OOM command not allowed")));

		new SessionStoreFailureHandler().onAuthenticationFailure(request(), response, failure);

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getContentAsString()).contains("sessions-indisponibles").doesNotContain("OOM");
	}

	@Test
	void autre_erreur_donne_500() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new SessionStoreFailureHandler().onAuthenticationFailure(request(), response,
				new OAuth2AuthenticationException(new OAuth2Error("autre"), new IllegalStateException("autre")));

		assertThat(response.getStatus()).isEqualTo(500);
	}
}
