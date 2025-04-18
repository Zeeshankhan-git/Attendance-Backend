package com.zeeshan;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.stream.Collectors;

public class AttendanceServer {
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        System.out.println("Server started on port " + PORT);

        server.createContext("/api/signup", new SignupHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/attendance", new AttendanceHandler());
        server.createContext("/api/exit", new ExitHandler());
        server.createContext("/api/save-location", new SaveLocationHandler());
        server.createContext("/", new RootHandler());

        server.setExecutor(null);
        server.start();
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            Headers headers = exchange.getResponseHeaders();
            headers.add("Location", "https://attendance-frontend-chi.vercel.app/");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                String requestBody = readRequestBody(exchange);
                String[] parts = requestBody.split("&");
                String name = "", email = "", password = "";

                for (String part : parts) {
                    String[] keyValue = part.split("=");
                    if (keyValue.length == 2) {
                        switch (keyValue[0]) {
                            case "name" -> name = keyValue[1];
                            case "email" -> email = keyValue[1];
                            case "password" -> password = keyValue[1];
                        }
                    }
                }

                try (Connection conn = getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO sys_user (name, email, password) VALUES (?, ?, ?)");
                    stmt.setString(1, name);
                    stmt.setString(2, email);
                    stmt.setString(3, password);
                    stmt.executeUpdate();
                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Signup successful\"}");
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                String requestBody = readRequestBody(exchange);
                String[] parts = requestBody.split("&");
                String email = "", password = "";

                for (String part : parts) {
                    String[] keyValue = part.split("=");
                    if (keyValue.length == 2) {
                        if (keyValue[0].equals("email")) email = keyValue[1];
                        if (keyValue[0].equals("password")) password = keyValue[1];
                    }
                }

                try (Connection conn = getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM sys_user WHERE email = ? AND password = ?");
                    stmt.setString(1, email);
                    stmt.setString(2, password);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Login successful\"}");
                    } else {
                        sendResponse(exchange, 401, "{\"status\":\"error\",\"message\":\"Invalid credentials\"}");
                    }
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
            }
        }
    }

    static class AttendanceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                String requestBody = readRequestBody(exchange);
                String[] parts = requestBody.split("&");
                String email = "", timestamp = "", location = "", weather = "", image = "", signature = "", remarks = "";

                for (String part : parts) {
                    String[] keyValue = part.split("=");
                    if (keyValue.length == 2) {
                        switch (keyValue[0]) {
                            case "email" -> email = keyValue[1];
                            case "timestamp" -> timestamp = keyValue[1];
                            case "location" -> location = keyValue[1];
                            case "weather" -> weather = keyValue[1];
                            case "image" -> image = keyValue[1];
                            case "signature" -> signature = keyValue[1];
                            case "remarks" -> remarks = keyValue[1];
                        }
                    }
                }

                try (Connection conn = getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO attendance_log (email, timestamp, location, weather, image, signature, remarks, type_id) VALUES (?, ?, ?, ?, ?, ?, ?, 1)");
                    stmt.setString(1, email);
                    stmt.setString(2, timestamp);
                    stmt.setString(3, location);
                    stmt.setString(4, weather);
                    stmt.setString(5, image);
                    stmt.setString(6, signature);
                    stmt.setString(7, remarks);
                    stmt.executeUpdate();
                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Attendance recorded\"}");
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
            }
        }
    }

    static class ExitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                String requestBody = readRequestBody(exchange);
                String[] parts = requestBody.split("&");
                String email = "", timestamp = "", location = "", remarks = "";

                for (String part : parts) {
                    String[] keyValue = part.split("=");
                    if (keyValue.length == 2) {
                        switch (keyValue[0]) {
                            case "email" -> email = keyValue[1];
                            case "timestamp" -> timestamp = keyValue[1];
                            case "location" -> location = keyValue[1];
                            case "remarks" -> remarks = keyValue[1];
                        }
                    }
                }

                try (Connection conn = getConnection()) {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO attendance_log (email, timestamp, location, remarks, type_id) VALUES (?, ?, ?, ?, 2)");
                    stmt.setString(1, email);
                    stmt.setString(2, timestamp);
                    stmt.setString(3, location);
                    stmt.setString(4, remarks);
                    stmt.executeUpdate();
                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Exit recorded\"}");
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
            }
        }
    }

    static class SaveLocationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                setCORSHeaders(exchange);
                String body = readRequestBody(exchange);
                System.out.println("Received location: " + body);
                sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Location saved\"}");
            } else {
                sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
            }
        }
    }

    // Helper functions
    private static Connection getConnection() throws SQLException {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        return DriverManager.getConnection(url, user, password);
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
        return new BufferedReader(isr).lines().collect(Collectors.joining("\n"));
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        setCORSHeaders(exchange);
        byte[] bytes = response.getBytes();
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            setCORSHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void setCORSHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Headers", "Content-Type");
        headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }
}
