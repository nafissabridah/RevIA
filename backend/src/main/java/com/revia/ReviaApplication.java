package com.revia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * RevIA - votre assistant intelligent de revue de code.
 * Point d'entrée de l'API REST Spring Boot.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ReviaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviaApplication.class, args);
    }
}
