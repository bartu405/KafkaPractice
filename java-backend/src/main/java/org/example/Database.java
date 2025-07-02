package org.example;

import org.example.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class Database {

    private Database() {};

    private static HikariDataSource dataSource;

    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(Config.URL);
            hikariConfig.setUsername(Config.USER);
            hikariConfig.setPassword(Config.PASSWORD);

            // Optional pool tuning:
            hikariConfig.setMaximumPoolSize(12);
            hikariConfig.setIdleTimeout(60000);
            hikariConfig.setConnectionTimeout(30000);

            // Add initialization fail timeout
            hikariConfig.setInitializationFailTimeout(60000); // 60 seconds

            dataSource = new HikariDataSource(hikariConfig);

            // Wait for connection to be available
            waitForDatabase();
        }
        return dataSource;
    }

    private static void waitForDatabase() {
        int maxRetries = 30;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try (Connection conn = dataSource.getConnection()) {
                System.out.println("Database connection successful!");
                return;
            } catch (SQLException e) {
                retryCount++;
                System.out.println("Waiting for database... Attempt " + retryCount + "/" + maxRetries);
                try {
                    Thread.sleep(2000); // Wait 2 seconds before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for database", ie);
                }
            }
        }
        throw new RuntimeException("Could not connect to database after " + maxRetries + " attempts");
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}