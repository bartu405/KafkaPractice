package org.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreatedEvent(
        int orderId,
        String userEmail,
        String username,
        String productName,
        int quantity,
        double price, // <--- ADD THIS LINE
        String status,
        LocalDateTime orderDate
) {}