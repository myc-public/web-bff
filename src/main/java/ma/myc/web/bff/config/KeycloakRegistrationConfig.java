package ma.myc.web.bff.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * DB4 : client Keycloak declare sans decouverte (aucun appel au demarrage). Le navigateur utilise les endpoints
 * publics (authorization, end_session), le serveur les endpoints internes (token, jwks, userinfo). L'iss attendu est
 * l'emetteur public : un ID token d'un autre emetteur est refuse (spike SB1).
 */
@Configuration(proxyBeanMethods = false)
public class KeycloakRegistrationConfig {

	public static final String REGISTRATION_ID = "keycloak";
	private static final String PROTOCOL = "/protocol/openid-connect";

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(BffProperties props) {
		return new InMemoryClientRegistrationRepository(keycloak(props.oidc()));
	}

	static ClientRegistration keycloak(BffProperties.Oidc oidc) {
		String pub = oidc.issuerUri();
		String internal = oidc.internalUri();
		return ClientRegistration.withRegistrationId(REGISTRATION_ID)
				.clientId(oidc.clientId())
				.clientSecret(oidc.clientSecret())
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				// {baseUrl} = origine du front + /bff : une redirect URI exacte par front, declaree dans le realm
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope(oidc.scopes())
				.issuerUri(pub)
				.authorizationUri(pub + PROTOCOL + "/auth")
				.tokenUri(internal + PROTOCOL + "/token")
				.jwkSetUri(internal + PROTOCOL + "/certs")
				.userInfoUri(internal + PROTOCOL + "/userinfo")
				.userNameAttributeName("sub")
				.providerConfigurationMetadata(Map.of("end_session_endpoint", pub + PROTOCOL + "/logout"))
				.clientName("Keycloak")
				.build();
	}
}
