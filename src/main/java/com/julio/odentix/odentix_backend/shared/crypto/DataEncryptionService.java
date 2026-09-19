package com.julio.odentix.odentix_backend.shared.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cifrado simétrico para secretos por tenant (pre-Fase 12: tokens de WhatsApp).
 *
 * <p>AES-256-GCM con IV aleatorio de 12 bytes antepuesto al ciphertext; todo en
 * base64 para columna TEXT. La llave (32 bytes en base64) sale de
 * `DATA_ENCRYPTION_KEY` —nunca del repo ni de la BD. Sin llave configurada el
 * cifrado/descifrado falla explícito (no silencioso): mejor un error claro que
 * un secreto a medio proteger.
 */
@Service
public class DataEncryptionService {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final byte[] key;

  public DataEncryptionService(
      @Value("${odentix.crypto.key:}") String base64Key) {
    byte[] decoded = new byte[0];
    try {
      decoded = base64Key != null && !base64Key.isBlank()
          ? Base64.getDecoder().decode(base64Key.strip())
          : new byte[0];
    } catch (IllegalArgumentException e) {
      decoded = new byte[0];
    }
    this.key = decoded;
  }

  /**
   * Cifra un secreto en base64 listo para BD.
   */
  public String cifrar(String plano) {
    exigirLlave();
    try {
      byte[] iv = new byte[IV_BYTES];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, llave(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] cifrado = cipher.doFinal(plano.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      ByteBuffer buf = ByteBuffer.allocate(iv.length + cifrado.length);
      buf.put(iv);
      buf.put(cifrado);
      return Base64.getEncoder().encodeToString(buf.array());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo cifrar el secreto.", e);
    }
  }

  /**
   * Descifra un valor de `cifrar`. Vacío/nulo entra, vacío sale (sin error).
   */
  public String descifrar(String cifradoB64) {
    if (cifradoB64 == null || cifradoB64.isBlank()) {
      return "";
    }
    exigirLlave();
    try {
      byte[] todo = Base64.getDecoder().decode(cifradoB64.strip());
      byte[] iv = new byte[IV_BYTES];
      byte[] cifrado = new byte[todo.length - IV_BYTES];
      System.arraycopy(todo, 0, iv, 0, IV_BYTES);
      System.arraycopy(todo, IV_BYTES, cifrado, 0, cifrado.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, llave(), new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(cifrado), java.nio.charset.StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      throw new IllegalStateException("No se pudo descifrar (¿llave distinta?).", e);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo descifrar (¿llave distinta?).", e);
    }
  }

  private void exigirLlave() {
    if (key.length != 32) {
      throw new IllegalStateException(
          "Falta configurar DATA_ENCRYPTION_KEY (32 bytes en base64) para secretos por tenant.");
    }
  }

  private SecretKeySpec llave() {
    return new SecretKeySpec(key, "AES");
  }
}
