package com.integrixs.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Web configuration for CORS and other MVC settings
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(
                "http://localhost:5173", // Vite dev server
                "http://localhost:3000",  // Alternative React dev server
                "https://49389ba1-4043-4ed2-8188-f375c842a571.lovableproject.com", // Frontend deployment
                "https://nonportable-astrictively-lorelai.ngrok-free.dev" // ngrok tunnel
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization", "ngrok-skip-browser-warning")
            .allowCredentials(true)
            .maxAge(3600);
    }

    @Bean
    public Filter ngrokHeaderFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                
                // Check if request is coming through ngrok
                String host = httpRequest.getHeader("Host");
                if (host != null && (host.contains("ngrok") || host.contains("ngrok-free.dev"))) {
                    // Add response header to bypass ngrok warning
                    httpResponse.setHeader("ngrok-skip-browser-warning", "true");
                }
                
                chain.doFilter(request, response);
            }
        };
    }
}