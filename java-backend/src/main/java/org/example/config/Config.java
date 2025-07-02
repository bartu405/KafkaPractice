package org.example.config;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {

    private static final Dotenv dotenv;

    static {
        // Try to load .env file. If it fails, Dotenv will act as empty.
        dotenv = Dotenv.configure().ignoreIfMissing().load();
    }

    private static String get(String key) {
        String value = dotenv.get(key);
        if (value == null) value = System.getenv(key);
        return value;
    }

    public static final String URL = get("DB_URL");
    public static final String USER = get("DB_USER");
    public static final String PASSWORD = get("DB_PASSWORD");
    public static final int PORT = Integer.parseInt(get("PORT") != null ? get("PORT") : "8080");

}
