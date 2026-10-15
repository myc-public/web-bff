package ma.myc.web.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** SEC-BFF-12 : Redis plein ou injoignable -> 503 sans detail interne. */
class SessionStoreUnavailableFilterTest {

	private static MockHttpServletRequest request() {
		return new MockHttpServletRequest("GET", "/bff/oauth2/authorization/keycloak");
	}

	@Test
	void erreur_redis_enveloppee_donne_503() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		RuntimeException wrapped = new IllegalStateException("filtre",
				new RedisSystemException("Error in execution", new RuntimeException("OOM command not allowed")));

		new SessionStoreUnavailableFilter().doFilter(request(), response, (req, res) -> {
			throw wrapped;
		});

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		assertThat(response.getContentAsString()).contains("sessions-indisponibles").doesNotContain("OOM");
	}

	@Test
	void autre_exception_relancee() {
		IllegalStateException other = new IllegalStateException("autre");

		assertThatThrownBy(() -> new SessionStoreUnavailableFilter().doFilter(request(), new MockHttpServletResponse(),
				(req, res) -> {
					throw other;
				})).isSameAs(other);
	}

	@Test
	void reponse_deja_envoyee_relancee() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setCommitted(true);
		RedisSystemException redis = new RedisSystemException("Error in execution", null);

		assertThatThrownBy(() -> new SessionStoreUnavailableFilter().doFilter(request(), response, (req, res) -> {
			throw redis;
		})).isSameAs(redis);
	}
}
