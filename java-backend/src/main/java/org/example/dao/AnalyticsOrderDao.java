package org.example.dao;

import org.jdbi.v3.core.Jdbi;

import java.util.Map;

public class AnalyticsOrderDao {
    private final Jdbi jdbi;

    public AnalyticsOrderDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // Increment order count for a product (insert or update)
    public void incrementProductCount(String productName) {
        jdbi.useHandle(handle -> {
            handle.createUpdate("""
                INSERT INTO product_order_counts (product_name, order_count)
                VALUES (:productName, 1)
                ON DUPLICATE KEY UPDATE order_count = order_count + 1
            """)
                    .bind("productName", productName)
                    .execute();
        });
    }

    // Optional: Get all analytics as a Map
    public Map<String, Integer> getAllProductCounts() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT product_name, order_count FROM product_order_counts")
                        .mapToMap()
                        .stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        m -> (String) m.get("product_name"),
                                        m -> ((Number) m.get("order_count")).intValue()
                                )
                        )
        );
    }
}
