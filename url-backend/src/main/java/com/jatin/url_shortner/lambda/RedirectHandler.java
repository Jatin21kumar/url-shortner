package com.jatin.url_shortner.lambda;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jatin.url_shortner.config.LambdaEnvConfig;
import com.jatin.url_shortner.hashing.ConsistentHashRouter;

import redis.clients.jedis.Jedis;

public class RedirectHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int REDIS_PORT = 6379;
    private static final int REDIS_TTL_SECONDS = 3600;
    private static final int RATE_LIMIT_MAX = 60;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    private static final ConsistentHashRouter ROUTER = new ConsistentHashRouter(Arrays.asList(
            LambdaEnvConfig.getUrlTableShardA(),
            LambdaEnvConfig.getUrlTableShardB(),
            LambdaEnvConfig.getUrlTableShardC()));

    private final AmazonDynamoDB dynamoDb;
    private final AmazonSQSAsync sqsClient;

    public RedirectHandler() {
        this.dynamoDb = AmazonDynamoDBClientBuilder.defaultClient();
        this.sqsClient = AmazonSQSAsyncClientBuilder.standard()
                .withRegion(LambdaEnvConfig.getAwsRegion())
                .build();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String shortCode = extractShortCode(event);
            if (shortCode == null || shortCode.isBlank()) {
                return errorResponse(404, "Not found");
            }

            String ip = extractIp(event);
            if (isRateLimited(ip)) {
                return errorResponse(429, "Too many requests. Please slow down.");
            }

            String redisKey = "url:" + shortCode;
            String longUrl = readFromRedis(redisKey);

            if (longUrl == null) {
                Map<String, AttributeValue> item = readItemFromDynamo(shortCode);
                if (item == null) {
                    return errorResponse(404, "Not found");
                }

                AttributeValue expiresAtAttr = item.get("expiresAt");
                if (expiresAtAttr != null && expiresAtAttr.getS() != null) {
                    try {
                        Instant expiresAt = Instant.parse(expiresAtAttr.getS());
                        if (Instant.now().isAfter(expiresAt)) {
                            return errorResponse(410, "This link has expired.");
                        }
                    } catch (Exception ignored) {
                    }
                }

                AttributeValue longUrlAttr = item.get("longUrl");
                if (longUrlAttr == null || longUrlAttr.getS() == null) {
                    return errorResponse(404, "Not found");
                }
                longUrl = longUrlAttr.getS();
                writeToRedis(redisKey, longUrl);
            }

            publishAnalyticsAsync(shortCode, event);

            Map<String, String> headers = defaultHeaders();
            headers.put("Location", longUrl);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(301)
                    .withHeaders(headers)
                    .withBody("");
        } catch (Exception ex) {
            return errorResponse(500, "Unexpected error");
        }
    }

    private boolean isRateLimited(String ip) {
        if (ip == null || ip.equals("unknown")) {
            return false;
        }
        try (Jedis jedis = new Jedis(LambdaEnvConfig.getRedisHost(), REDIS_PORT)) {
            String key = "rate:" + ip;
            long count = jedis.incr(key);
            if (count == 1) {
                jedis.expire(key, RATE_LIMIT_WINDOW_SECONDS);
            }
            return count > RATE_LIMIT_MAX;
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizePath(APIGatewayProxyRequestEvent event) {
        String path = event.getPath();
        if (path == null || path.isBlank()) {
            return path;
        }
        if (event.getRequestContext() == null || event.getRequestContext().getStage() == null
                || event.getRequestContext().getStage().isBlank()) {
            return path;
        }
        String stagePrefix = "/" + event.getRequestContext().getStage();
        if (path.equals(stagePrefix)) {
            return "/";
        }
        if (path.startsWith(stagePrefix + "/")) {
            return path.substring(stagePrefix.length());
        }
        return path;
    }

    private String extractShortCode(APIGatewayProxyRequestEvent event) {
        if (event.getPathParameters() != null && event.getPathParameters().get("shortcode") != null) {
            return event.getPathParameters().get("shortcode");
        }
        String path = normalizePath(event);
        if (path == null || path.isBlank() || "/".equals(path)) {
            return null;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.contains("/")) {
            return null;
        }
        return normalized;
    }

    private String readFromRedis(String key) {
        try (Jedis jedis = new Jedis(LambdaEnvConfig.getRedisHost(), REDIS_PORT)) {
            return jedis.get(key);
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeToRedis(String key, String value) {
        try (Jedis jedis = new Jedis(LambdaEnvConfig.getRedisHost(), REDIS_PORT)) {
            jedis.setex(key, REDIS_TTL_SECONDS, value);
        } catch (Exception ex) {
        }
    }

    private Map<String, AttributeValue> readItemFromDynamo(String shortCode) {
        String tableName = ROUTER.getNode(shortCode);
        GetItemRequest request = new GetItemRequest()
                .withTableName(tableName)
                .withKey(Map.of("shortCode", new AttributeValue().withS(shortCode)));
        GetItemResult result = dynamoDb.getItem(request);
        if (result == null || result.getItem() == null || result.getItem().isEmpty()) {
            return null;
        }
        return result.getItem();
    }

    private void publishAnalyticsAsync(String shortCode, APIGatewayProxyRequestEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> payload = new HashMap<>();
                payload.put("shortCode", shortCode);
                payload.put("ip", extractIp(event));
                payload.put("timestamp", Instant.now().toString());
                String body = OBJECT_MAPPER.writeValueAsString(payload);
                sqsClient.sendMessage(new SendMessageRequest()
                        .withQueueUrl(LambdaEnvConfig.getAnalyticsQueueUrl())
                        .withMessageBody(body));
            } catch (Exception ex) {
            }
        });
    }

    private String extractIp(APIGatewayProxyRequestEvent event) {
        Map<String, String> headers = event.getHeaders();
        if (headers != null) {
            String ff = headers.get("X-Forwarded-For");
            if (ff == null) {
                ff = headers.get("x-forwarded-for");
            }
            if (ff != null && !ff.isBlank()) {
                return ff.split(",")[0].trim();
            }
        }
        if (event.getRequestContext() != null && event.getRequestContext().getIdentity() != null) {
            String src = event.getRequestContext().getIdentity().getSourceIp();
            if (src != null && !src.isBlank()) {
                return src;
            }
        }
        return "unknown";
    }

    private APIGatewayProxyResponseEvent errorResponse(int statusCode, String message) {
        try {
            return new APIGatewayProxyResponseEvent().withStatusCode(statusCode)
                    .withHeaders(defaultHeaders())
                    .withBody(OBJECT_MAPPER.writeValueAsString(Map.of("error", message)));
        } catch (Exception ex) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500)
                    .withHeaders(defaultHeaders()).withBody("{\"error\":\"Internal error\"}");
        }
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        h.put("Content-Type", "application/json");
        return h;
    }
}
