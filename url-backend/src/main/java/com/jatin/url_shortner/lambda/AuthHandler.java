package com.jatin.url_shortner.lambda;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jatin.url_shortner.config.LambdaEnvConfig;
import com.jatin.url_shortner.hashing.ConsistentHashRouter;
import com.jatin.url_shortner.util.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final AmazonDynamoDB DYNAMO_CLIENT = AmazonDynamoDBClientBuilder.defaultClient();
    private static final DynamoDB DYNAMO = new DynamoDB(DYNAMO_CLIENT);
    private static final JwtUtil JWT = new JwtUtil(
            LambdaEnvConfig.getJwtSecret(),
            LambdaEnvConfig.getJwtExpirationMsOrDefault());
    private static final ConsistentHashRouter ROUTER = new ConsistentHashRouter(Arrays.asList(
            LambdaEnvConfig.getUrlTableShardA(),
            LambdaEnvConfig.getUrlTableShardB(),
            LambdaEnvConfig.getUrlTableShardC()));
    private static final String USERS_TABLE = "url-users";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String path = normalizePath(event);
            String method = event.getHttpMethod();

            if ("POST".equalsIgnoreCase(method) && "/auth/register".equalsIgnoreCase(path)) {
                return handleRegister(event);
            }

            if ("POST".equalsIgnoreCase(method) && "/auth/login".equalsIgnoreCase(path)) {
                return handleLogin(event);
            }
            return response(404, Map.of("message", "Route not found"));
        } catch (Exception e) {
            return response(500, Map.of("message", "Internal error: " + e.getMessage()));
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

    private APIGatewayProxyResponseEvent handleRegister(APIGatewayProxyRequestEvent event) throws Exception {
        Map<String, String> body = MAPPER.readValue(event.getBody(), Map.class);
        String username = body.get("username");
        String password = body.get("password");
        String name = body.get("name");
        String email = body.get("email");

        if (isBlank(username) || isBlank(password) || isBlank(name) || isBlank(email)) {
            return response(400, Map.of("message", "Missing required fields"));
        }

        Table table = DYNAMO.getTable(USERS_TABLE);
        Item existing = table.getItem("username", username);
        if (existing != null) {
            return response(400, Map.of("message", "Username already taken"));
        }

        String hashedPassword = ENCODER.encode(password);
        String id = UUID.randomUUID().toString();

        Item user = new Item()
                .withPrimaryKey("username", username)
                .withString("id", id)
                .withString("password", hashedPassword)
                .withString("name", name)
                .withString("email", email)
                .withString("createdAt", Instant.now().toString());

        table.putItem(user);

        return response(201, Map.of(
                "id", id,
                "username", username,
                "name", name,
                "email", email));
    }

    private APIGatewayProxyResponseEvent handleLogin(APIGatewayProxyRequestEvent event) throws Exception {
        Map<String, String> body = MAPPER.readValue(event.getBody(), Map.class);
        String username = body.get("username");
        String password = body.get("password");

        if (isBlank(username) || isBlank(password)) {
            return response(400, Map.of("message", "Missing required fields"));
        }

        Table table = DYNAMO.getTable(USERS_TABLE);
        Item user = table.getItem("username", username);

        if (user == null || !ENCODER.matches(password, user.getString("password"))) {
            return response(401, Map.of("message", "Invalid credentials"));
        }

        String token = JWT.generateToken(username);
        return response(200, Map.of("token", token));
    }

    private APIGatewayProxyResponseEvent response(int status, Object payload) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(defaultHeaders())
                    .withBody(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(defaultHeaders())
                    .withBody("{}");
        }
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
