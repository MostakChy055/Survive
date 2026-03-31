## Phase C — The JWT Filter

This is the piece that makes protection real. Right now, even though `SecurityConfig` says `.anyRequest().authenticated()`, Spring Security has no way to read the JWT from the request header — it does not know what a JWT is until you teach it. The `JwtAuthFilter` is that teacher.

Here is exactly what happens on every single request:

The critical thing to understand is what "Set auth in context" means. Spring Security uses a thread-local object called the `SecurityContext` to track who made the current request. If you set a valid authentication object in it, Spring Security considers this request authenticated for its entire lifetime — from filter to controller to service. If you do not set it, Spring Security sees an anonymous request and blocks it if the endpoint requires auth.

---

### `JwtAuthFilter.java`

**Purpose:** Intercepts every HTTP request before it reaches your controllers. Reads the JWT access token, validates it, and if valid, populates the Spring Security context so the rest of the request pipeline knows who is making the call.

```java
package com.example.chat.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// OncePerRequestFilter is a Spring base class that guarantees this filter
// runs exactly once per request — even if the request is forwarded internally.
// We extend it instead of implementing Filter directly for this safety guarantee.
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain       // filterChain is the next filter (or the controller)
    ) throws ServletException, IOException {

        // Step 1: Read the Authorization header from the request.
        // A valid header looks like: "Authorization: Bearer eyJhbGci..."
        final String authHeader = request.getHeader("Authorization");

        // Step 2: If there is no Authorization header, or it does not start with "Bearer ",
        // this request is either anonymous or uses a different auth scheme.
        // We do NOT block it here — we simply pass it along unchanged.
        // SecurityConfig will block it later if the endpoint requires authentication.
        // This design means the filter never hard-rejects — it only enriches.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;  // early return — nothing more for this filter to do
        }

        // Step 3: Extract the token by stripping the "Bearer " prefix (7 characters).
        // authHeader = "Bearer eyJhbGci..."
        // token      =         "eyJhbGci..."
        final String token = authHeader.substring(7);

        // Step 4: Try to extract the username from the token.
        // extractUsername() will throw a JwtException if the token is malformed,
        // has an invalid signature, or is expired. We catch that below.
        final String username;
        try {
            username = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            // The token is garbage — corrupted, tampered, or expired.
            // Pass along without setting any authentication.
            // SecurityConfig will block protected endpoints naturally.
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5: Only proceed if we got a username AND the request is not already authenticated.
        // The second check (getAuthentication() == null) prevents overwriting a valid
        // authentication that was set earlier in the chain — defensive programming.
        if (username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            // Step 6: Load the full UserDetails from the database.
            // We need this to get the user's roles/authorities so Spring Security
            // can make authorization decisions downstream.
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Step 7: Ask JwtUtil to fully validate the token against this user.
            // This double-checks the username match and expiry inside one call.
            if (jwtUtil.isAccessTokenValid(token, userDetails.getUsername())) {

                // Step 8: Build an Authentication object.
                // UsernamePasswordAuthenticationToken is Spring Security's standard
                // "this person is authenticated" wrapper.
                // Constructor: (principal, credentials, authorities)
                // - principal   = the UserDetails object (who they are)
                // - credentials = null (we don't need the password after authentication)
                // - authorities = the roles list from UserDetails
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // Step 9: Attach request details (IP address, session info) to the auth token.
                // This is optional but provides useful metadata for security auditing later.
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Step 10: THE KEY STEP.
                // Place this authentication object in the SecurityContext.
                // From this point forward, any code in this request's thread can call
                // SecurityContextHolder.getContext().getAuthentication()
                // and get back this user's identity and roles.
                // This is what makes the request "authenticated" from Spring Security's perspective.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Step 11: Always continue the filter chain, whether we authenticated or not.
        // The filter's job is only to SET authentication — never to BLOCK.
        // Blocking happens in SecurityConfig's authorizeHttpRequests rules.
        filterChain.doFilter(request, response);
    }
}
```

---

### Updated `SecurityConfig.java`

**Purpose of the changes:** We need to register the `JwtAuthFilter` in the security pipeline. Without this registration, the filter exists as a Spring bean but is never called during request processing. We also tighten the endpoint rules slightly.

