You are absolutely right to catch that. Let me clarify before we write a single line of Phase B.

---

## Access token vs Refresh token

You are thinking of **access token** and **refresh token** — and yes, Phase A only implemented one token doing both jobs, which is a real architectural weakness.

Here is why they exist as two separate things:

An **access token** is short-lived (15–60 minutes). It is sent with every single request. Because it travels over the network so frequently, there is always a small risk it gets stolen — from a log, a compromised client, a man-in-the-middle. The short expiry limits the damage window: a stolen access token becomes useless within minutes.

A **refresh token** is long-lived (7–30 days). It is sent exactly once — only when the access token expires and the client needs a new one. Because it travels rarely, it is far less exposed. It is also stored in the database, which means you can revoke it server-side. If a user logs out, or you detect suspicious activity, you delete the refresh token and the attacker is locked out on their next refresh attempt. You cannot do this with a pure stateless access token.

The practical flow looks like this:

The key insight is: the access token never touches the database. It is purely cryptographic — verified by the signature alone. The refresh token lives in the database, which is what gives you the power to revoke it.

So yes — Phase A's `JwtUtil` only generates one kind of token. We are going to fix that right now in Phase B by building both properly from the start. Here is what changes:

In `application.properties` we will add two expiry values instead of one. `JwtUtil` will gain a second `generateRefreshToken()` method. We will add a `RefreshToken` entity and repository so refresh tokens persist in the database. And the `AuthController` will return both tokens on login, and expose a `/auth/refresh` endpoint.

---

## Phase B — HTTP Endpoints + Proper Two-Token Auth

### `application.properties` additions

Add these two lines, replacing the single expiration from Phase A:

```properties
# Access token — short lived, stateless, never stored in DB
app.jwt.access-token.expiration=900000

# Refresh token — long lived, stored in DB, revocable
app.jwt.refresh-token.expiration=604800000
```

---

### `RefreshToken.java` (new entity)

**Purpose:** This table stores every active refresh token. One row = one logged-in session. When a user logs out, their row is deleted. This is the only stateful part of your auth system.

```java
package com.example.chat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which user does this session belong to?
    // FetchType.LAZY means: don't load the User object from DB until it is actually accessed.
    // This avoids unnecessary DB queries when you only need the token value.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The actual token string stored in DB — a randomly generated UUID, NOT a JWT.
    // We use a random UUID because refresh tokens do not need to carry claims.
    // Their only job is to be an unforgeable key that maps back to a user in our DB.
    @Column(nullable = false, unique = true)
    private String token;

    // When this refresh token stops being valid.
    // Instant is used instead of Date because it is timezone-agnostic
    // and works cleanly with Java's modern time API.
    @Column(nullable = false)
    private Instant expiresAt;
}
```

---

### `RefreshTokenRepository.java`

**Purpose:** Database access for refresh tokens. The key operation is "find a session by token string" — used when the client asks for a new access token.

```java
package com.example.chat.repository;

import com.example.chat.model.RefreshToken;
import com.example.chat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Used during token refresh: client sends the token string,
    // we look it up to find the associated user and check the expiry.
    Optional<RefreshToken> findByToken(String token);

    // Used during logout: delete this user's session from the DB.
    // The int return value is the number of rows deleted (0 or 1).
    int deleteByUser(User user);

    // Used if you later want to enforce one-session-per-user:
    // delete all existing sessions before creating a new one on login.
    // For now we allow multiple sessions (multiple devices).
    int deleteAllByUser(User user);
}
```

---

### Updated `JwtUtil.java`

**Purpose:** Now generates two distinct token types with separate expiry durations. The refresh token is a simple UUID — not a JWT — because it does not need to carry any data. Its only job is to be a random, unguessable key.

