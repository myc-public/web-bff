package ma.myc.web.bff.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration de l'instance (DB2) : une valeur absente ou invalide arrete le demarrage, avec le nom de la
 * propriete et jamais sa valeur.
 */
@Validated
@ConfigurationProperties("bff")
public record BffProperties(
		@NotBlank @Pattern(regexp = "[a-z0-9-]+") String instance,
		@Valid @NotNull Oidc oidc,
		@NotEmpty List<@Pattern(regexp = "https?://[^/]+") String> allowedOrigins,
		@NotBlank String apiBaseUrl,
		@Valid @NotNull Session session,
		@Valid @NotNull Refresh refresh) {

	/** DB4 : emetteur public (iss, endpoints du navigateur) et URL interne (endpoints appeles par le serveur). */
	public record Oidc(@NotBlank String issuerUri, @NotBlank String internalUri, @NotBlank String clientId,
			@NotBlank String clientSecret, @NotEmpty List<String> scopes) {

		@Override
		public String toString() {
			return "Oidc[issuerUri=" + issuerUri + ", internalUri=" + internalUri + ", clientId=" + clientId + "]";
		}
	}

	/** DB5 : inactivite (Spring Session) et duree absolue (AbsoluteTimeoutFilter). */
	public record Session(@NotNull Duration idleTimeout, @NotNull Duration absoluteTimeout) {
	}

	/** DB6 : marge avant expiration et verrou Redis par session. */
	public record Refresh(@NotNull Duration clockSkew, @NotNull Duration lockTtl, @NotNull Duration waitStep,
			@NotNull Duration maxWait) {
	}

	/** Prefixe des cles Redis de l'instance (couvert par l'ACL ~instance:*). */
	public String redisPrefix() {
		return instance + ":";
	}
}
