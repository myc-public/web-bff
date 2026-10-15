package ma.myc.web.bff;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

/** Profil "test" : Spring Session en memoire a la place de Redis (meme filtre, meme cookie). */
@Profile("test")
@Configuration(proxyBeanMethods = false)
@EnableSpringHttpSession
class TestSessionConfig {

	@Bean
	MapSessionRepository sessionRepository() {
		return new MapSessionRepository(new ConcurrentHashMap<>());
	}
}
