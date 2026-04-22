package com.jatin.url_shortner.hashing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConsistentHashRouter {

    private static final int VIRTUAL_NODES = 150;

    private final NavigableMap<Integer, String> ring = new TreeMap<>(Integer::compareUnsigned);

    public ConsistentHashRouter(List<String> nodes) {
        if (nodes != null) {
            for (String node : nodes) {
                addNode(node);
            }
        }
    }

    public String getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No nodes available in hash ring");
        }

        int hash = hashToRingPosition(key);
        NavigableMap<Integer, String> tail = ring.tailMap(hash, true);
        Integer targetKey = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(targetKey);
    }

    public void addNode(String node) {
        validateNode(node);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            int hash = hashToRingPosition(node + ":" + i);
            ring.put(hash, node);
        }
    }

    public void removeNode(String node) {
        validateNode(node);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            int hash = hashToRingPosition(node + ":" + i);
            if (node.equals(ring.get(hash))) {
                ring.remove(hash);
            }
        }
    }

    private void validateNode(String node) {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("Node name cannot be null or blank");
        }
    }

    private int hashToRingPosition(String value) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest, 0, 4).getInt();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 algorithm is not available", ex);
        }
    }
}
