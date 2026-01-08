package com.integrixs.backend.config;

import com.integrixs.backend.interceptor.AdministrativeAuditLoggingInterceptor;
import com.integrixs.backend.interceptor.InterfaceAuditLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration - CORS disabled for production
 * Configures audit logging interceptors for controller operations
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final InterfaceAuditLoggingInterceptor interfaceAuditLoggingInterceptor;
    private final AdministrativeAuditLoggingInterceptor administrativeAuditLoggingInterceptor;
    
    public WebConfig(InterfaceAuditLoggingInterceptor interfaceAuditLoggingInterceptor,
                    AdministrativeAuditLoggingInterceptor administrativeAuditLoggingInterceptor) {
        this.interfaceAuditLoggingInterceptor = interfaceAuditLoggingInterceptor;
        this.administrativeAuditLoggingInterceptor = administrativeAuditLoggingInterceptor;
    }

    // CORS configuration removed - not needed in production
    // Frontend served from same domain/port
    
    /**
     * Configure interceptors for audit logging.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register interface audit logging interceptor for all interface controller paths
        registry.addInterceptor(interfaceAuditLoggingInterceptor)
                .addPathPatterns("/api/interfaces/**")
                .order(1); // Execute first for complete request/response cycle logging
        
        // Register administrative audit logging interceptor for all admin controller paths
        registry.addInterceptor(administrativeAuditLoggingInterceptor)
                .addPathPatterns("/api/admin/**", "/api/logs/**", "/api/system/**", "/api/users/**", "/api/config/**", "/api/data-retention/**")
                .order(2); // Execute after interface interceptor
    }
}