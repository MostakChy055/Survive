Great choice — getting auth right from the start means you never have to retrofit it later. Let me plan the phases before we write a single line.

---

## What we're building and why JWT

When a user logs in, the server gives them a **token** — a signed string that proves who they are. Every future request (HTTP or WebSocket) carries that token. The server never stores "who is logged in" — it just verifies the token's signature. This is called **stateless auth**, and it's why JWT scales so well.

The token looks like this: `header.payload.signature` — three Base64 strings joined by dots. The payload holds the username and expiry. The signature is a cryptographic proof that *your server* issued it.

---

## The Phases for Auth

**Phase A — The foundation:** User model, password hashing, JWT utility, database layer. No endpoints yet — just the building blocks.

**Phase B — The HTTP endpoints:** `POST /auth/register` and `POST /auth/login`. A user can create an account and get a token back.

**Phase C — Protecting routes:** The JWT filter that reads the token from every request header and tells Spring Security "this request belongs to user X". Locking down endpoints so only authenticated users can reach them.

**Phase D — WebSocket auth:** Hooking the JWT filter into the WebSocket handshake, so unauthenticated users can't even open a socket connection.

Let's do **Phase A** now — thoroughly, line by line.

---

## Phase A — The Foundation

First, the dependencies you need in `build.gradle`:

Here is the flow we are building, so you can see where every file fits before we write it:

Now let's write every file, one at a time.

---

## Step 1 — `build.gradle` dependencies

```groovy
dependencies {

    // Spring Boot core web layer — gives us @RestController, HTTP handling
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Spring Security — the entire auth framework we are plugging JWT into
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // Spring Data JPA — lets us write repositories instead of raw SQL
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // H2 — an in-memory database, perfect for development
    // runtimeOnly means it is only needed when the app is actually running, not at compile time
    runtimeOnly 'com.h2database:h2'

    // jjwt is the standard Java library for creating and parsing JWT tokens
    // We need all three: the API (interfaces), the implementation, and the Jackson binding
    // so the library can serialize/deserialize claims as JSON
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'

    // Lombok — removes boilerplate: @Getter, @Setter, @Builder, @NoArgsConstructor etc.
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

## Step 2 — `application.properties`

**Purpose of this file:** This is where you configure your app without touching Java code. Think of it as the settings panel for your entire application.

```properties
# ─── H2 In-Memory Database ───────────────────────────────────────────────────

# The JDBC URL tells Spring where to find the database.
# "mem:chatdb" means it lives entirely in RAM — it is destroyed when the app stops.
# MODE=PostgreSQL makes H2 behave like Postgres so switching later requires zero SQL changes.
# DB_CLOSE_DELAY=-1 keeps the database alive as long as the JVM is running.
spring.datasource.url=jdbc:h2:mem:chatdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1

spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# Hibernate is the JPA provider. "update" means: look at our entity classes,
# compare them to the actual DB schema, and add any missing tables or columns.
# Never use "update" in production — use "validate" or Flyway migrations instead.
spring.jpa.hibernate.ddl-auto=update

# Makes Hibernate log the actual SQL it runs. Turn this off later — it is noisy.
spring.jpa.show-sql=true

# H2 web console — lets you inspect the in-memory database at http://localhost:8080/h2-console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# ─── JWT Configuration ────────────────────────────────────────────────────────

# This secret is used to sign tokens. It MUST be long (256+ bits) and random.
# In production, this comes from an environment variable, NEVER committed to git.
# The value below is just for development.
app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970

# How long a token lives before it expires. 86400000 ms = 24 hours.
app.jwt.expiration=86400000
```

---

## Step 3 — `User.java` (the entity)

**Purpose of this file:** This Java class IS your database table. JPA reads the annotations and creates a `users` table with exactly these columns. Every instance of this class represents one row.

```java
// This package path mirrors your folder structure
package com.example.chat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// @Entity tells JPA: "this class maps to a database table"
// Without this annotation, JPA completely ignores the class
@Entity

// @Table lets us name the table explicitly.
// Without it, JPA uses the class name — which would also be "user",
// but "user" is a reserved keyword in PostgreSQL, so we name it explicitly.
@Table(name = "users")

