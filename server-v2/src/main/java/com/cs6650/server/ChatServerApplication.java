package com.cs6650.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChatServerApplication {
  public static void main(String[] args) {
    SpringApplication.run(ChatServerApplication.class, args);
  }
}
