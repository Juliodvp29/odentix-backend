package com.julio.odentix.odentix_backend.auth.controller;

import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import com.julio.odentix.odentix_backend.auth.dto.LoginResponse;
import com.julio.odentix.odentix_backend.auth.dto.LogoutRequest;
import com.julio.odentix.odentix_backend.auth.dto.TokenRefreshRequest;
import com.julio.odentix.odentix_backend.auth.dto.TokenRefreshResponse;
import com.julio.odentix.odentix_backend.auth.exception.AccountTemporarilyLockedException;
import com.julio.odentix.odentix_backend.auth.exception.RateLimitExceededException;
import com.julio.odentix.odentix_backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de autenticación (FASE1-06 / FASE1-IMPROVE).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Login público con rate limiting, renovación de tokens y cierre de sesión.")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  @Operation(summary = "Autenticarse con email y contraseña (devuelve JWT y refresh token)")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest) {
    String clientIp = extractClientIp(httpRequest);
    LoginResponse response = authService.login(request, clientIp);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/refresh")
  @Operation(summary = "Renovar access token mediante refresh token con rotación atómica")
  public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
    TokenRefreshResponse response = authService.refreshToken(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  @Operation(summary = "Cerrar sesión revocando el refresh token")
  public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(BadCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Map<String, String> handleBadCredentials(BadCredentialsException ex) {
    return Map.of("error", ex.getMessage());
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(Map.of(
            "error", "Límite de peticiones excedido",
            "message", ex.getMessage(),
            "retryAfterSeconds", ex.getRetryAfterSeconds()
        ));
  }

  @ExceptionHandler(AccountTemporarilyLockedException.class)
  public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountTemporarilyLockedException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(Map.of(
            "error", "Cuenta temporalmente bloqueada",
            "message", ex.getMessage(),
            "retryAfterSeconds", ex.getRetryAfterSeconds()
        ));
  }

  private String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
  }
}
