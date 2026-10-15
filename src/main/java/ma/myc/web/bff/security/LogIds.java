package ma.myc.web.bff.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Empreinte courte d'un identifiant de session pour les logs : jamais l'identifiant lui-meme (il vaut le cookie). */
public final class LogIds {

	private LogIds() {
	}

	public static String hash(String id) {
		if (id == null) {
			return "-";
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).substring(0, 10);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
