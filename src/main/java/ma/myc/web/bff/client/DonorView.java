package ma.myc.web.bff.client;

/** Profil renvoye au front : 4 champs (ni identifiant interne ni date de naissance). */
public record DonorView(String firstName, String lastName, String email, String country) {
}
