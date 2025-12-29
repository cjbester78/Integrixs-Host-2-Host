package com.integrixs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Main Spring Boot application for H2H File Transfer
 */
@SpringBootApplication
@EnableScheduling
public class H2HBackendApplication {

    private static final Logger logger = LoggerFactory.getLogger(H2HBackendApplication.class);

    public static void main(String[] args) {
        try {
            ConfigurableApplicationContext context = SpringApplication.run(H2HBackendApplication.class, args);
            Environment env = context.getEnvironment();
            
            logApplicationStartup(env);
            
        } catch (Exception e) {
            logger.error("Failed to start H2H Backend Application", e);
            System.exit(1);
        }
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String hostAddress = "localhost";
        
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("The host name could not be determined, using 'localhost' as fallback");
        }
        
        String activeProfiles = String.join(", ", env.getActiveProfiles());
        if (activeProfiles.isEmpty()) {
            activeProfiles = "default";
        }
        
        logger.info("""
            
            ----------------------------------------------------------
            	Application 'Integrixs Host 2 Host' is running! Access URLs:
            	Local:      {}://localhost:{}{}
            	External:   {}://{}:{}{}
            	Profile(s): {}
            	Context:    {}
            ----------------------------------------------------------
            
            Default Admin Credentials:
            	Username: Administrator
            	Password: Int3grix@01
            	
            API Documentation:
            	Health:     {}://localhost:{}/api/health
            	Auth:       {}://localhost:{}/api/auth/login
            	Users:      {}://localhost:{}/api/users
            	Dashboard:  {}://localhost:{}/api/dashboard/overview
            ----------------------------------------------------------
            """, 
            protocol, serverPort, contextPath,
            protocol, hostAddress, serverPort, contextPath,
            activeProfiles,
            contextPath,
            protocol, serverPort,
            protocol, serverPort,
            protocol, serverPort,
            protocol, serverPort
        );
    }
}