package ma.myc.web.bff.config.observability;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/**
 * Exporte les logs en OTLP (Loki) en plus de la console JSON ECS (stdout).
 * Spring Boot configure l'export OTLP des logs mais n'installe pas l'appender Logback :
 * on l'attache ici a la racine, sans logback-spring.xml, pour garder le structured logging natif de Boot.
 * Compromis du Sandbox (ni DaemonSet ni hostPath) : en cible, un collecteur par noeud lira stdout.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "management.logging.export.otlp", name = "enabled", havingValue = "true")
public class OpenTelemetryLogbackConfig {

	private static final String APPENDER_NAME = "OTEL";

	@Bean
	InitializingBean openTelemetryLogbackAppenderInstaller(OpenTelemetry openTelemetry) {
		return () -> {
			LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
			Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
			if (root.getAppender(APPENDER_NAME) == null) {
				OpenTelemetryAppender appender = new OpenTelemetryAppender();
				appender.setContext(context);
				appender.setName(APPENDER_NAME);
				appender.start();
				root.addAppender(appender);
			}
			OpenTelemetryAppender.install(openTelemetry);
		};
	}
}
