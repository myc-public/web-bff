package ma.myc.web.bff.security;

import java.time.Duration;

/** DB6 : verrou distribue par session, partage par toutes les replicas de l'instance. */
public interface RefreshLock {

	/** Prend le verrou s'il est libre ; il expire seul apres ttl (replica arretee pendant un refresh). */
	boolean tryLock(String key, String owner, Duration ttl);

	/** Libere le verrou seulement s'il appartient encore a owner (pas celui d'une autre requete apres expiration). */
	void unlock(String key, String owner);
}
