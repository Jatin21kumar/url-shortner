package com.jatin.url_shortner.dynamodb;

import java.util.List;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.jatin.url_shortner.config.LambdaEnvConfig;
import com.jatin.url_shortner.hashing.ConsistentHashRouter;

public final class DynamoDbClientUtil {

    private static final AmazonDynamoDB CLIENT = AmazonDynamoDBClientBuilder.standard()
            .withRegion(Regions.fromName(LambdaEnvConfig.getAwsRegion()))
            .build();

    private static final DynamoDBMapper MAPPER = new DynamoDBMapper(CLIENT);

    private static final List<String> SHARD_TABLES = List.of(
            LambdaEnvConfig.getUrlTableShardA(),
            LambdaEnvConfig.getUrlTableShardB(),
            LambdaEnvConfig.getUrlTableShardC());

    private static final ConsistentHashRouter ROUTER = new ConsistentHashRouter(SHARD_TABLES);

    private DynamoDbClientUtil() {
    }

    public static AmazonDynamoDB getClient() {
        return CLIENT;
    }

    public static DynamoDBMapper getMapper() {
        return MAPPER;
    }

    public static String resolveTable(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("shortCode cannot be null or blank");
        }
        return ROUTER.getNode(shortCode);
    }

    public static List<String> getUrlShardTables() {
        return SHARD_TABLES;
    }
}
