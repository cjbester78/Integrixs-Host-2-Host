package com.integrixs.backend.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Utility class to access Spring context from non-Spring managed objects
 * like Logback appenders.
 */
@Component
public class SpringContext implements ApplicationContextAware {
    
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContext.context = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return context;
    }

    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(beanClass);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T getBean(String beanName, Class<T> beanClass) {
        if (context == null) {
            return null;
        }
        try {
            return context.getBean(beanName, beanClass);
        } catch (Exception e) {
            return null;
        }
    }
}