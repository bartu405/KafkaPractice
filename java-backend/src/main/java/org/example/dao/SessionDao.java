package org.example.dao;

import org.jdbi.v3.core.Jdbi;

import java.util.Optional;

public class SessionDao {

    private final Jdbi jdbi;

    public SessionDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void insertSession(String token, int userId) {
        jdbi.useHandle(handle ->
                handle.createUpdate(
                                "INSERT INTO sessions (token, user_id, expires_at) " +
                                        "VALUES (:token, :userId, CURRENT_TIMESTAMP + INTERVAL 1 HOUR)"
                        )
                        .bind("token", token)
                        .bind("userId", userId)
                        .execute()
        );
    }

    public Optional<Integer> getUserIdByToken(String token) {
        return jdbi.withHandle(handle ->
                handle.createQuery(
                                "SELECT user_id FROM sessions " +
                                        "WHERE token = :token AND expires_at > CURRENT_TIMESTAMP"
                        )
                        .bind("token", token)
                        .mapTo(Integer.class)
                        .findOne()
        );
    }

    public void deleteSession(String token) {
        jdbi.useHandle(handle ->
                handle.createUpdate("DELETE FROM sessions WHERE token = :token")
                        .bind("token", token)
                        .execute()
        );
    }
}
