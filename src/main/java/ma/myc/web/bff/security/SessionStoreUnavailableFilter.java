package ma.myc.web.bff.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SEC-BFF-12 : magasin de sessions (Redis) plein ou injoignable -> 503, sans detail interne, au lieu d'un 500. Place
 * autour de SessionRepositoryFilter : couvre l'ecriture de la session en fin de requete et les erreurs non
 * interceptees par la securite (la redirection vers Keycloak a son propre gestionnaire : SessionStoreFailureHandler).
 */
public class SessionStoreUnavailableFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(SessionStoreUnavailableFilter.class);
	private static final String BODY = "{\"type\":\"about:blank\",\"title\":\"sessions-indisponibles\",\"status\":503}";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		try {
			chain.doFilter(request, response);
		}
		catch (ServletException | IOException | RuntimeException e) {
			if (!writeIfSessionStoreFailure(e, request, response)) {
				throw e;
			}
		}
	}

	/** Erreur Redis dans la chaine des causes et reponse pas encore envoyee : 503 ecrit, true. Sinon false. */
	static boolean writeIfSessionStoreFailure(Throwable e, HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		DataAccessException cause = dataAccessCause(e);
		if (cause == null || response.isCommitted()) {
			return false;
		}
		log.warn("sessions.indisponibles cause={} uri={}", cause.getClass().getSimpleName(), request.getRequestURI());
		response.resetBuffer();
		response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write(BODY);
		return true;
	}

	private static DataAccessException dataAccessCause(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof DataAccessException dae) {
				return dae;
			}
		}
		return null;
	}
}
