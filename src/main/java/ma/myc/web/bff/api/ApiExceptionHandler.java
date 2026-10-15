package ma.myc.web.bff.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import ma.myc.web.bff.security.LogIds;

/**
 * Erreurs renvoyees au front, sans detail interne : refresh refuse -> session detruite et 401 (reconnexion) ; statut
 * de l'API repris tel quel (404 pas de profil, 409 deja cree, 400) ; API injoignable -> 503.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ClientAuthorizationException.class)
	ResponseEntity<ProblemDetail> reauthenticate(ClientAuthorizationException e, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		log.warn("autorisation.refusee cause={} session={}", e.getError().getErrorCode(),
				LogIds.hash(session == null ? null : session.getId()));
		if (session != null) {
			session.invalidate();
		}
		return problem(HttpStatus.UNAUTHORIZED, "reconnexion");
	}

	@ExceptionHandler(RestClientResponseException.class)
	ResponseEntity<ProblemDetail> api(RestClientResponseException e) {
		HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
		if (status == null || status.is5xxServerError()) {
			log.warn("api.erreur statut={}", e.getStatusCode().value());
			return problem(HttpStatus.BAD_GATEWAY, "api");
		}
		return problem(status, "api");
	}

	@ExceptionHandler(ResourceAccessException.class)
	ResponseEntity<ProblemDetail> unavailable(ResourceAccessException e) {
		log.warn("api.injoignable cause={}", e.getClass().getSimpleName());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "api-indisponible");
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
		ProblemDetail body = ProblemDetail.forStatus(status);
		body.setTitle(code);
		return ResponseEntity.status(status).body(body);
	}
}
