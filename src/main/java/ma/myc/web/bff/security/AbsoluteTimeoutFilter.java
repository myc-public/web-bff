package ma.myc.web.bff.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * DB5 : duree de vie absolue de la session (Spring Session ne gere que l'inactivite). Au-dela, la session est
 * detruite avant la securite : la requete continue sans session (401 sur /api, reconnexion).
 */
public class AbsoluteTimeoutFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(AbsoluteTimeoutFilter.class);

	private final Duration maxAge;
	private final Clock clock;

	public AbsoluteTimeoutFilter(Duration maxAge, Clock clock) {
		this.maxAge = maxAge;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && Instant.ofEpochMilli(session.getCreationTime()).plus(maxAge).isBefore(clock.instant())) {
			log.info("session.expiration.absolue session={}", LogIds.hash(session.getId()));
			session.invalidate();
		}
		chain.doFilter(request, response);
	}
}