// Lombok: @Data generates getters, setters, equals, hashCode, and toString
// @Builder generates a builder pattern: User.builder().username("alice").build()
// @NoArgsConstructor and @AllArgsConstructor generate the two constructors JPA needs
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // @Id marks this field as the primary key
    // @GeneratedValue tells JPA to auto-assign IDs — IDENTITY means the DB handles it
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column with unique=true creates a UNIQUE constraint in the DB.
    // nullable=false means this column cannot be NULL — enforced at DB level.
    // Spring validation (later) adds a second layer of enforcement before the DB is even hit.
    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    // This stores the BCrypt hash of the password, NOT the plain-text password.
    // A BCrypt hash looks like: $2a$10$abcdefghijklmnopqrstuuABC...
    // It is physically impossible to reverse it back to the original password.
    @Column(nullable = false)
    private String password;

    // The role determines what the user is allowed to do.
    // For now, everyone is ROLE_USER. Later, admins get ROLE_ADMIN.
    // Spring Security reads this to make authorization decisions.
    @Column(nullable = false)
    private String role;
}
```

---

## Step 4 — `UserRepository.java`

**Purpose of this file:** This is your entire database access layer for users. Spring Data JPA generates all the SQL for you — you never write `SELECT * FROM users WHERE...` by hand.

```java
package com.example.chat.repository;

import com.example.chat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// @Repository marks this as a Spring-managed bean.
// JpaRepository<User, Long> means: "this repository manages User objects, whose ID type is Long"
// By extending JpaRepository, we automatically get: save(), findById(), findAll(),
// delete(), count(), and many more — all implemented by Spring, zero SQL written.
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // This is a "derived query method". Spring reads the method name and generates SQL:
    // SELECT * FROM users WHERE username = ?
    // Optional<> is used instead of User because the user might not exist —
    // it forces the caller to handle the "not found" case explicitly, preventing NullPointerExceptions
    Optional<User> findByUsername(String username);

    // Spring generates: SELECT * FROM users WHERE email = ?
    // Used during registration to check if the email is already taken
    Optional<User> findByEmail(String email);

    // Spring generates: SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END FROM users WHERE username = ?
    // Cleaner than findByUsername().isPresent() when you only care about existence
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
```

---

## Step 5 — `JwtUtil.java`

**Purpose of this file:** This is the brain of your JWT system. It does exactly two things: create tokens when a user logs in, and verify tokens when a user makes a request. Every other class delegates to this one for anything JWT-related.

```java
package com.example.chat.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

// @Component registers this class as a Spring bean so other classes can @Autowire it
@Component
public class JwtUtil {

    // @Value reads the value from application.properties at startup.
    // The ${...} syntax tells Spring: "look up this key and inject its value here"
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    // This method converts our raw secret string into a cryptographic Key object.
    // Keys.hmacShaKeyFor() ensures the key is long enough for HMAC-SHA256 signing.
    // It is private because nothing outside this class should ever touch the raw key.
    private Key getSigningKey() {
        // HexFormat.of().parseHex() converts our hex string from application.properties
        // into the raw bytes that the cryptographic library needs
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Called once when a user successfully logs in.
    // username is embedded inside the token as the "subject" claim.
    // Anyone who has this token can prove they are that username — until it expires.
    public String generateToken(String username) {
        return Jwts.builder()
                // setSubject stores the username inside the token payload
                .setSubject(username)
                // setIssuedAt records when the token was created
                .setIssuedAt(new Date())
                // setExpiration sets when the token stops being valid
                // new Date(System.currentTimeMillis() + jwtExpirationMs) = now + 24 hours
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                // signWith signs the token with our secret key using HMAC-SHA256
                // This signature is what makes the token tamper-proof:
                // if anyone changes even one character of the payload, the signature becomes invalid
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                // compact() serializes everything into the final "header.payload.signature" string
                .compact();
    }

    // Called on every incoming request to extract who the token belongs to.
    // The username is then used to load the full user from the database.
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                // We must provide the same key that was used to sign the token
                // so the library can verify the signature
                .setSigningKey(getSigningKey())
                .build()
                // parseClaimsJws() does three things at once:
                // 1. Verifies the signature (throws if tampered)
                // 2. Checks the expiration date (throws if expired)
                // 3. Returns the parsed claims object
                .parseClaimsJws(token)
                .getBody()
                // getSubject() returns the username we stored in generateToken()
                .getSubject();
    }

