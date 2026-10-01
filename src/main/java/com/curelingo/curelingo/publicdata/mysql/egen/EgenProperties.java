package com.curelingo.curelingo.publicdata.mysql.egen;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "curelingo.public-data.sync")
public record EgenProperties(
        String apiKey,
        String baseUrl,
        int pageSize,
        Duration requestDelay,
        int maxRequestsPerRun
) {
}
