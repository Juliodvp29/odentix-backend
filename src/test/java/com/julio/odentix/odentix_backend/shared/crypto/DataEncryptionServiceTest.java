package com.julio.odentix.odentix_backend.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Pruebas planas del cifrado de secretos por tenant (pre-Fase 12).
 */
class DataEncryptionServiceTest {

  private static final String LLAVE = Base64.getEncoder().encodeToString(new byte[32]);
  private static final String OTRA_LLAVE = Base64.getEncoder().encodeToString(llenar((byte) 7));

  private static byte[] llenar(byte valor) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, valor);
    return bytes;
  }

  @Test
  void roundtripCifraYDescifra() {
    DataEncryptionService servicio = new DataEncryptionService(LLAVE);

    String cifrado = servicio.cifrar("token-secreto-123");
    assertThat(cifrado).isNotBlank();
    assertThat(cifrado).doesNotContain("token-secreto-123");
    assertThat(servicio.descifrar(cifrado)).isEqualTo("token-secreto-123");
  }

  @Test
  void cadaCifradoUsaIvDistinto() {
    DataEncryptionService servicio = new DataEncryptionService(LLAVE);

    assertThat(servicio.cifrar("mismo")).isNotEqualTo(servicio.cifrar("mismo"));
  }

  @Test
  void llaveDistintaNoDescifra() {
    DataEncryptionService servicio = new DataEncryptionService(LLAVE);
    String cifrado = servicio.cifrar("token");

    assertThatThrownBy(() -> new DataEncryptionService(OTRA_LLAVE).descifrar(cifrado))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sinLlaveFallaExplicitoYVacioPasa() {
    DataEncryptionService servicio = new DataEncryptionService("");

    assertThat(servicio.descifrar(null)).isEmpty();
    assertThat(servicio.descifrar("  ")).isEmpty();
    assertThatThrownBy(() -> servicio.cifrar("x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DATA_ENCRYPTION_KEY");
  }
}
