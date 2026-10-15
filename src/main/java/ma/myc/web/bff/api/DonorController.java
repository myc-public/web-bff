package ma.myc.web.bff.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ma.myc.web.bff.client.DonationApiClient;
import ma.myc.web.bff.client.DonorCreate;
import ma.myc.web.bff.client.DonorView;

/**
 * DB10 : profil du donateur connecte. 404 de l'API = pas encore de profil (le front affiche le formulaire) ;
 * 409 au POST = profil deja cree (succes pour le front).
 */
@RestController
public class DonorController {

	private final DonationApiClient api;

	public DonorController(DonationApiClient api) {
		this.api = api;
	}

	@GetMapping("/api/donors/me")
	public DonorView myProfile() {
		return api.myProfile();
	}

	@PostMapping(value = "/api/donors/me", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DonorView register(@Valid @RequestBody DonorCreate request) {
		return api.register(request);
	}
}
