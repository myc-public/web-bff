package ma.myc.web.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

/** SB1 / factor Config : configuration invalide -> arret au demarrage, avec le nom de la propriete, jamais le secret. */
class BffPropertiesTest {

	@EnableConfigurationProperties(BffProperties.class)
	static class Props {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(Props.class)
		.withPropertyValues(
				"bff.instance=web-bff-customers",
				"bff.oidc.issuer-uri=https://front.example/realms/myc-customers",
				"bff.oidc.internal-uri=http://keycloak:8080/realms/myc-customers",
				"bff.oidc.client-id=web-bff",
				"bff.oidc.scopes=openid",
				"bff.allowed-origins=https://front.example",
				"bff.api-base-url=http://apisix-internal",
				"bff.session.idle-timeout=30m",
				"bff.session.absolute-timeout=10h",
				"bff.refresh.clock-skew=30s",
				"bff.refresh.lock-ttl=5s",
				"bff.refresh.wait-step=100ms",
				"bff.refresh.max-wait=5s");

	@Test
	void configuration_complete_acceptee_secret_absent_du_toString() {
		runner.withPropertyValues("bff.oidc.client-secret=valeur-secrete").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(BffProperties.class).toString()).doesNotContain("valeur-secrete");
		});
	}

	@Test
	void secret_absent_demarrage_refuse_avec_le_nom() {
		runner.run(context -> assertThat(context).getFailure()
			.rootCause()
			.hasMessageContaining("clientSecret"));
	}

	@Test
	void origine_avec_chemin_refusee() {
		runner.withPropertyValues("bff.oidc.client-secret=x", "bff.allowed-origins=https://front.example/")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void nom_d_instance_invalide_refuse() {
		runner.withPropertyValues("bff.oidc.client-secret=x", "bff.instance=web bff *")
			.run(context -> assertThat(context).hasFailed());
	}
}