```java
package com.example.chat.config;

import com.example.chat.security.JwtAuthFilter;
import com.example.chat.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    // JwtAuthFilter is now injected here so we can insert it into the chain
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())

            // This is the critical registration line.
            // addFilterBefore(A, B) means: "run filter A immediately before filter B".
            // UsernamePasswordAuthenticationFilter is the filter Spring Security uses
            // for traditional form-based login. By placing our JWT filter before it,
            // we ensure JWTs are processed first — before Spring tries any other auth mechanism.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
```

---

### `GlobalExceptionHandler.java`

**Purpose:** Right now, if `AuthService` throws a `RuntimeException("Username already taken")`, Spring Boot converts it into a raw 500 Internal Server Error with a stack trace. This handler intercepts those exceptions and returns clean, descriptive JSON responses instead. Every API needs this — it is not optional.

```java
package com.example.chat.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

// @RestControllerAdvice means: watch ALL controllers for exceptions,
// and intercept them before they bubble up to the default Spring error handler.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // BadCredentialsException is thrown by AuthenticationManager when the
    // username or password is wrong. We map it to 401 Unauthorized.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    // Our own RuntimeExceptions from AuthService (duplicate username, invalid token, etc.)
    // We map all of them to 400 Bad Request and forward the message to the client.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // A shared helper that builds a consistent error response shape.
    // Every error from this API looks like:
    // { "status": 400, "error": "...", "timestamp": "..." }
    // Consistency here matters — your Vue.js frontend can handle errors generically.
    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> body = Map.of(
                "status", status.value(),
                "error", message,
                "timestamp", LocalDateTime.now().toString()
        );
        return ResponseEntity.status(status).body(body);
    }
}
```

---

## The complete file structure after Phases A, B, and C

```
src/main/java/com/example/chat/
│
├── config/
│   └── SecurityConfig.java          ← filter chain, BCrypt bean, session policy
│
├── controller/
│   └── AuthController.java          ← /register  /login  /refresh  /logout
│
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── RefreshRequest.java
│   └── AuthResponse.java
│
├── exception/
│   └── GlobalExceptionHandler.java  ← converts exceptions → clean JSON errors
│
├── model/
│   ├── User.java                    ← users table
│   └── RefreshToken.java            ← refresh_tokens table
│
├── repository/
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
│
├── security/
│   ├── JwtUtil.java                 ← generate + validate access tokens
│   ├── JwtAuthFilter.java           ← reads header, populates SecurityContext
│   └── UserDetailsServiceImpl.java  ← loads User → UserDetails for Spring Security
│
└── service/
    └── AuthService.java             ← register / login / refresh / logout logic
```

---

## Testing the full protected flow

With Phase C in place, you can now test end-to-end protection. Try this sequence in Postman:

```
# 1. Register
POST /api/auth/register
Body: { "username": "alice", "email": "alice@test.com", "password": "secret123" }
→ 201, get back accessToken and refreshToken

# 2. Hit a protected endpoint WITHOUT a token
GET /api/test/hello          (you can add a simple @GetMapping for this)
→ 403 Forbidden — filter passes, SecurityConfig blocks it

# 3. Hit a protected endpoint WITH a valid token
GET /api/test/hello
Header: Authorization: Bearer <accessToken from step 1>
→ 200 OK — filter populates SecurityContext, request goes through

# 4. Wait 15 minutes (or temporarily set expiration to 5000ms to test faster)
GET /api/test/hello
Header: Authorization: Bearer <same expired accessToken>
→ 403 Forbidden — filter catches JwtException, skips auth

# 5. Refresh
POST /api/auth/refresh
Body: { "refreshToken": "<UUID from step 1>" }
→ 200, new accessToken, same refreshToken

# 6. Use the new access token
GET /api/test/hello
Header: Authorization: Bearer <new accessToken from step 5>
→ 200 OK again
```

---

Phase D — WebSocket auth — is the final piece. Right now, anyone can open a WebSocket connection even without a token, because the WebSocket handshake is an HTTP upgrade request that bypasses the filter chain. Phase D hooks the JWT check into the STOMP handshake interceptor so only authenticated users can establish a socket connection. Ready to continue?
