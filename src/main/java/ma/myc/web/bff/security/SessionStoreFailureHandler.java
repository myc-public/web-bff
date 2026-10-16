package ma.myc.web.bff.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * SEC-BFF-12 : echec de la redirection vers Keycloak (OAuth2AuthorizationRequestRedirectFilter intercepte l'erreur,
 * qui n'atteint pas SessionStoreUnavailableFilter). Redis plein ou injoignable -> 503 ; sinon comme Spring : 500.
 */
public class SessionStoreFailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(SessionStoreFailureHandler.class);

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		if (SessionStoreUnavailableFilter.writeIfSessionStoreFailure(exception, request, response)) {
			return;
		}
		log.error("connexion.redirection.echec", exception);
		response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
	}
}
