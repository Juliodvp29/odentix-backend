package com.julio.odentix.odentix_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OdentixBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(OdentixBackendApplication.class, args);
	}

}
