package com.zeeshan;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.zeeshan.utils.Env;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Base64;

public class AttendanceServer {
    static String dbUrl = Env.get("DB_URL");
    static String dbUser = Env.get("DB_USER");
    static String dbPass = Env.get("DB_PASSWORD");

    public static void main(String[] args) throws Exception {
        // Initialize database schema
        try (Connection conn = getConnection()) {
            System.out.println("Connected to database");
            initializeDatabase(conn);
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            System.err.println("Skipping database initialization for now...");
        }

        int port = Integer.parseInt(Env.get("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/signup", new SignupHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/attendance", new AttendanceHandler("Attendance"));
        server.createContext("/api/exit", new AttendanceHandler("Exit"));
        server.createContext("/api/save-location", new LocationHandler());
        server.createContext("/", new RootHandler());

        // Add OPTIONS handler for CORS preflight requests
        server.createContext("/api/", new CorsPreflightHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + port);
    }

    private static void initializeDatabase(Connection conn) {
        try {
            // Read schema.sql from resources
            InputStream is = AttendanceServer.class.getClassLoader().getResourceAsStream("schema.sql");
            if (is == null) {
                System.err.println("Could not find schema.sql in resources");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sql = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sql.append(line).append("\n");
            }

            // Split by semicolons to execute each statement separately
            String[] statements = sql.toString().split(";");
            for (String statement : statements) {
                if (!statement.trim().isEmpty()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(statement);
                    }
                }
            }

            System.out.println("Database schema initialized");
        } catch (Exception e) {
            System.err.println("Failed to initialize database schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Connection getConnection() throws SQLException {
        // If the URL contains postgres, make sure to adjust SSL settings
        if (dbUrl != null && dbUrl.contains("postgres")) {
            // Add SSL parameters if needed
            if (!dbUrl.contains("sslmode=")) {
                dbUrl += (dbUrl.contains("?") ? "&" : "?") + "sslmode=require";
            }
        }
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // Allow all domains
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"); // Allow all methods
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization"); // Allow headers
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    static class CorsPreflightHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400"); // 24 hours
                exchange.sendResponseHeaders(204, -1); // No content
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
            }
        }
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Redirect to your Vercel frontend
            String vercelUrl = Env.get("FRONTEND_URL", "https://attendance-frontend-chi.vercel.app/");
            exchange.getResponseHeaders().set("Location", vercelUrl);
            exchange.sendResponseHeaders(301, -1);  // 301 = Permanent redirect
        }
    }

    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                return;
            }

            String requestBody = readRequestBody(exchange);
            System.out.println("Signup Request Body: " + requestBody);
            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid JSON: " + e.getMessage() + "\"}");
                return;
            }

            String username = json.optString("username");
            String password = json.optString("password");

            if (username.isEmpty() || password.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Username and password required\"}");
                return;
            }

            try (Connection conn = getConnection()) {
                // Check if username exists
                String checkSql = "SELECT sys_user_id FROM sys_user WHERE name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                    stmt.setString(1, username);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Username already exists\"}");
                        return;
                    }
                }

                // Hash the password using SHA-256
                String hashedPassword = hashPassword(password);

                // Insert new user
                String insertSql = "INSERT INTO sys_user (name, password, organization_id, active_lookup_id) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, hashedPassword);
                    stmt.setInt(3, 1); // Default organization_id
                    stmt.setInt(4, 1); // Assume active (1 = active)
                    stmt.executeUpdate();
                }

                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Signup successful\"}");
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Database error: " + e.getMessage() + "\"}");
            }
        }

        private String hashPassword(String password) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hash);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Password hashing failed", e);
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                return;
            }

            String requestBody = readRequestBody(exchange);
            System.out.println("Login Request Body: " + requestBody);
            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid JSON: " + e.getMessage() + "\"}");
                return;
            }

            String username = json.optString("username");
            String password = json.optString("password");

            try (Connection conn = getConnection()) {
                String sql = "SELECT sys_user_id, password FROM sys_user WHERE name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next() && rs.getString("password").equals(hashPassword(password))) { // Compare hashed passwords
                        sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Login successful\",\"username\":\"" + username + "\"}");
                    } else {
                        sendResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid credentials\"}");
                    }
                }
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Database error: " + e.getMessage() + "\"}");
            }
        }

        private String hashPassword(String password) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(hash);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Password hashing failed", e);
            }
        }
    }

    static class AttendanceHandler implements HttpHandler {
        private final String type;

        public AttendanceHandler(String type) {
            this.type = type;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                return;
            }

            String requestBody = readRequestBody(exchange);
            System.out.println("Attendance Request Body: " + requestBody);
            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid JSON: " + e.getMessage() + "\"}");
                return;
            }

            String username = json.optString("username");
            String location = json.optString("location");
            String signature = json.optString("signature");
            String image = json.optString("image");
            String description = json.optString("description", "");
            JSONObject deviceInfo = json.optJSONObject("deviceInfo");
            String imei = json.optString("imei", "Not Available");
            double latitude = json.optDouble("latitude", Double.NaN);
            double longitude = json.optDouble("longitude", Double.NaN);

            if (username.isEmpty()) {
                sendResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid user\"}");
                return;
            }
            if (location.isEmpty() || signature.isEmpty() || image.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing required fields\"}");
                return;
            }
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                try {
                    String[] latLon = location.split(",");
                    latitude = Double.parseDouble(latLon[0].trim());
                    longitude = Double.parseDouble(latLon[1].trim());
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid location format\"}");
                    return;
                }
            }

            try (Connection conn = getConnection()) {
                // Verify user
                String userSql = "SELECT sys_user_id FROM sys_user WHERE name = ?";
                int sysUserId;
                try (PreparedStatement stmt = conn.prepareStatement(userSql)) {
                    stmt.setString(1, username);
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        sendResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid user\"}");
                        return;
                    }
                    sysUserId = rs.getInt("sys_user_id");
                }

                // Get attendance type ID
                String typeSql = "SELECT attendance_type_lookup_id FROM attendance_type_lookup WHERE type_name = ?";
                int attendanceTypeId;
                try (PreparedStatement stmt = conn.prepareStatement(typeSql)) {
                    stmt.setString(1, type);
                    ResultSet rs = stmt.executeQuery();
                    if (!rs.next()) {
                        sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Invalid attendance type\"}");
                        return;
                    }
                    attendanceTypeId = rs.getInt("attendance_type_lookup_id");
                }

                // Fetch address and weather (simplified; reuse frontend data if provided)
                String address = "Fetched address"; // TODO: Integrate Nominatim API if needed
                String weather = "Sunny, 28°C"; // TODO: Integrate weather API if needed

                // Insert attendance record
                String insertSql = "INSERT INTO attendance_log (sys_user_id, attendance_type_lookup_id, attendance_time, latitude, longitude, address, weather, signature, selfie_picture, organization_id) " +
                        "VALUES (?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setInt(1, sysUserId);
                    stmt.setInt(2, attendanceTypeId);
                    stmt.setDouble(3, latitude);
                    stmt.setDouble(4, longitude);
                    stmt.setString(5, address);
                    stmt.setString(6, weather);
                    stmt.setString(7, signature);
                    stmt.setString(8, image);
                    stmt.setInt(9, 1); // Default organization_id
                    stmt.executeUpdate();
                }

                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"" + type + " marked\"}");
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Database error: " + e.getMessage() + "\"}");
            }
        }
    }

    static class LocationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
                return;
            }

            String requestBody = readRequestBody(exchange);
            System.out.println("Location Save Request Body: " + requestBody);
            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid JSON: " + e.getMessage() + "\"}");
                return;
            }

            String username = json.optString("username");
            String name = json.optString("name");
            double latitude = json.optDouble("latitude", Double.NaN);
            double longitude = json.optDouble("longitude", Double.NaN);

            if (username.isEmpty()) {
                sendResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid user\"}");
                return;
            }
            if (name.isEmpty()) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Location name required\"}");
                return;
            }
            if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid latitude (-90 to 90 required)\"}");
                return;
            }
            if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid longitude (-180 to 180 required)\"}");
                return;
            }

            try (Connection conn = getConnection()) {
                // Verify user and update their location
                String sql = "UPDATE sys_user SET latitude = ?, longitude = ?, address = ? WHERE name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setDouble(1, latitude);
                    stmt.setDouble(2, longitude);
                    stmt.setString(3, name);
                    stmt.setString(4, username);
                    int rows = stmt.executeUpdate();
                    if (rows == 0) {
                        sendResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid user\"}");
                        return;
                    }
                }

                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Location saved successfully\"}");
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"Database error: " + e.getMessage() + "\"}");
            }
        }
    }
}