    // Called before trusting a token. Returns true only if:
    // 1. The signature is valid (proves we issued it)
    // 2. The token has not expired
    // 3. The username inside the token matches the user we loaded from DB
    public boolean isTokenValid(String token, String username) {
        try {
            String extractedUsername = extractUsername(token);
            // Check: does the token belong to this specific user, AND is it not expired?
            return extractedUsername.equals(username) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException covers: invalid signature, malformed token, expired token
            // We catch all of these and simply return false — no crash, no information leak
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        // getExpiration() returns the Date we set in generateToken()
        // .before(new Date()) means: is the expiration date in the past?
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
    }
}
```

---

## Step 6 — `UserDetailsServiceImpl.java`

**Purpose of this file:** Spring Security does not know what a "User" is — it only understands its own interface called `UserDetails`. This class is the translator. It loads your `User` from the database and wraps it in a `UserDetails` object that Spring Security can work with.

```java
package com.example.chat.security;

import com.example.chat.model.User;
import com.example.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

// @RequiredArgsConstructor (Lombok) generates a constructor that injects all final fields.
// This is the recommended way to do dependency injection — cleaner than @Autowired on fields.
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // final means Spring MUST inject this — it cannot be null
    private final UserRepository userRepository;

    // Spring Security calls this method automatically whenever it needs to verify
    // who a request belongs to. The username comes from the JWT token we extracted.
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // We look up the user in the database by username
        User user = userRepository.findByUsername(username)
                // If the user does not exist, throw UsernameNotFoundException.
                // Spring Security catches this and returns a 401 Unauthorized response.
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username
                ));

        // Spring Security's own User class (different from our User entity!) implements UserDetails.
        // We use it as a convenient wrapper. We pass it:
        // 1. The username
        // 2. The BCrypt-hashed password (Spring Security will compare this against what the user sends)
        // 3. The list of "authorities" (roles) — SimpleGrantedAuthority wraps our "ROLE_USER" string
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole()))
        );
    }
}
```

---

## Step 7 — `SecurityConfig.java`

**Purpose of this file:** This is the gatekeeper of your entire application. It defines which URLs are public, which require a logged-in user, how passwords are hashed, and wires all the security components together. Every security decision flows through here.

```java
package com.example.chat.config;

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

// @Configuration tells Spring: "this class produces beans — read its @Bean methods"
// @EnableWebSecurity activates Spring Security's web security support
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    // @Bean means: "Spring, manage this object in your container so others can inject it"
    // BCryptPasswordEncoder is the industry-standard way to hash passwords.
    // BCrypt is deliberately slow (takes ~100ms) to make brute-force attacks impractical.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationProvider is the component that actually checks "is this password correct?"
    // DaoAuthenticationProvider uses our UserDetailsService to load the user from the DB,
    // then uses the PasswordEncoder to compare the submitted password against the stored hash.
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        // Tell it which service to use to load users
        provider.setUserDetailsService(userDetailsService);
        // Tell it which encoder to use to compare passwords
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // AuthenticationManager is what your AuthController will call when a user tries to log in.
    // Spring creates this from the configuration automatically — we just expose it as a bean.
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // SecurityFilterChain is the main security pipeline.
    // Every HTTP request passes through this chain of rules.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (Cross-Site Request Forgery) protection.
            // CSRF is a browser-based attack. Since our API uses JWT tokens in headers
            // (not browser cookies), CSRF is not applicable here.
            .csrf(csrf -> csrf.disable())

            // Define which URLs require authentication and which are public
            .authorizeHttpRequests(auth -> auth
                // These endpoints must be public — users cannot log in if login requires auth!
                .requestMatchers("/api/auth/**").permitAll()
                // The H2 console also needs to be public during development
                .requestMatchers("/h2-console/**").permitAll()
                // Every other request requires the user to be authenticated
                .anyRequest().authenticated()
            )

            // STATELESS means: do NOT create HTTP sessions.
            // Instead of a session cookie, every request must carry its JWT token.
            // This is what makes JWT auth "stateless" — the server remembers nothing between requests.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Register our custom AuthenticationProvider
            .authenticationProvider(authenticationProvider())

            // H2 console uses iframes. Spring Security blocks iframes by default.
            // This disables that block so we can use the console in development.
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
```

---

Phase A is now complete. You have the entire foundation: the database layer, the password hashing, the JWT engine, and the security configuration wired together — but no HTTP endpoints yet.

Here is what Phase B (the next step) will build on top of this: an `AuthController` with `POST /api/auth/register` and `POST /api/auth/login`, plus the DTOs that carry the request and response data. Then Phase C adds the `JwtAuthFilter` that intercepts every request and reads the token from the `Authorization` header.

Ready to move to Phase B? Before we do — do you want me to clarify anything about Phase A? The `UserDetailsServiceImpl` and `SecurityConfig` interaction is the part most people find confusing at first.
