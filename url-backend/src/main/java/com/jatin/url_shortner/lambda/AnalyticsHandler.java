package com.jatin.url_shortner.lambda;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jatin.url_shortner.config.LambdaEnvConfig;
import com.jatin.url_shortner.hashing.ConsistentHashRouter;

public class AnalyticsHandler implements RequestHandler<SQSEvent, Void> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AmazonDynamoDB dynamoDb;
        private static final ConsistentHashRouter ROUTER = new ConsistentHashRouter(Arrays.asList(
            LambdaEnvConfig.getUrlTableShardA(),
            LambdaEnvConfig.getUrlTableShardB(),
            LambdaEnvConfig.getUrlTableShardC()));

    public AnalyticsHandler() {
        this.dynamoDb = AmazonDynamoDBClientBuilder.defaultClient();
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        if (event == null || event.getRecords() == null) {
            return null;
        }

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                Map<String, String> body = MAPPER.readValue(message.getBody(), new TypeReference<Map<String, String>>() {
                });

                String shortCode = body.get("shortCode");
                String ip = body.get("ip");
                String timestamp = body.get("timestamp");

                Map<String, AttributeValue> item = new HashMap<>();
                item.put("id", new AttributeValue().withS(UUID.randomUUID().toString()));
                item.put("shortCode", new AttributeValue().withS(shortCode == null ? "" : shortCode));
                item.put("ip", new AttributeValue().withS(ip == null ? "" : ip));
                item.put("timestamp", new AttributeValue().withS(normalizeTimestamp(timestamp)));

                PutItemRequest request = new PutItemRequest()
                        .withTableName(LambdaEnvConfig.getClicksTableName())
                        .withItem(item);

                dynamoDb.putItem(request);

                // Increment clickCount on the URL shard record.
                if (shortCode != null && !shortCode.isBlank()) {
                    String shardTable = ROUTER.getNode(shortCode);
                    UpdateItemRequest updateRequest = new UpdateItemRequest()
                            .withTableName(shardTable)
                            .withKey(Map.of("shortCode", new AttributeValue().withS(shortCode)))
                            .withUpdateExpression("ADD clickCount :inc")
                            .withExpressionAttributeValues(Map.of(":inc", new AttributeValue().withN("1")));
                    dynamoDb.updateItem(updateRequest);
                }
            } catch (Exception ex) {
                if (context != null && context.getLogger() != null) {
                    context.getLogger().log("Failed to process SQS message: " + ex.getMessage());
                }
            }
        }

        return null;
    }

    private String normalizeTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp).toString();
        } catch (Exception ex) {
            return Instant.now().toString();
        }
    }
}
