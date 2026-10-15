package ma.myc.web.bff.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * DB8 : une mutation (POST, PUT, PATCH, DELETE) n'est acceptee que depuis l'origine d'un front autorise, en plus du
 * jeton CSRF. Sans en-tete Origin, seul Sec-Fetch-Site=same-origin est accepte. Le back-channel (serveur a serveur,
 * URL interne) n'est pas concerne.
 */
public class OriginFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(OriginFilter.class);
	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	private final Set<String> allowedOrigins;
	private final String backChannelPath;

	public OriginFilter(Set<String> allowedOrigins, String backChannelPath) {
		this.allowedOrigins = Set.copyOf(allowedOrigins);
		this.backChannelPath = backChannelPath;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SAFE_METHODS.contains(request.getMethod())
				|| request.getRequestURI().startsWith(request.getContextPath() + backChannelPath)) {
			chain.doFilter(request, response);
			return;
		}
		String origin = request.getHeader("Origin");
		boolean allowed = origin != null ? allowedOrigins.contains(origin)
				: "same-origin".equals(request.getHeader("Sec-Fetch-Site"));
		if (!allowed) {
			log.warn("origine.refusee origin={} methode={} uri={}", origin, request.getMethod(), request.getRequestURI());
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		chain.doFilter(request, response);
	}
}
