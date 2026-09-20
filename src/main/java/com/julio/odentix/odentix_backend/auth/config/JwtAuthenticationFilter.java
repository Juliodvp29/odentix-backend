package com.julio.odentix.odentix_backend.auth.config;

import com.julio.odentix.odentix_backend.auth.dto.AuthenticatedUser;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.JwtValidationResult;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que intercepta cada petición HTTP, extrae el token Bearer del header Authorization,
 * valida en tiempo real la vigencia y estado del usuario en base de datos (FASE1-IMPROVE),
 * sincroniza su rol actual, establece la autenticación en el SecurityContext (FASE1-07)
 * y configura el TenantContext asegurando su limpieza en el bloque finally (FASE1-08).
 * Si el token es inválido, expirado o el usuario está inactivo, registra atributos en el request
 * para que JwtAuthenticationEntryPoint emita la respuesta granular adecuada (RFC 6750).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    try {
      String authHeader = request.getHeader("Authorization");

      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7).trim();

        JwtValidationResult result = jwtService.validateTokenResult(token);

        if (result == JwtValidationResult.VALID) {
          UUID userId = jwtService.extractUserId(token);
          UUID tenantId = jwtService.extractTenantId(token);

          // Validación en tiempo real (Opción B): verificar que el usuario exista en su tenant y esté activo
          Optional<User> userOpt = userRepository.findByIdAndTenantId(userId, tenantId);
          if (userOpt.isPresent() && userOpt.get().isActive()) {
            User userEntity = userOpt.get();
            TenantContext.setTenantId(tenantId);
            // FASE12-04: el tenant viaja también en el MDC para que el layout
            // JSON de prod lo incluya como campo filtrable (nunca datos del tenant).
            MDC.put("tenant_id", tenantId.toString());

            // Sincronización en tiempo real del rol desde la base de datos
            String actualRole = userEntity.getRole().name();
            AuthenticatedUser user = new AuthenticatedUser(userId, tenantId, userEntity.getEmail(), actualRole);

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + actualRole.toUpperCase());
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
          } else {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            request.setAttribute("jwt_auth_error_code", "user_inactive");
            request.setAttribute("jwt_auth_error_message", "El usuario está inactivo o no existe");
          }
        } else if (result == JwtValidationResult.EXPIRED) {
          request.setAttribute("jwt_auth_error_code", "token_expired");
          request.setAttribute("jwt_auth_error_message", "El token de acceso ha expirado");
        } else {
          request.setAttribute("jwt_auth_error_code", "token_invalid");
          request.setAttribute("jwt_auth_error_message", "El token de acceso es inválido o ha sido alterado");
        }
      } else if (authHeader != null) {
        request.setAttribute("jwt_auth_error_code", "token_invalid");
        request.setAttribute("jwt_auth_error_message", "El encabezado Authorization no utiliza el formato Bearer");
      }

      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      // Misma garantía anti-fugas que el contexto: ningún hilo del pool debe
      // conservar el tenant_id de una request anterior en sus logs.
      MDC.clear();
    }
  }
}
