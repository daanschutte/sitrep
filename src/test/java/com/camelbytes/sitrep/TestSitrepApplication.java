package com.camelbytes.sitrep;

import org.springframework.boot.SpringApplication;

public class TestSitrepApplication {

  public static void main(String[] args) {
    SpringApplication.from(SitrepApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
