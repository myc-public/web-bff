package ma.myc.web.bff.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** DB7 : etat de connexion pour le front (connecte, prenom, nom, email) ; jamais de token ni d'identifiant de session. */
@RestController
public class SessionController {

	public record SessionView(boolean authenticated, String givenName, String familyName, String email) {

		static final SessionView ANONYMOUS = new SessionView(false, null, null, null);
	}

	@GetMapping("/api/session")
	public SessionView session(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
			return new SessionView(true, user.getGivenName(), user.getFamilyName(), user.getEmail());
		}
		return SessionView.ANONYMOUS;
	}
}
