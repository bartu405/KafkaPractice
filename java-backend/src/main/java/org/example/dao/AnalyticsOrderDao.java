package org.example.dao;

import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AnalyticsOrderDao {
    private final Jdbi jdbi;

    public AnalyticsOrderDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // Increment order count AND quantity sum for a product (insert or update)
    public void incrementProductCount(String productName, int quantity, double price) {
        jdbi.useHandle(handle -> {
            handle.createUpdate("""
            INSERT INTO product_order_counts (product_name, order_count, quantity_sum, revenue_sum)
            VALUES (:productName, 1, :quantity, :revenue)
            ON DUPLICATE KEY UPDATE
              order_count = order_count + 1,
              quantity_sum = quantity_sum + :quantity,
              revenue_sum = revenue_sum + :revenue
        """)
                    .bind("productName", productName)
                    .bind("quantity", quantity)
                    .bind("revenue", price*quantity)
                    .execute();
        });
    }


    public List<Map<String, Object>> getAllProductCounts() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT product_name, order_count, quantity_sum, revenue_sum FROM product_order_counts")
                        .mapToMap()
                        .list()
        );
    }


}
