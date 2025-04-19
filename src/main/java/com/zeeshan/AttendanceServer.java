package com.zeeshan;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import io.github.cdimascio.dotenv.Dotenv;

public class AttendanceServer {
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // PostgreSQL config from env variables
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .filename(".env")
                .ignoreIfMissing()
                .load();

        String dbUrl = dotenv.get("DB_URL");
        String dbUser = dotenv.get("DB_USER");
        String dbPassword = dotenv.get("DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null) {
            System.err.println("Environment variables DB_URL, DB_USER, or DB_PASSWORD not set.");
            return;
        }

        initializeDatabase(dbUrl, dbUser, dbPassword);

        server.createContext("/api/signup", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                handleSignup(exchange, dbUrl, dbUser, dbPassword);
            } else {
                handleOptions(exchange);
            }
        });

        server.createContext("/api/login", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                handleLogin(exchange, dbUrl, dbUser, dbPassword);
            } else {
                handleOptions(exchange);
            }
        });

        server.createContext("/api/attendance", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                handleAttendance(exchange, dbUrl, dbUser, dbPassword);
            } else {
                handleOptions(exchange);
            }
        });

        server.createContext("/api/exit", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                handleExit(exchange, dbUrl, dbUser, dbPassword);
            } else {
                handleOptions(exchange);
            }
        });

        server.createContext("/api/save-location", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                handleSaveLocation(exchange, dbUrl, dbUser, dbPassword);
            } else {
                handleOptions(exchange);
            }
        });

        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://attendance-frontend-chi.vercel.app/");
            exchange.sendResponseHeaders(302, -1);
        });

        server.setExecutor(null); // Default executor
        server.start();
        System.out.println("Server started on port " + PORT);
    }

    private static void setCORSHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private static void handleOptions(HttpExchange exchange) throws IOException {
        setCORSHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void handleSignup(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String[] parts = requestBody.split("&");
        String email = null, password = null, name = null;

        for (String part : parts) {
            if (part.startsWith("email=")) email = URLDecoder.decode(part.split("=")[1], StandardCharsets.UTF_8);
            if (part.startsWith("password=")) password = URLDecoder.decode(part.split("=")[1], StandardCharsets.UTF_8);
            if (part.startsWith("name=")) name = URLDecoder.decode(part.split("=")[1], StandardCharsets.UTF_8);
        }

        if (email == null || password == null || name == null) {
            sendJsonResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing required fields\"}");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO sys_user (name, email, password) VALUES (?, ?, ?)");
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.executeUpdate();
            sendJsonResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Signup successful\"}");
        } catch (SQLException e) {
            sendJsonResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Signup failed: " + e.getMessage() + "\"}");
        }
    }

    private static void handleLogin(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String[] parts = requestBody.split("&");
        String email = null, password = null;

        for (String part : parts) {
            if (part.startsWith("email=")) email = URLDecoder.decode(part.split("=")[1], StandardCharsets.UTF_8);
            if (part.startsWith("password=")) password = URLDecoder.decode(part.split("=")[1], StandardCharsets.UTF_8);
        }

        if (email == null || password == null) {
            sendJsonResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing email or password\"}");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM sys_user WHERE email=? AND password=?");
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                sendJsonResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Login successful\"}");
            } else {
                sendJsonResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid credentials\"}");
            }
        } catch (SQLException e) {
            sendJsonResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Login failed: " + e.getMessage() + "\"}");
        }
    }

    private static void handleAttendance(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonData(requestBody);

        String username = data.get("username");
        String latitude = data.get("latitude");
        String longitude = data.get("longitude");
        String image = data.get("image");
        String description = data.get("description");

        if (username == null || latitude == null || longitude == null) {
            sendJsonResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing required fields\"}");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO attendance_log (user_email, timestamp, latitude, longitude, address, weather, image, remark, type) " +
                            "VALUES (?, NOW(), ?, ?, ?, ?, ?, ?, 1)"
            );
            stmt.setString(1, username);
            stmt.setDouble(2, Double.parseDouble(latitude));
            stmt.setDouble(3, Double.parseDouble(longitude));
            stmt.setString(4, data.get("address"));
            stmt.setString(5, data.get("weather"));
            stmt.setString(6, image);
            stmt.setString(7, description);
            stmt.executeUpdate();
            sendJsonResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Attendance marked\"}");
        } catch (SQLException e) {
            sendJsonResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Attendance failed: " + e.getMessage() + "\"}");
        }
    }

    private static void handleExit(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonData(requestBody);

        String username = data.get("username");
        String latitude = data.get("latitude");
        String longitude = data.get("longitude");
        String image = data.get("image");
        String description = data.get("description");

        if (username == null || latitude == null || longitude == null) {
            sendJsonResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing required fields\"}");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO attendance_log (user_email, timestamp, latitude, longitude, address, weather, image, remark, type) " +
                            "VALUES (?, NOW(), ?, ?, ?, ?, ?, ?, 2)"
            );
            stmt.setString(1, username);
            stmt.setDouble(2, Double.parseDouble(latitude));
            stmt.setDouble(3, Double.parseDouble(longitude));
            stmt.setString(4, data.get("address"));
            stmt.setString(5, data.get("weather"));
            stmt.setString(6, image);
            stmt.setString(7, description);
            stmt.executeUpdate();
            sendJsonResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Exit marked\"}");
        } catch (SQLException e) {
            sendJsonResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Exit failed: " + e.getMessage() + "\"}");
        }
    }

    private static void handleSaveLocation(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonData(requestBody);

        String username = data.get("username");
        String name = data.get("name");
        String latitude = data.get("latitude");
        String longitude = data.get("longitude");

        if (username == null || name == null || latitude == null || longitude == null) {
            sendJsonResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing required fields\"}");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO saved_locations (user_email, name, latitude, longitude) VALUES (?, ?, ?, ?)"
            );
            stmt.setString(1, username);
            stmt.setString(2, name);
            stmt.setDouble(3, Double.parseDouble(latitude));
            stmt.setDouble(4, Double.parseDouble(longitude));
            stmt.executeUpdate();
            sendJsonResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Location saved\"}");
        } catch (SQLException e) {
            sendJsonResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Failed to save location: " + e.getMessage() + "\"}");
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static Map<String, String> parseJsonData(String json) {
        Map<String, String> map = new HashMap<>();
        try {
            json = json.replace("{", "").replace("}", "").replace("\"", "");
            for (String pair : json.split(",")) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    map.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (Exception e) {
            System.err.println("JSON parsing error: " + e.getMessage());
        }
        return map;
    }

    private static void initializeDatabase(String dbUrl, String dbUser, String dbPassword) {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sys_user (
                    id SERIAL PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS attendance_log (
                    id SERIAL PRIMARY KEY,
                    user_email TEXT REFERENCES sys_user(email),
                    timestamp TIMESTAMP NOT NULL,
                    latitude DOUBLE PRECISION,
                    longitude DOUBLE PRECISION,
                    address TEXT,
                    weather TEXT,
                    image TEXT,
                    remark TEXT,
                    type INTEGER NOT NULL
                );
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS saved_locations (
                    id SERIAL PRIMARY KEY,
                    user_email TEXT REFERENCES sys_user(email),
                    name TEXT NOT NULL,
                    latitude DOUBLE PRECISION NOT NULL,
                    longitude DOUBLE PRECISION NOT NULL
                );
            """);

            System.out.println("Database initialized");
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }
}