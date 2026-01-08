package com.integrixs.backend.config;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Server configuration to support both HTTP and HTTPS protocols
 */
@Configuration
public class ServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(ServerConfig.class);

    @Value("${h2h.server.http-port:8080}")
    private int httpPort;

    @Value("${h2h.server.https-port:8443}")
    private int httpsPort;

    @Value("${h2h.server.ssl-enabled:false}")
    private boolean sslEnabled;

    @Value("${server.ssl.key-store:#{null}}")
    private Resource keyStore;

    @Value("${server.ssl.key-store-password:}")
    private String keyStorePassword;

    @Value("${server.ssl.key-store-type:PKCS12}")
    private String keyStoreType;

    @Value("${server.ssl.key-alias:h2h}")
    private String keyAlias;

    @Bean
    public ConfigurableServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        
        if (sslEnabled && keyStore != null) {
            logger.info("Configuring dual-port server: HTTP on port {} and HTTPS on port {}", httpPort, httpsPort);
            
            // Configure HTTPS as primary connector
            factory.setPort(httpsPort);
            
            // Add HTTP connector as additional connector
            factory.addAdditionalTomcatConnectors(createHttpConnector());
        } else {
            logger.info("Configuring HTTP-only server on port {}", httpPort);
            factory.setPort(httpPort);
        }
        
        return factory;
    }

    private Connector createHttpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        return connector;
    }
}