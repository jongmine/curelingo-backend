package com.curelingo.curelingo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.curelingo.curelingo.publicdata.mysql.egen.EgenProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(EgenProperties.class)
public class CurelingoApplication {

	public static void main(String[] args) {
		SpringApplication.run(CurelingoApplication.class, args);
	}

}
