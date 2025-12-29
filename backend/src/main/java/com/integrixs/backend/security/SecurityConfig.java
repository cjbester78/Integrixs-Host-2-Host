package com.integrixs.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security Configuration for H2H Application
 * Configures JWT-based authentication with role-based access control
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                         UserDetailsService userDetailsService) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Password encoder bean using BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength 12 for good security
    }

    /**
     * Authentication provider bean
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Authentication manager bean
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Main security filter chain configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            // Disable CSRF for API (using JWT tokens)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Configure session management (stateless for JWT)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure authentication entry point
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // SPA routes (allow all frontend routes without authentication)
                .requestMatchers(
                    "/login/**", "/login",
                    "/dashboard/**", "/dashboard",
                    "/interfaces/**", "/interfaces",
                    "/executions/**", "/executions",
                    "/ssh-keys/**", "/ssh-keys",
                    "/pgp-keys/**", "/pgp-keys",
                    "/files/**", "/files",
                    "/monitoring/**", "/monitoring",
                    "/settings/**", "/settings",
                    "/admin/**", "/admin"
                ).permitAll()
                
                // Public API endpoints (no authentication required)
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/").permitAll()
                
                // Static resources and frontend assets
                .requestMatchers(
                    "/index.html", "/favicon.ico", "/robots.txt",
                    "/static/**", "/assets/**",
                    "/*.js", "/*.css", "/*.svg", "/*.png", "/*.jpg", "/*.woff2", "/*.ttf"
                ).permitAll()
                
                // Admin-only endpoints
                .requestMatchers(HttpMethod.POST, "/api/users/**").hasAuthority("ADMINISTRATOR")
                .requestMatchers(HttpMethod.PUT, "/api/users/**").hasAuthority("ADMINISTRATOR")
                .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasAuthority("ADMINISTRATOR")
                .requestMatchers("/api/admin/**").hasAuthority("ADMINISTRATOR")
                .requestMatchers("/api/interfaces/*/delete").hasAuthority("ADMINISTRATOR")
                .requestMatchers(HttpMethod.POST, "/api/interfaces").hasAuthority("ADMINISTRATOR")
                .requestMatchers(HttpMethod.PUT, "/api/interfaces/**").hasAuthority("ADMINISTRATOR")
                .requestMatchers(HttpMethod.DELETE, "/api/interfaces/**").hasAuthority("ADMINISTRATOR")
                
                // SSH Key management (Admin only)
                .requestMatchers("/api/ssh-keys/**").hasAuthority("ADMINISTRATOR")
                
                // PGP Key management (Admin only)
                .requestMatchers("/api/pgp-keys/**").hasAuthority("ADMINISTRATOR")
                
                // Configuration endpoints (Admin only)
                .requestMatchers("/api/config/**").hasAuthority("ADMINISTRATOR")
                .requestMatchers("/api/system/**").hasAuthority("ADMINISTRATOR")
                
                // Viewer can read most endpoints
                .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAnyAuthority("ADMINISTRATOR", "VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/interfaces/**").hasAnyAuthority("ADMINISTRATOR", "VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/executions/**").hasAnyAuthority("ADMINISTRATOR", "VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/logs/**").hasAnyAuthority("ADMINISTRATOR", "VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/files/**").hasAnyAuthority("ADMINISTRATOR", "VIEWER")
                .requestMatchers(HttpMethod.GET, "/api/monitoring/**").hasAnyAuthority("ADMINISTRATOR", "VIEWER")
                
                // File downloads (both roles)
                .requestMatchers("/api/files/download/**").hasAnyAuthority("ADMINISTRATOR", "VIEWER")
                
                // API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // All other requests (SPA routes) are allowed
                .anyRequest().permitAll()
            )
            
            // Add JWT filter before username/password authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration for frontend integration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow specific origins (configure for your frontend)
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",     // React dev server
            "http://localhost:5173",     // Vite dev server
            "http://localhost:8080",     // Local backend serving frontend
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:8080",     // Local backend serving frontend
            "https://localhost:*",       // HTTPS development
            "https://127.0.0.1:*",
            "https://49389ba1-4043-4ed2-8188-f375c842a571.lovableproject.com", // Frontend deployment
            "https://nonportable-astrictively-lorelai.ngrok-free.dev", // ngrok tunnel
            "https://*.ngrok.io",        // All ngrok tunnels
            "https://*.ngrok-free.app"   // All ngrok free app tunnels
        ));
        
        // Allow all HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        ));
        
        // Allow all headers
        configuration.setAllowedHeaders(List.of("*"));
        
        // Allow credentials (for cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        // Expose headers that frontend might need
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Total-Count", "X-Page-Number", "ngrok-skip-browser-warning"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}