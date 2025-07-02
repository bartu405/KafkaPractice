package org.example.dao;


import java.util.List;
import java.util.Optional;
import org.example.model.User;
import org.jdbi.v3.core.Jdbi;


public class UserDao {

    private final Jdbi jdbi;

    public UserDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void insertUser(String username, String email, String pwHash) {
        jdbi.useHandle(handle ->
                handle.createUpdate("INSERT INTO users (username, email, password_hash) VALUES (:username, :email, :pwHash)")
                        .bind("username", username)
                        .bind("email", email)
                        .bind("pwHash", pwHash)
                        .execute()
        );
    }

    public Optional<User> findByUsername(String username) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM users WHERE username = :username")
                        .bind("username", username)
                        .mapTo(User.class)
                        .findOne()
        );
    }

    public Optional<User> findById(int id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT id, username, email, password_hash FROM users WHERE id = :id")
                        .bind("id", id)
                        .mapTo(User.class)
                        .findOne()
        );
    }

    public List<User> findAll() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT id, username, email, password_hash FROM users")
                        .mapTo(User.class)
                        .list()
        );
    }
}

