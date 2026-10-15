package ma.myc.web.bff.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** SB3 : duree de vie absolue de la session. */
class AbsoluteTimeoutFilterTest {

	private static MockHttpServletRequest requestWith(MockHttpSession session) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bff/api/session");
		request.setSession(session);
		return request;
	}

	@Test
	void session_plus_vieille_que_la_duree_absolue_detruite() throws Exception {
		MockHttpSession session = new MockHttpSession();
		Clock later = Clock.fixed(Instant.ofEpochMilli(session.getCreationTime()).plus(Duration.ofHours(11)), ZoneOffset.UTC);
		MockFilterChain chain = new MockFilterChain();

		new AbsoluteTimeoutFilter(Duration.ofHours(10), later).doFilter(requestWith(session), new MockHttpServletResponse(), chain);

		assertThat(session.isInvalid()).isTrue();
		assertThat(chain.getRequest()).isNotNull(); // la requete continue, sans session
	}

	@Test
	void session_recente_conservee() throws Exception {
		MockHttpSession session = new MockHttpSession();
		Clock soon = Clock.fixed(Instant.ofEpochMilli(session.getCreationTime()).plus(Duration.ofHours(9)), ZoneOffset.UTC);

		new AbsoluteTimeoutFilter(Duration.ofHours(10), soon).doFilter(requestWith(session), new MockHttpServletResponse(),
				new MockFilterChain());

		assertThat(session.isInvalid()).isFalse();
	}
}
