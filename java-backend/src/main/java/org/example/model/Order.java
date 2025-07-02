package org.example.model;

import java.time.LocalDateTime;

public record Order(
        int id,
        int userId,
        int productId,
        int quantity,
        String status,
        LocalDateTime orderDate
) {}
