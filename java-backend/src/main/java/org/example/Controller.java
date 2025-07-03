package org.example;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import io.javalin.openapi.*;

import org.example.dao.*;

import org.example.dto.*;

import org.example.model.Order;
import org.example.model.Product;
import org.example.model.User;

import org.example.service.KafkaService;
import org.example.util.TokenGenerator;
import org.mindrot.jbcrypt.BCrypt;

import java.util.*;

public class Controller {

    private final UserDao userDao;
    private final SessionDao sessionDao;
    private final ProductDao productDao;
    private final OrderDao orderDao;
    private final AnalyticsOrderDao analyticsOrderDao;
    private final KafkaService kafkaService;

    public Controller(UserDao userDao, SessionDao sessionDao, ProductDao productDao, OrderDao orderDao, AnalyticsOrderDao analyticsOrderDao, KafkaService kafkaService) {
        this.userDao = userDao;
        this.sessionDao = sessionDao;
        this.productDao = productDao;
        this.orderDao = orderDao;
        this.analyticsOrderDao = analyticsOrderDao;
        this.kafkaService = kafkaService;
    }


    @OpenApi(
            path = "/", summary = "Health check", description = "Returns a simple { status: \"OK\" } JSON to show the server is up.", responses = {
            @OpenApiResponse(status = "200", description = "Server is running! ")
    })
    public void healthCheck(Context ctx) {
        ctx.json(Map.of("status", "OK"));
    }


    @OpenApi(
            path = "/users",
            methods = HttpMethod.POST,
            summary = "Register a new user",
            description = "Registers a user with a username, password, and email.",
            requestBody = @OpenApiRequestBody(
                    content = @OpenApiContent(
                            from = RegisterDto.class,
                            mimeType = "application/json",
                            example = "{\"username\": \"jane_doe\", \"password\": \"StrongPass123!\", \"email\": \"jane_doe@gmail.com\"}"
                    ),
                    required = true
            ),
            responses = {
                    @OpenApiResponse(status = "201", description = "Registered successfully"),
                    @OpenApiResponse(status = "400", description = "Missing or duplicate username or email"),
                    @OpenApiResponse(status = "500", description = "Server error")
            }
    )
    public void registerUser(Context ctx) {

        //MAPS JSON TO JAVA OBJECT
        RegisterDto form = ctx.bodyAsClass(RegisterDto.class);

        if (form.username() == null || form.password() == null) {
            ctx.status(400).json(Map.of("error", "username and password required"));
            return;
        }

        // If email is missing or blank, set it to "username@gmail.com"
        String email = (form.email() == null || form.email().isBlank())
                ? form.username() + "@gmail.com"
                : form.email();

        String pwHash = BCrypt.hashpw(form.password(), BCrypt.gensalt(12));

        try {

            userDao.insertUser(form.username(), form.email(), pwHash);
            ctx.status(201).json(Map.of("message", "Registered successfully"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "error", "Registration failed",
                    "details", e.getMessage()
            ));
        }
    }

    @OpenApi(
            path = "/sessions",
            methods = HttpMethod.POST,
            summary = "Login and create session",
            description = "Authenticates the user and creates a session if credentials are valid.",
            requestBody = @OpenApiRequestBody(
                    content = @OpenApiContent(
                            from = LoginDto.class,
                            mimeType = "application/json",
                            example = "{\"username\": \"jane_doe\", \"password\": \"StrongPass123!\"}"

                    ),
                    required = true
            ),
            responses = {
                    @OpenApiResponse(status = "200", description = "Login successful"),
                    @OpenApiResponse(status = "400", description = "Missing username or password"),
                    @OpenApiResponse(status = "401", description = "Invalid credentials"),
                    @OpenApiResponse(status = "500", description = "Server error")
            }
    )
    public void login(Context ctx) {

        // MAPS JSON TO JAVA OBJECT
        LoginDto form = ctx.bodyAsClass(LoginDto.class);

        if (form.username() == null || form.password() == null) {
            ctx.status(400).json(Map.of("error", "username and password required"));
            return;
        }

        try {

            Optional<User> maybeUser = userDao.findByUsername(form.username());
            if (maybeUser.isEmpty()) {
                ctx.status(401).json(Map.of("error", "Invalid credentials"));
                return;
            }

            User user = maybeUser.get();
            String hash = user.password_hash();
            int userId = user.id();

            // 3) Check bcrypt
            if (BCrypt.checkpw(form.password(), hash)) {
                // Generate session token
                String token = TokenGenerator.generateToken();

                sessionDao.insertSession(token, userId);

                Cookie newCookie = new Cookie("session_token", token);
                newCookie.setMaxAge(3600);
                newCookie.setPath("/");
                newCookie.setHttpOnly(true);
                newCookie.setSameSite(SameSite.LAX);

                ctx.cookie(newCookie);

                ctx.json(Map.of("message", "Login successful"));
            } else {
                ctx.status(401).json(Map.of("error", "Invalid credentials"));
            }
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Login error", "details", e.getMessage()));
        }
    }

