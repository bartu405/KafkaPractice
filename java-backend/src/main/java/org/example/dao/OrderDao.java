package org.example.dao;

import org.example.model.Order;
import org.jdbi.v3.core.Jdbi;

import java.util.Optional;

public class OrderDao {

    private final Jdbi jdbi;

    public OrderDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public Order createOrder(int userId, int productId, int quantity, String status) {
        int newId = jdbi.withHandle(handle ->
                handle.createUpdate("""
                INSERT INTO orders (user_id, product_id, quantity, status)
                VALUES (:userId, :productId, :quantity, :status)
            """)
                        .bind("userId", userId)
                        .bind("productId", productId)
                        .bind("quantity", quantity)
                        .bind("status", status)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(int.class)
                        .one()
        );
        return findById(newId).orElseThrow();
    }

    public Optional<Order> findById(int id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                SELECT 
                    id, 
                    user_id AS userId, 
                    product_id AS productId, 
                    quantity, 
                    status, 
                    order_date AS orderDate
                FROM orders
                WHERE id = :id
            """)
                        .bind("id", id)
                        .mapTo(Order.class)
                        .findOne()
        );
    }
}
