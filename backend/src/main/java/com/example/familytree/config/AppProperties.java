package com.example.familytree.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String storageRoot, long maxUploadBytes, Clamav clamav, BootstrapAdmin bootstrapAdmin, Ocr ocr) {
    public record Clamav(boolean enabled, String host, int port) {}
    public record BootstrapAdmin(String username, String password) {}
    public record Ocr(boolean enabled, String provider, String endpoint, String apiKey) {}
}
