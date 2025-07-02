package org.example.dao;

import org.example.model.Product;
import org.jdbi.v3.core.Jdbi;
import java.util.List;
import java.util.Optional;

public class ProductDao {
    private final Jdbi jdbi;

    public ProductDao(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<Product> findAll() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT id, name, price, stock FROM products")
                        .mapTo(Product.class)
                        .list()
        );
    }

    public void createProduct(String name, double price, int stock) {
        jdbi.useHandle(handle ->
                handle.createUpdate("INSERT INTO products (name, price, stock) VALUES (:name, :price, :stock)")
                        .bind("name", name)
                        .bind("price", price)
                        .bind("stock", stock)
                        .execute()
        );
    }

    public Optional<Product> findById(int id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT id, name, price, stock FROM products WHERE id = :id")
                        .bind("id", id)
                        .mapTo(Product.class)
                        .findOne()
        );
    }

}
