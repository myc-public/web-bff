package ma.myc.web.bff.client;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** Creation de son profil (DB10) : memes contraintes que RegisterDonorRequest de l'API ; email et id viennent du token. */
public record DonorCreate(
		@NotBlank @Size(max = 120) String lastName,
		@NotBlank @Size(max = 120) String firstName,
		@Past LocalDate dateOfBirth,
		@Size(max = 80) String country) {
}
