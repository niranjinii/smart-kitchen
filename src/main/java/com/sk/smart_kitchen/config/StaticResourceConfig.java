package com.sk.smart_kitchen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.io.File;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.url-prefix:/uploads}")
    private String uploadUrlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String normalizedPrefix = uploadUrlPrefix.endsWith("/") ? uploadUrlPrefix : uploadUrlPrefix + "/";
        String absoluteUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        String normalizedPath = absoluteUploadPath.replace(File.separatorChar, '/');
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }
        String uploadLocation = "file:" + normalizedPath;
        registry.addResourceHandler(normalizedPrefix + "**")
                .addResourceLocations(uploadLocation);
    }
}