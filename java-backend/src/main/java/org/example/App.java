package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.http.Handler;
import io.javalin.json.JavalinJackson;
import org.example.config.Config;

import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import org.example.dao.OrderDao;
import org.example.dao.ProductDao;
import org.example.dao.SessionDao;
import org.example.dao.UserDao;
import org.example.model.Order;
import org.example.model.Product;
import org.example.model.User;
import org.example.service.AnalyticsOrderConsumer;
import org.example.service.KafkaService;
import org.example.service.OrderEventListener;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.mapper.reflect.ReflectionMappers;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


import javax.sql.DataSource;
import java.util.Map;


public class App {

    public static void main(String[] args) {

        // Add the shutdown hook at startup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Closing database connection pool...");
            Database.close();
        }));

        // Creates the Javalin app on port 8080
        Javalin app = Javalin.create(config -> {
            // ─────────── 1) Register the OpenAPI plugin ───────────
            config.registerPlugin(new OpenApiPlugin(openApiConfig -> {
                openApiConfig
                        // 1.a) Expose the JSON spec at /openapi.json
                        .withDocumentationPath("/openapi.json")
                        // 1.b) Customize the Info/Server/Security sections
                        .withDefinitionConfiguration((version, openApiDefinition) ->
                                openApiDefinition
                                        .withServer(openApiServer -> openApiServer
                                                .description("Local servers")
                                                .url("http://localhost:{port}/")
                                                .variable("port", "Server port", "8080", "8080", "7070")
                                        )
                                        .withSecurity(openApiSecurity ->
                                                openApiSecurity
                                                        .withCookieAuth("CookieAuth", "session_token")
                                        )
                        );
            }));
            config.registerPlugin(new SwaggerPlugin(swaggerConfiguration -> {
                swaggerConfiguration
                        .setDocumentationPath("/openapi.json");

            }));


            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
            }));

        });
        // ──────────────────────────────────────────────

        // now start the server
        app.start(Config.PORT);


        DataSource ds = Database.getDataSource();


        // ******************
        Jdbi jdbi = Jdbi.create(ds);

        jdbi.useHandle(handle -> {
            String dbName = handle.createQuery("SELECT DATABASE()").mapTo(String.class).first();
            System.out.println("Connected to database: " + dbName);
            var meta = handle.createQuery("SHOW COLUMNS FROM users").mapToMap().list();
            System.out.println(meta);
        });



        jdbi.registerRowMapper(ConstructorMapper.factory(User.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(Order.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(Product.class));



        UserDao userDao = new UserDao(jdbi);
        SessionDao sessionDao = new SessionDao(jdbi);
        ProductDao productDao = new ProductDao(jdbi);
        OrderDao orderDao = new OrderDao(jdbi);
        KafkaService kafkaService = new KafkaService();

        Controller cnt = new Controller(userDao, sessionDao, productDao, orderDao, kafkaService);


        // Easier to replace UserDao with MockUserDao in tests:
        // Controller cnt = new Controller(new MockUserDao(), new MockSessionDao(), amadeus);

        // define your auth check once
        Handler requireLogin = ctx -> {
            String token = ctx.cookie("session_token");
            if (token == null || sessionDao.getUserIdByToken(token).isEmpty()) {
                // this sets status 401 with your message and skips the rest
                ctx.status(401).json(Map.of("error", "Not logged in"));
                ctx.skipRemainingHandlers(); // This halts further handlers for this request
            }
        };

        // -------------- Add the Kafka Listener here --------------
        OrderEventListener listener = new OrderEventListener(); // . OrderEventListener listener = new OrderEventListener(); This creates an instance of your consumer/listener class, which connects to Kafka and prepares to listen for new messages.
        new Thread(listener).start(); //  This starts your listener running in a new thread, so it works in the background while your main application continues to run. If you did not use a thread, the run() method would block and your app would never finish starting.
        // ---------------------------------------------------------
//        Tip:
//        Don’t add it before your app/DAO setup, because your consumer might rely on the app being up and everything ready.

//        OrderEventListener — reacts to events, simulating a notification service (could also be named OrderNotificationConsumer, for example).
//        AnalyticsOrderConsumer — processes and analyzes events, fits the “consumer” idea.
        AnalyticsOrderConsumer analyticsConsumer = new AnalyticsOrderConsumer(jdbi);
        new Thread(analyticsConsumer).start();

        // apply it to all “protected” paths
        app.before("/me", requireLogin);
        app.before("/allUsers", requireLogin);
        app.before("/orders", requireLogin);


        // public endpoints
        app.get("/", cnt::healthCheck);
        app.post("/users", cnt::registerUser);
        app.post("/sessions", cnt::login);
        app.delete("/sessions", cnt::logout);


        app.get("/me", cnt::me);
        app.get("/allUsers", cnt::allUsers);
        app.get("/allProducts", cnt::allProducts);
        app.post("/products", cnt::createProduct);
        app.post("/orders", cnt::createOrder);



    }


}