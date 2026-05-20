package com.camelbytes.sitrep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Sitrep")
@SpringBootApplication
public class SitrepApplication {

  public static void main(String[] args) {
    SpringApplication.run(SitrepApplication.class, args);
  }
}
