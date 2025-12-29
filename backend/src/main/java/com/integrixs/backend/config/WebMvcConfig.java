package com.integrixs.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC configuration for H2H File Transfer
 * Handles static resource serving and SPA routing
 */
@Configuration
@Order(Ordered.LOWEST_PRECEDENCE)
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.setOrder(Ordered.LOWEST_PRECEDENCE);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Explicitly handle static resources first
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(0);

        registry.addResourceHandler("/*.js", "/*.css", "/*.ico", "/*.png", "/*.jpg", "/*.json")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0);

        // Handle React routes
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);

                        // If the resource exists and is readable, serve it
                        if(requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // Skip API routes and backend routes
                        if(resourcePath.startsWith("api/") ||
                           resourcePath.startsWith("actuator/") ||
                           resourcePath.startsWith("swagger-ui/") ||
                           resourcePath.startsWith("v3/api-docs")) {
                            return null;
                        }

                        // For all other routes, return index.html (React app)
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}