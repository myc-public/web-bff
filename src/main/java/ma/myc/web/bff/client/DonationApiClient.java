package ma.myc.web.bff.client;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * DB7 : appels a l'API par APISIX interne. Le Bearer est pose (et rafraichi) par l'intercepteur OAuth2 du
 * RestClient ; les reponses sont reduites aux DTO du front.
 */
public class DonationApiClient {

	private final RestClient api;

	public DonationApiClient(RestClient api) {
		this.api = api;
	}

	public DonorView myProfile() {
		return api.get().uri("/api/v1/donors/me").retrieve().body(DonorView.class);
	}

	public DonorView register(DonorCreate request) {
		return api.post()
			.uri("/api/v1/donors/me")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(DonorView.class);
	}
}
