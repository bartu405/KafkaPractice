package org.example.model;

public record Product(
        int id,
        String name,
        double price,
        int stock
) {}
