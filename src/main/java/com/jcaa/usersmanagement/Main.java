package com.jcaa.usersmanagement;

import com.jcaa.usersmanagement.infrastructure.config.KafkaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KafkaProperties.class)
public class Main {

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}