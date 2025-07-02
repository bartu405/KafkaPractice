package org.example.model;

public record User(
        int id,
        String username,
        String email,          // <-- Add this
        String password_hash
) {
    // JDBI SQL-Object will now correctly map to all fields"
}
