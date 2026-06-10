package com.scorestv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Auth tamamen JWT tabanli; Spring'in varsayilan bellek-ici kullanici/sifre
// uretimini (UserDetailsServiceAutoConfiguration) devre disi birakiyoruz.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class ScorestvApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScorestvApplication.class, args);
	}
}
