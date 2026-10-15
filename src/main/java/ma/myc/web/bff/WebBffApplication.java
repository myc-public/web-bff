package ma.myc.web.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WebBffApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebBffApplication.class, args);
	}
}
