package com.jatin.url_shortner.lambda;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jatin.url_shortner.config.LambdaEnvConfig;
import com.jatin.url_shortner.hashing.ConsistentHashRouter;
import com.jatin.url_shortner.util.Base62Util;
import com.jatin.url_shortner.util.JwtUtil;

public class UrlHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DELETE_PATH = Pattern.compile("^/url/([^/]+)$");
    private static final int DEFAULT_TTL_DAYS = 30;

    private static final AmazonDynamoDB DYNAMO = AmazonDynamoDBClientBuilder.defaultClient();
    private static final JwtUtil JWT = new JwtUtil(
            LambdaEnvConfig.getJwtSecret(),
            LambdaEnvConfig.getJwtExpirationMsOrDefault());
    private static final List<String> SHARD_TABLES = List.of(
            LambdaEnvConfig.getUrlTableShardA(),
            LambdaEnvConfig.getUrlTableShardB(),
            LambdaEnvConfig.getUrlTableShardC());
    private static final ConsistentHashRouter ROUTER = new ConsistentHashRouter(Arrays.asList(
            LambdaEnvConfig.getUrlTableShardA(),
            LambdaEnvConfig.getUrlTableShardB(),
            LambdaEnvConfig.getUrlTableShardC()));

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String username = authenticate(event.getHeaders());
            if (username == null) {
                return errorResponse(401, "Unauthorized");
            }

            String path = normalizePath(event);
            String method = event.getHttpMethod();

            if ("POST".equalsIgnoreCase(method) && "/url/shorten".equals(path)) {
                return handleShorten(event, username);
            }

            if ("GET".equalsIgnoreCase(method) && "/url/my".equals(path)) {
                return handleGetMyUrls(username);
            }

            if ("DELETE".equalsIgnoreCase(method)) {
                Matcher matcher = DELETE_PATH.matcher(path == null ? "" : path);
                if (matcher.matches()) {
                    String id = matcher.group(1);
                    return handleDelete(id, username);
                }
            }

            return response(404, Map.of("message", "Route not found"));
        } catch (Exception ex) {
            return errorResponse(500, "Unexpected error");
        }
    }

    private String normalizePath(APIGatewayProxyRequestEvent event) {
        String path = event.getPath();
        if (path == null || path.isBlank()) {
            return path;
        }

        if (event.getRequestContext() == null || event.getRequestContext().getStage() == null || event.getRequestContext().getStage().isBlank()) {
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

    private APIGatewayProxyResponseEvent handleShorten(APIGatewayProxyRequestEvent event, String username) throws Exception {
        Map<String, Object> body = MAPPER.readValue(event.getBody(), Map.class);
        String longUrl = (String) body.get("longUrl");

        if (longUrl == null || longUrl.isBlank()) {
            return errorResponse(400, "Missing required fields");
        }

        int ttlDays = DEFAULT_TTL_DAYS;
        if (body.get("ttlDays") instanceof Number) {
            ttlDays = Math.min(((Number) body.get("ttlDays")).intValue(), 365);
            if (ttlDays < 1) {
                ttlDays = DEFAULT_TTL_DAYS;
            }
        }

        String shortCode = generateUniqueShortCode();
        String id = UUID.randomUUID().toString();
        String targetTable = ROUTER.getNode(shortCode);
        Instant expiresAt = Instant.now().plus(ttlDays, ChronoUnit.DAYS);

        putUrlToDynamo(targetTable, id, shortCode, longUrl, username, expiresAt);

        String shortUrl = LambdaEnvConfig.getAppBaseUrl() + "/" + shortCode;
        return response(200, Map.of(
                "id", id,
                "shortCode", shortCode,
                "shortUrl", shortUrl,
                "longUrl", longUrl,
                "expiresAt", expiresAt.toString()));
    }

    private APIGatewayProxyResponseEvent handleGetMyUrls(String username) {
        List<Map<String, Object>> urls = new ArrayList<>();

        for (String tableName : SHARD_TABLES) {
            Map<String, AttributeValue> lastKey = null;

            do {
                ScanRequest request = new ScanRequest()
                        .withTableName(tableName)
                        .withFilterExpression("userId = :uid")
                        .withExpressionAttributeValues(Map.of(":uid", new AttributeValue().withS(username)))
                        .withExclusiveStartKey(lastKey);

                ScanResult scanResult = DYNAMO.scan(request);
                for (Map<String, AttributeValue> item : scanResult.getItems()) {
                    Map<String, Object> mapped = new HashMap<>();
                    mapped.put("id", stringValue(item, "id"));
                    mapped.put("shortCode", stringValue(item, "shortCode"));
                    mapped.put("longUrl", stringValue(item, "longUrl"));
                    mapped.put("createdAt", stringValue(item, "createdAt"));
                    mapped.put("expiresAt", stringValue(item, "expiresAt"));
                    mapped.put("clickCount", numberValue(item, "clickCount"));
                    urls.add(mapped);
                }

                lastKey = scanResult.getLastEvaluatedKey();
            } while (lastKey != null && !lastKey.isEmpty());
        }

        return response(200, urls);
    }

    private APIGatewayProxyResponseEvent handleDelete(String id, String username) {
        for (String tableName : SHARD_TABLES) {
            ScanRequest request = new ScanRequest()
                    .withTableName(tableName)
                    .withFilterExpression("id = :id")
                    .withExpressionAttributeValues(Map.of(":id", new AttributeValue().withS(id)));
            ScanResult scanResult = DYNAMO.scan(request);
            if (scanResult.getItems() == null || scanResult.getItems().isEmpty()) {
                continue;
            }

            Map<String, AttributeValue> item = scanResult.getItems().get(0);

            String owner = stringValue(item, "userId");
            if (!username.equals(owner)) {
                return errorResponse(403, "Forbidden: you do not own this URL");
            }

            String shortCode = stringValue(item, "shortCode");
            if (shortCode == null || shortCode.isBlank()) {
                return errorResponse(500, "Stored URL item is invalid");
            }

            DYNAMO.deleteItem(new DeleteItemRequest()
                    .withTableName(ROUTER.getNode(shortCode))
                    .withKey(Map.of("shortCode", new AttributeValue().withS(shortCode))));

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(204)
                    .withHeaders(defaultHeaders())
                    .withBody("");
        }

        return errorResponse(404, "URL not found");
    }

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < 8; attempt++) {
            long value = ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
            String candidate = Base62Util.toBase62(value);
            String table = ROUTER.getNode(candidate);
            if (!shortCodeExists(table, candidate)) {
                return candidate;
            }
        }

        String fallback = Base62Util.toBase62(System.currentTimeMillis());
        String suffix = Base62Util.toBase62(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        return fallback + suffix.substring(0, Math.min(4, suffix.length()));
    }

    private boolean shortCodeExists(String tableName, String shortCode) {
        GetItemRequest request = new GetItemRequest()
                .withTableName(tableName)
                .withKey(Map.of("shortCode", new AttributeValue().withS(shortCode)));

        GetItemResult result = DYNAMO.getItem(request);
        return result != null && result.getItem() != null && !result.getItem().isEmpty();
    }

    private void putUrlToDynamo(String tableName, String id, String shortCode, String longUrl, String username, Instant expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("shortCode", new AttributeValue().withS(shortCode));
        item.put("id", new AttributeValue().withS(id));
        item.put("longUrl", new AttributeValue().withS(longUrl));
        item.put("userId", new AttributeValue().withS(username));
        item.put("createdAt", new AttributeValue().withS(Instant.now().toString()));
        item.put("clickCount", new AttributeValue().withN("0"));
        item.put("expiresAt", new AttributeValue().withS(expiresAt.toString()));

        DYNAMO.putItem(new PutItemRequest()
                .withTableName(tableName)
                .withItem(item));
    }

    private String authenticate(Map<String, String> headers) {
        String token = extractBearerToken(headers);
        if (token == null || !JWT.isTokenValid(token)) {
            return null;
        }

        try {
            return JWT.extractUsername(token);
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractBearerToken(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        String auth = headers.get("Authorization");
        if (auth == null) {
            auth = headers.get("authorization");
        }

        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return auth.substring(7);
    }

    private String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null) {
            return null;
        }
        return value.getS() != null ? value.getS() : value.getN();
    }

    private long numberValue(Map<String, AttributeValue> item, String key) {
        try {
            String value = stringValue(item, key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private APIGatewayProxyResponseEvent response(int statusCode, Object payload) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(defaultHeaders())
                    .withBody(MAPPER.writeValueAsString(payload));
        } catch (Exception ex) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(defaultHeaders())
                    .withBody("{\"error\":\"Serialization error\"}");
        }
    }

    private APIGatewayProxyResponseEvent errorResponse(int statusCode, String message) {
        return response(statusCode, Map.of("error", message));
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        h.put("Content-Type", "application/json");
        return h;
    }
}
