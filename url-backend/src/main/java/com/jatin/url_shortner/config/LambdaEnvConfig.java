package com.jatin.url_shortner.config;

import java.util.Map;

public final class LambdaEnvConfig {

    private static final Map<String, String> ENV = System.getenv();

    private LambdaEnvConfig() {
    }

    public static String getJwtSecret() {
        return require("JWT_SECRET");
    }

    public static String getAppBaseUrl() {
        return require("APP_BASE_URL");
    }

    public static String getRedisHost() {
        return require("REDIS_HOST");
    }

    public static String getUrlTableShardA() {
        return require("URL_TABLE_SHARD_A");
    }

    public static String getUrlTableShardB() {
        return require("URL_TABLE_SHARD_B");
    }

    public static String getUrlTableShardC() {
        return require("URL_TABLE_SHARD_C");
    }

    public static String getClicksTableName() {
        return require("CLICKS_TABLE_NAME");
    }

    public static String getAnalyticsQueueUrl() {
        return require("ANALYTICS_QUEUE_URL");
    }

    public static String getAwsRegion() {
        return require("AWS_REGION");
    }

    public static long getJwtExpirationMsOrDefault() {
        String value = ENV.get("JWT_EXPIRATION_MS");
        if (value == null || value.isBlank()) {
            return 86_400_000L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Environment variable JWT_EXPIRATION_MS must be a valid number", ex);
        }
    }

    private static String require(String key) {
        String value = ENV.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
