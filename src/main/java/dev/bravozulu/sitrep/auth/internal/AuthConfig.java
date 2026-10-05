package dev.bravozulu.sitrep.auth.internal;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {
  private static final String BCRYPT = "bcrypt";
  private static final int BCRYPT_COST = 12;

  // Built by hand: PasswordEncoderFactories.createDelegatingPasswordEncoder() defaults to cost 10.
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        BCRYPT, Map.of(BCRYPT, new BCryptPasswordEncoder(BCRYPT_COST)));
  }
}
