package dev.bravozulu.sitrep.unit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bravozulu.sitrep.auth.internal.SecureTokens;
import org.junit.jupiter.api.Test;

class SecureTokensTest {
  @Test
  void generate_returnsUnpaddedBase64UrlOf32Bytes() {
    assertThat(SecureTokens.generate()).matches("[A-Za-z0-9_-]{43}");
  }

  @Test
  void generate_returnsDistinctTokens() {
    assertThat(SecureTokens.generate()).isNotEqualTo(SecureTokens.generate());
  }

  @Test
  void sha256Hex_matchesKnownVector() {
    assertThat(SecureTokens.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