```java
package com.example.chat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Now we have two separate expiry durations — one per token type
    @Value("${app.jwt.access-token.expiration}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token.expiration}")
    private long refreshTokenExpirationMs;

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Generates a SHORT-LIVED JWT access token (15 minutes).
    // This is what gets sent in the Authorization header with every API request.
    public String generateAccessToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                // We add a custom claim "type" so we can reject refresh tokens
                // being used as access tokens (a security hardening measure for later)
                .claim("type", "access")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Generates a random UUID string that will be stored in the DB as a refresh token.
    // UUID.randomUUID() produces a cryptographically random 128-bit value —
    // impossible to guess, impossible to brute-force.
    // We return the Instant expiry alongside it so the service can store both together.
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString();
    }

    // Convenience method: the service asks "when should this refresh token expire?"
    public Instant calculateRefreshTokenExpiry() {
        return Instant.now().plusMillis(refreshTokenExpirationMs);
    }

    // Extracts the username from an access token.
    // Called by the JWT filter on every incoming request.
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // Validates an access token — checks signature and expiry.
    public boolean isAccessTokenValid(String token, String username) {
        try {
            String extracted = extractUsername(token);
            return extracted.equals(username) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration()
                .before(new Date());
    }
}
```

---

### The DTOs

**Purpose of DTOs (Data Transfer Objects):** These are simple classes that define the exact shape of the JSON going in and out of your API. They act as a contract — the frontend knows exactly what fields to send and what to expect back. You never expose your `User` entity directly because it contains the password hash.

```java
// ─── RegisterRequest.java ─────────────────────────────────────────────────────
// The body the client sends to POST /api/auth/register
package com.example.chat.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    // @Data generates getters/setters — Jackson (the JSON library) uses
    // these getters/setters to deserialize the incoming JSON into this object
    private String username;
    private String email;
    private String password;  // plain-text here — we hash it in the service, never before
}
```

```java
// ─── LoginRequest.java ────────────────────────────────────────────────────────
// The body the client sends to POST /api/auth/login
package com.example.chat.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;  // plain-text — Spring Security will compare it against the BCrypt hash
}
```

```java
// ─── AuthResponse.java ────────────────────────────────────────────────────────
// What the server sends BACK after successful register or login.
// The client must store both tokens — access token in memory, refresh token in localStorage.
package com.example.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    // Short-lived JWT — goes in the Authorization header of every request
    private String accessToken;

    // Long-lived UUID — stored by the client, sent only to /auth/refresh
    private String refreshToken;

    // Useful for the frontend to know who just logged in without decoding the JWT
    private String username;

    // The "Bearer" prefix is a convention — it tells the server
    // the auth scheme being used. Always "Bearer" for JWT.
    private String tokenType = "Bearer";
}
```

```java
// ─── RefreshRequest.java ──────────────────────────────────────────────────────
// The body the client sends to POST /api/auth/refresh
package com.example.chat.dto;

import lombok.Data;

@Data
public class RefreshRequest {
    // The client sends back the refresh token UUID it stored at login
    private String refreshToken;
}
```

---

### `AuthService.java`

**Purpose:** Contains all the business logic for auth. The controller stays thin and just delegates to this service. This separation means if you ever change your auth logic, you change it in one place.

