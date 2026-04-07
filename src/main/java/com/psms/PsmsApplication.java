package com.psms;

import com.psms.config.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileStorageProperties.class)
public class PsmsApplication {

	public static void main(String[] args) {
		SpringApplication.run(PsmsApplication.class, args);
	}

}
