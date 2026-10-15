package ma.myc.web.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import ma.myc.web.bff.client.DonationApiClient;

/**
 * DB7 / DB11 : RestClient construit depuis le builder de Boot (observation : traceparent propage jusqu'a l'API),
 * Bearer du client "keycloak" de la session courante.
 */
@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

	@Bean
	DonationApiClient donationApiClient(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
			BffProperties props) {
		OAuth2ClientHttpRequestInterceptor bearer = new OAuth2ClientHttpRequestInterceptor(manager);
		bearer.setClientRegistrationIdResolver(request -> KeycloakRegistrationConfig.REGISTRATION_ID);
		return new DonationApiClient(builder.baseUrl(props.apiBaseUrl()).requestInterceptor(bearer).build());
	}
}
