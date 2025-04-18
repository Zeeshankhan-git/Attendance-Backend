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
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");


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

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO sys_user (name, email, password) VALUES (?, ?, ?)");
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.executeUpdate();
            sendResponse(exchange, 200, "Signup successful");
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Signup failed: " + e.getMessage());
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

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM sys_user WHERE email=? AND password=?");
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                sendResponse(exchange, 200, "Login successful");
            } else {
                sendResponse(exchange, 401, "Invalid credentials");
            }
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Login failed: " + e.getMessage());
        }
    }

    private static void handleAttendance(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseFormData(requestBody);

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO attendance_log (user_email, timestamp, latitude, longitude, address, weather, image, remark, type) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)"
            );
            stmt.setString(1, data.get("email"));
            stmt.setTimestamp(2, Timestamp.valueOf(data.get("timestamp")));
            stmt.setDouble(3, Double.parseDouble(data.get("latitude")));
            stmt.setDouble(4, Double.parseDouble(data.get("longitude")));
            stmt.setString(5, data.get("address"));
            stmt.setString(6, data.get("weather"));
            stmt.setString(7, data.get("image"));
            stmt.setString(8, data.get("remark"));
            stmt.executeUpdate();
            sendResponse(exchange, 200, "Attendance marked");
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Attendance failed: " + e.getMessage());
        }
    }

    private static void handleExit(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseFormData(requestBody);

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO attendance_log (user_email, timestamp, latitude, longitude, address, weather, image, remark, type) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 2)"
            );
            stmt.setString(1, data.get("email"));
            stmt.setTimestamp(2, Timestamp.valueOf(data.get("timestamp")));
            stmt.setDouble(3, Double.parseDouble(data.get("latitude")));
            stmt.setDouble(4, Double.parseDouble(data.get("longitude")));
            stmt.setString(5, data.get("address"));
            stmt.setString(6, data.get("weather"));
            stmt.setString(7, data.get("image"));
            stmt.setString(8, data.get("remark"));
            stmt.executeUpdate();
            sendResponse(exchange, 200, "Exit marked");
        } catch (SQLException e) {
            sendResponse(exchange, 500, "Exit failed: " + e.getMessage());
        }
    }

    private static void handleSaveLocation(HttpExchange exchange, String dbUrl, String dbUser, String dbPassword) throws IOException {
        sendResponse(exchange, 200, "Location saved (dummy response)");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText) throws IOException {
        byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static Map<String, String> parseFormData(String data) {
        Map<String, String> map = new HashMap<>();
        for (String pair : data.split("&")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                map.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
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

            System.out.println("Database initialized");
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }
}
