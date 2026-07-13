package it.zuperman.support_trainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportTrainerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupportTrainerApplication.class, args);
	}
}
