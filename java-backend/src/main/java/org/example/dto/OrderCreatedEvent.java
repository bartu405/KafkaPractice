package org.example.dto;

import java.time.LocalDateTime;

public record OrderCreatedEvent(
        int orderId,
        String userEmail,
        String username,
        String productName,
        int quantity,
        String status,
        LocalDateTime orderDate
) {}