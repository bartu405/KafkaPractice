package org.example.dto;

public record RegisterDto(
        String username,
        String password,
        String email // <-- Add this!
) {}

