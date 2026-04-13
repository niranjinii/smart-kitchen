package com.sk.smart_kitchen.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultipartConfig {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;
    private static final long MAX_REQUEST_SIZE_BYTES = 30L * 1024 * 1024;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement(
                "",
                MAX_FILE_SIZE_BYTES,
                MAX_REQUEST_SIZE_BYTES,
                0
        );
    }
}
