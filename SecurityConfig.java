package com.chatroom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — Configures Spring Security for our chatroom.
 *
 * Spring Security, by default, locks down EVERYTHING.
 * Every endpoint requires authentication, CSRF is enabled (which breaks WebSocket),
 * and H2 console is blocked.
 *
 * This class overrides those defaults to:
 *   1. Define which endpoints are public vs protected
 *   2. Configure CORS so our Vue.js dev server (port 5173) can call us (port 8080)
 *   3. Disable CSRF for WebSocket (STOMP over WebSocket doesn't support CSRF tokens)
 *   4. Allow H2 console (it uses iframes, which Spring Security blocks by default)
 *   5. Define test users (in-memory, no DB required for this learning project)
 *   6. Configure BCrypt for password hashing
 *
 * @Configuration  — Makes this a Spring Bean configuration class
 * @EnableWebSecurity — Activates Spring Security's web security support
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * securityFilterChain() — The main security rulebook.
     *
     * Spring Security works as a chain of servlet filters.
     * Every HTTP request passes through this filter chain.
     * The order of rules matters — more specific rules go first.
     *
     * @Bean — Registers this as a Spring-managed Bean so Spring Security picks it up.
     * @param http — Spring injects this builder, we chain configuration methods on it.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http

            /**
             * CORS Configuration
             *
             * CORS = Cross-Origin Resource Sharing.
             * Our Vue.js app runs on http://localhost:5173.
             * Our Spring Boot runs on http://localhost:8080.
             * They are different origins (different ports = different origin per RFC 6454).
             *
             * Without CORS config, the browser blocks Vue.js from calling Spring Boot.
             *
             * .cors(cors -> cors.configurationSource(corsConfigurationSource()))
             * tells Spring Security to apply our CORS rules (defined below)
             * BEFORE checking authentication. This is important — browsers send
             * a preflight OPTIONS request before the actual request, and it must
             * be allowed even without credentials.
             */
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /**
             * CSRF Disable
             *
             * CSRF = Cross-Site Request Forgery.
             * Spring Security protects against CSRF by requiring a hidden token
             * in every state-changing request (POST, PUT, DELETE).
             *
             * WHY we disable it for WebSocket:
             *   - STOMP messages are sent over the WebSocket connection, not as HTTP forms.
             *   - There's no standard way to embed CSRF tokens in STOMP frames.
             *   - The WebSocket handshake itself IS protected by the Origin check in WebSocketConfig.
             *
             * NOTE: For a production app with traditional form submissions,
             * you'd keep CSRF enabled for HTTP endpoints and only disable for WS.
             * For this learning project, we disable globally for simplicity.
             */
            .csrf(csrf -> csrf.disable())

            /**
             * Frame Options — Allow H2 Console
             *
             * H2's web console renders inside an HTML <iframe>.
             * Spring Security's default X-Frame-Options: DENY header blocks iframes.
             * We override it to SAMEORIGIN (allow iframes from the same origin only),
             * which allows the H2 console to load at http://localhost:8080/h2-console.
             */
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )

            /**
             * Authorization Rules
             *
             * These rules decide which endpoints are public and which need a login.
             * Rules are evaluated in order — the FIRST match wins.
             */
            .authorizeHttpRequests(auth -> auth

                /**
                 * /api/auth/** → Public
                 * Login endpoint must be public, otherwise users can never log in!
                 * permitAll() = no authentication required
                 */
                .requestMatchers("/api/auth/**").permitAll()

                /**
                 * /ws/** → Public at HTTP level
                 * The WebSocket handshake starts as an HTTP GET request to /ws.
                 * We allow it through here — the actual STOMP-level auth
                 * is handled by the ChannelInterceptor in WebSocketConfig.
                 * (In this simplified version, we rely on session-based auth.)
                 */
                .requestMatchers("/ws/**").permitAll()

                /**
                 * /h2-console/** → Public
                 * Only enabled in dev. The H2 console needs to be accessible
                 * for database inspection during learning.
                 */
                .requestMatchers("/h2-console/**").permitAll()

                /**
                 * Everything else → Requires authentication
                 * Any other endpoint (like /api/messages) requires the user to be logged in.
                 * If not authenticated, Spring returns 401 Unauthorized.
                 */
                .anyRequest().authenticated()
            )

            /**
             * Form Login
             *
             * Enables Spring's built-in session-based authentication.
             * POST /login with username + password creates a session cookie (JSESSIONID).
             * Subsequent requests include this cookie, so Spring knows who you are.
             *
             * We configure a custom login URL (/api/auth/login) that matches
             * what our Vue.js frontend will call.
             *
             * successHandler / failureHandler return JSON instead of HTML redirects,
             * since our Vue.js app expects JSON responses, not HTML form pages.
             *
             * permitAll() — the /api/auth/login endpoint itself must be public.
             */
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> {
                    // On successful login, return 200 OK with JSON user info
                    response.setContentType("application/json");
                    response.setStatus(200);
                    response.getWriter().write(
                        "{\"success\": true, \"username\": \"" +
                        authentication.getName() + "\"}"
                    );
                })
                .failureHandler((request, response, exception) -> {
                    // On failed login, return 401 Unauthorized with JSON error
                    response.setContentType("application/json");
                    response.setStatus(401);
                    response.getWriter().write(
                        "{\"success\": false, \"message\": \"Invalid username or password\"}"
                    );
                })
                .permitAll()
            )

            /**
             * Logout
             *
             * Enables /api/auth/logout (POST).
             * Spring invalidates the session and clears the JSESSIONID cookie.
             * We return JSON (not a redirect to a login page).
             */
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setContentType("application/json");
                    response.setStatus(200);
                    response.getWriter().write("{\"success\": true}");
                })
                .permitAll()
            );

        return http.build();
    }

    /**
     * corsConfigurationSource() — Defines the detailed CORS rules.
     *
     * CORS works by: browser sends Origin header, server responds with
     * Access-Control-Allow-* headers. If they match, the browser allows the response.
     *
     * @Bean — Spring registers this so it can be used by the security filter chain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        /**
         * setAllowedOrigins — Which frontend origins can call our backend?
         *
         * We allow:
         *   - http://localhost:5173 (Vite dev server default port)
         *   - http://localhost:3000 (alternative dev server port)
         *
         * In production, replace with your actual domain, e.g.:
         *   config.setAllowedOrigins(List.of("https://mychatapp.com"));
         */
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://localhost:3000"
        ));

        /**
         * setAllowedMethods — Which HTTP methods are allowed cross-origin?
         * We need GET (fetch data), POST (send messages/login), OPTIONS (preflight).
         */
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        /**
         * setAllowedHeaders — Which request headers can the frontend send?
         * "*" means any header is allowed.
         * Important headers include: Content-Type, Authorization, X-Requested-With.
         */
        config.setAllowedHeaders(List.of("*"));

        /**
         * setAllowCredentials(true) — CRITICAL for session cookies.
         *
         * This tells the browser: "Yes, you may send cookies (JSESSIONID)
         * along with cross-origin requests."
         *
         * Without this: after login, Vue.js wouldn't send the session cookie
         * to Spring Boot, so every request would appear unauthenticated.
         *
         * IMPORTANT: When allowCredentials is true, you cannot use "*" for
         * allowedOrigins — you must specify exact origins (which we do above).
         */
        config.setAllowCredentials(true);

        // Apply this CORS configuration to all URL patterns
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * userDetailsService() — Defines who can log in.
     *
     * In a real app, you'd load users from a database via a UserRepository.
     * For this learning project, we use InMemoryUserDetailsManager —
     * users are hardcoded in memory.
     *
     * We define 3 test users: alice, bob, and charlie.
     *
     * @Bean — Spring Security uses this bean to look up users during authentication.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        /**
         * User.withUsername("alice")
         *   .password(passwordEncoder().encode("password"))  ← BCrypt hash
         *   .roles("USER")   ← assigns ROLE_USER authority
         *   .build()
         *
         * The password is stored as a BCrypt hash, never plain text.
         * BCrypt is the industry-standard algorithm for password hashing.
         * See: OWASP Password Storage Cheat Sheet.
         */
        var alice = User.withUsername("alice")
            .password(passwordEncoder().encode("password"))
            .roles("USER")
            .build();

        var bob = User.withUsername("bob")
            .password(passwordEncoder().encode("password"))
            .roles("USER")
            .build();

        var charlie = User.withUsername("charlie")
            .password(passwordEncoder().encode("password"))
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(alice, bob, charlie);
    }

    /**
     * passwordEncoder() — Configures BCrypt password hashing.
     *
     * BCryptPasswordEncoder uses the BCrypt hashing function (created by
     * Niels Provos and David Mazières, 1999). It is:
     *   - Adaptive (work factor can be increased as hardware gets faster)
     *   - Salted (each hash includes a random salt, so same password → different hash)
     *   - Slow by design (makes brute-force attacks impractical)
     *
     * Default strength is 10 (2^10 = 1024 iterations).
     * Increase to 12 or 14 for production (at the cost of login speed).
     *
     * @Bean — Used by userDetailsService() above and by the authentication manager.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * authenticationManager() — Exposes Spring's built-in authentication manager.
     *
     * The AuthenticationManager is the core component that validates
     * username/password credentials. Spring auto-configures one for us,
     * but we expose it as a @Bean so we can inject it elsewhere
     * (e.g., if we build a custom login controller later).
     */
    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