    @OpenApi(
            path = "/sessions",
            methods = HttpMethod.DELETE,
            summary = "Logout",
            description = "Logs out the user, removes the session token, and invalidates session if found.",
            responses = {
                    @OpenApiResponse(status = "200", description = "Logged out successfully"),
                    @OpenApiResponse(status = "401", description = "Invalid or missing session token"),
                    @OpenApiResponse(status = "500", description = "Server error during logout")
            }
    )
    public void logout(Context ctx) {
        String token = ctx.cookie("session_token");

        if (token == null) {
            ctx.status(401).json(Map.of("error", "Missing session token"));
            return;
        }

        try {
            // Validate the token

            Optional<Integer> maybeUserId = sessionDao.getUserIdByToken(token);
            if (maybeUserId.isEmpty()) {
                ctx.status(401).json(Map.of("error", "Invalid or expired session token"));
                return;
            }

            // Delete session and cookie
            sessionDao.deleteSession(token);
            ctx.removeCookie("session_token");

            ctx.json(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Logout error", "details", e.getMessage()));
        }
    }

    @OpenApi(
            path = "/me",
            methods = HttpMethod.GET,
            summary = "Get current authenticated user",
            description = "Returns the current user's ID and username if logged in. Requires an active session.",
            responses = {
                    @OpenApiResponse(status = "200", description = "User is logged in and session is active"),
                    @OpenApiResponse(status = "401", description = "User is not logged in")
            }
    )
    public void me(Context ctx) {
        try {
            int userId = sessionDao.getUserIdByToken(ctx.cookie("session_token")).get();
            User user = userDao.findById(userId).get();
            ctx.json(Map.of("id", user.id(), "username", user.username()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not fetch user", "details", e.getMessage()));
        }
    }

    @OpenApi(
            path = "/allUsers",
            methods = HttpMethod.GET,
            summary = "Get all users",
            description = "Returns the info of all users",
            responses = {
                    @OpenApiResponse(status = "200", description = ""),
                    @OpenApiResponse(status = "401", description = "User is not logged in")
            }
    )
    public void allUsers(Context ctx) {
        try {
            List<User> users = userDao.findAll();
            ctx.json(users);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not fetch users", "details", e.getMessage()));
        }
    }


    @OpenApi(
            path = "/allProducts",
            methods = HttpMethod.GET,
            summary = "List all products",
            description = "Returns a list of all products available for ordering.",
            responses = {
                    @OpenApiResponse(status = "200", description = "")
            }
    )
    public void allProducts(Context ctx) {
        try {
            List<Product> products = productDao.findAll();
            ctx.json(products);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not fetch users", "details", e.getMessage()));
        }
    }

    @OpenApi(
            path = "/products",
            methods = HttpMethod.POST,
            summary = "Create a new product",
            description = "Creates a new product (admin action).",
            requestBody = @OpenApiRequestBody(
                    content = @OpenApiContent(
                            from = CreateProductDto.class,
                            mimeType = "application/json",
                            example = "{\"name\":\"Book\", \"price\":30.00, \"stock\":100}"
                    ),
                    required = true
            ),
            responses = {
                    @OpenApiResponse(status = "201", description = "Product created", content = @OpenApiContent(from = Product.class)),
                    @OpenApiResponse(status = "400", description = "Invalid product data"),
                    @OpenApiResponse(status = "500", description = "Server error")
            }
    )
    public void createProduct(Context ctx) {
        try {
            CreateProductDto form = ctx.bodyAsClass(CreateProductDto.class);

            if (form.name() == null || form.name().isBlank()) {
                ctx.status(400).json(Map.of("error", "Product name is required"));
                return;
            }
            if (form.price() < 0) {
                ctx.status(400).json(Map.of("error", "Price must be non-negative"));
                return;
            }
            if (form.stock() < 0) {
                ctx.status(400).json(Map.of("error", "Stock must be non-negative"));
                return;
            }

            productDao.createProduct(form.name(), form.price(), form.stock());
            // ctx.status(201).json(created);

        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not create product", "details", e.getMessage()));
        }
    }

    @OpenApi(
            path = "/orders",
            methods = HttpMethod.POST,
            summary = "Create a new order",
            description = "Creates a new order for the authenticated user. Requires a valid session.",
            requestBody = @OpenApiRequestBody(
                    content = @OpenApiContent(
                            from = Order.class,
                            mimeType = "application/json",
                            example = "{\"productId\": 1, \"quantity\": 2}"
                    ),
                    required = true
            ),
            responses = {
                    @OpenApiResponse(status = "201", description = "Order created", content = @OpenApiContent(from = Order.class)),
                    @OpenApiResponse(status = "401", description = "User not authenticated"),
                    @OpenApiResponse(status = "500", description = "Order creation failed")
            }
    )
    public void createOrder(Context ctx) {
        try {
            // Parse order info from body (DO NOT TRUST userId/status from client)
            CreateOrderDto req = ctx.bodyAsClass(CreateOrderDto.class);

            // Validate input
            if (req.quantity() <= 0) {
                ctx.status(400).json(Map.of("error", "Quantity must be greater than zero"));
                return;
            }
            // Get logged-in user from session
            int userId = sessionDao.getUserIdByToken(ctx.cookie("session_token")).orElseThrow();

            // (Optional) Check if product exists
             Optional<Product> product = productDao.findById(req.productId());
             if (product.isEmpty()) {
                 ctx.status(404).json(Map.of("error", "Product not found"));
                 return;
             }

            // Always set status to CREATED and userId from session
            Order createdOrder = orderDao.createOrder(userId, req.productId(), req.quantity(), "CREATED");
            User user = userDao.findById(createdOrder.userId()).orElseThrow(); // alan userın bilgileri, findbyID id, username, email, password_hash bilgileri var userın
            Product purchasedProduct = productDao.findById(createdOrder.productId()).orElseThrow(); // alınan ürünün bilgileri

            // Build the enriched event
            OrderCreatedEvent event = new OrderCreatedEvent(
                    createdOrder.id(),
                    user.email(),
                    user.username(),
                    purchasedProduct.name(),
                    createdOrder.quantity(),
                    purchasedProduct.price(),
                    createdOrder.status(),
                    createdOrder.orderDate()
            );


            ObjectMapper om = new ObjectMapper();
            om.registerModule(new JavaTimeModule());
            String orderJson = om.writeValueAsString(event);

            kafkaService.sendOrderCreatedEvent(orderJson);
            ctx.status(201).json(createdOrder);


        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Order creation failed", "details", e.getMessage()));
        }
    }

    @OpenApi(
            summary = "Get analytics of product order counts",
            operationId = "getAnalytics",
            path = "/analytics",
            methods = HttpMethod.GET,
            responses = {
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(
                                    from = Map.class, // This will show a "dictionary" in Swagger UI
                                    type = "application/json"
                            ),
                            description = "Returns a map of product names to order counts"
                    )
            }
    )
    public void getAnalytics(Context ctx) {
        List<Map<String, Object>> analytics = analyticsOrderDao.getAllProductCounts();
        ctx.json(analytics);
    }



}
