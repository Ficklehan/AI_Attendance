package com.attendance.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private CorsProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsProperties.getAllowedOriginPatterns()
                .stream()
                .filter(item -> item != null && !item.trim().isEmpty())
                .map(String::trim)
                .toArray(String[]::new);
        if (origins.length == 0) {
            origins = new String[] { "*" };
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(corsProperties.isAllowCredentials())
                .maxAge(3600);
    }
}
