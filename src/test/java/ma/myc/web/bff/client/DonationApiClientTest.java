package ma.myc.web.bff.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** SB6 : la reponse de l'API est reduite au DTO du front (4 champs). */
class DonationApiClientTest {

	private static final String API_DONOR = """
			{"donorId":"0b7c1e0e-5d1e-4c43-9a7e-2f0f3f0d8a11","lastName":"Curie","firstName":"Marie",
			 "email":"marie@example.org","dateOfBirth":"1990-01-01","country":"MA"}""";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://api.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final DonationApiClient client = new DonationApiClient(builder.build());

	@Test
	void profil_reduit_a_quatre_champs() {
		server.expect(requestTo("http://api.test/api/v1/donors/me"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(API_DONOR, MediaType.APPLICATION_JSON));

		assertThat(client.myProfile()).isEqualTo(new DonorView("Marie", "Curie", "marie@example.org", "MA"));
		server.verify();
	}

	@Test
	void creation_transmet_le_corps_valide() {
		server.expect(requestTo("http://api.test/api/v1/donors/me"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"lastName\":\"Curie\",\"firstName\":\"Marie\",\"dateOfBirth\":\"1990-01-01\",\"country\":\"MA\"}"))
			.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(API_DONOR));

		DonorView created = client.register(new DonorCreate("Curie", "Marie", LocalDate.of(1990, 1, 1), "MA"));

		assertThat(created.email()).isEqualTo("marie@example.org");
		server.verify();
	}
}
