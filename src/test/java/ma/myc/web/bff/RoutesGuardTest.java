package ma.myc.web.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * DB7 / SB6 : liste fermee des routes du BFF. Ajouter une route = modifier ce test (revue de securite explicite) ;
 * jamais de proxy generique.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoutesGuardTest {

	private static final Set<String> EXPECTED = Set.of(
			"GET /api/session",
			"GET /api/donors/me",
			"POST /api/donors/me");

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mapping;

	@Test
	void seules_les_routes_declarees_sont_exposees() {
		Set<String> routes = new TreeSet<>();
		mapping.getHandlerMethods().forEach((info, method) -> {
			if (method.getBeanType().getPackageName().startsWith("ma.myc")) {
				info.getMethodsCondition().getMethods().forEach(httpMethod -> info.getPatternValues()
					.forEach(pattern -> routes.add(httpMethod + " " + pattern)));
			}
		});
		assertThat(routes).containsExactlyInAnyOrderElementsOf(EXPECTED);
	}
}
