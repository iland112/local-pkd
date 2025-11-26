package com.smartcoreinc.localpkd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan("com.smartcoreinc.localpkd")
@EnableJpaRepositories(basePackages = "com.smartcoreinc.localpkd")
public class LocalPkdEvaluationProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocalPkdEvaluationProjectApplication.class, args);
	}

}