```java
package com.example.chat.service;

import com.example.chat.dto.*;
import com.example.chat.model.RefreshToken;
import com.example.chat.model.User;
import com.example.chat.repository.RefreshTokenRepository;
import com.example.chat.repository.UserRepository;
import com.example.chat.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    // @Transactional means: if anything inside this method throws an exception,
    // ALL database changes made so far in this method are rolled back automatically.
    // So if saving the refresh token fails, the user record also gets undone — no orphan data.
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Validate uniqueness before attempting to save.
        // This gives us a clean, descriptive error instead of a raw DB constraint violation.
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // Build and save the new user.
        // passwordEncoder.encode() hashes the plain-text password with BCrypt.
        // From this point on, the plain-text password is GONE — even we cannot recover it.
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .build();

        userRepository.save(user);

        // After registration, immediately issue both tokens so the user is logged in.
        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        // AuthenticationManager does the heavy lifting here.
        // It calls UserDetailsService.loadUserByUsername(), then uses PasswordEncoder
        // to compare the submitted password against the stored BCrypt hash.
        // If the credentials are wrong, it throws BadCredentialsException automatically.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // If we reach here, authentication passed — the credentials are valid.
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Delete any existing refresh tokens for this user before issuing a new one.
        // This enforces a single active session per user. Remove this line
        // if you want to support multiple devices simultaneously.
        refreshTokenRepository.deleteAllByUser(user);

        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {

        // Look up the refresh token in the database.
        // If it does not exist, the client sent an invalid or already-revoked token.
        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Check if the refresh token itself has expired.
        // Even though it is in the DB, it might have outlived its 7-day window.
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            // Clean up the expired token from the DB
            refreshTokenRepository.delete(stored);
            throw new RuntimeException("Refresh token expired. Please log in again.");
        }

        // The refresh token is valid — issue a fresh access token.
        // We do NOT issue a new refresh token here (that would be "refresh token rotation").
        // Rotation is more secure but more complex. We will add it later.
        String newAccessToken = jwtUtil.generateAccessToken(stored.getUser().getUsername());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(stored.getToken())  // the same refresh token continues
                .username(stored.getUser().getUsername())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        // Find the refresh token and delete it.
        // Once deleted, the next /auth/refresh call with this token will return an error,
        // effectively ending the session even if the access token has not expired yet.
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(refreshTokenRepository::delete);
    }

    // Private helper used by both register() and login() to avoid repeating
    // the token-creation logic. Both operations end with the same result:
    // a valid access token + a valid refresh token returned to the client.
    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getUsername());

        // Create and persist the refresh token entity
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(jwtUtil.generateRefreshTokenValue())
                .expiresAt(jwtUtil.calculateRefreshTokenExpiry())
                .build();

        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .build();
    }
}
```

---

### `AuthController.java`

**Purpose:** The HTTP layer. Maps URLs to service methods. Stays deliberately thin — no business logic lives here, only request/response handling.

```java
package com.example.chat.controller;

import com.example.chat.dto.*;
import com.example.chat.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// @RestController = @Controller + @ResponseBody
// Every method return value is automatically serialized to JSON
@RestController
// All endpoints in this class are prefixed with /api/auth
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/register
    // @RequestBody tells Spring to deserialize the incoming JSON into a RegisterRequest object
    // ResponseEntity lets us control the HTTP status code explicitly
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        // 201 Created is the correct status for a resource being created,
        // as opposed to 200 OK which implies something was just retrieved
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);  // 200 OK
    }

    // POST /api/auth/refresh
    // The client sends its refresh token and gets a new access token back
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    // POST /api/auth/logout
    // The client sends its refresh token so we can delete it from the DB
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        // 204 No Content is the correct status when an operation succeeded but there is nothing to return
        return ResponseEntity.noContent().build();
    }
}
```

---

## What you can test right now

Start the application and use any HTTP client (Postman, curl, or the H2 console). With these three requests you can verify the full cycle:

```
POST /api/auth/register
{ "username": "alice", "email": "alice@test.com", "password": "secret123" }
→ returns accessToken + refreshToken

POST /api/auth/login
{ "username": "alice", "password": "secret123" }
→ returns new accessToken + refreshToken

POST /api/auth/refresh
{ "refreshToken": "<the UUID from login>" }
→ returns new accessToken, same refreshToken
```

---

Phase B is complete. The next phase, Phase C, is where the two tokens start working together in real requests — we build the `JwtAuthFilter` that intercepts every incoming HTTP request, reads the access token from the `Authorization: Bearer <token>` header, validates it, and tells Spring Security "this request belongs to user X." Without that filter, the protected endpoints we set up in `SecurityConfig` cannot actually verify who is calling them.

Ready for Phase C?
