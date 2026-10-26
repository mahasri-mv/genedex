package genomicindex;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import genomicindex.auth.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Enterprise Genomic Web Server with Role-Based Access Control (RBAC),
 * User Dataset Isolation, Session Authentication, and REST APIs.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Module: REST API Gateway, Multi-tenant Dataset & Authentication Endpoints
 *
 * Features Managed:
 * - Dataset upload (/api/upload) & registry management (/api/datasets)
 * - User authentication, session cookies, and CSRF protection (/api/auth/*)
 * - Report summary and export handlers (/api/reports/*)
 * - Multi-threaded HTTP request routing via thread-safe ConcurrentHashMap caches
 *
 * @author sruthicasathish-stack
 */
public class WebServer {

    public static final int DEFAULT_PORT = 8080;
    private final int port;
    private final UserDatabase userDatabase;
    private final SessionManager sessionManager;
    private final DatasetRegistry datasetRegistry;
    private HttpServer server;

    public WebServer(int port) {
        this.port = port;
        this.userDatabase = new UserDatabase(Paths.get("data"));
        this.sessionManager = new SessionManager();
        this.datasetRegistry = new DatasetRegistry(Paths.get("data"));
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        WebServer webServer = new WebServer(port);
        try {
            webServer.start();
            System.out.println("=================================================================");
            System.out.println("  GENOMIC WAVELET TREE INDEXING SERVER WITH AUTHENTICATION");
            System.out.println("  URL: http://localhost:" + port);
            System.out.println("  Authentication: RBAC Enabled (ADMIN, REGISTERED_USER, GUEST)");
            System.out.println("  Dataset Isolation: Per-User Isolated IndexManager Active");
            System.out.println("=================================================================");
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to start server on port " + port + ": " + e.getMessage());
            System.err.println("Ensure port " + port + " is not occupied by another process.");
            System.exit(1);
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // --- Authentication & Session APIs ---
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/auth/logout", new LogoutHandler());
        server.createContext("/api/auth/me", new MeHandler());
        server.createContext("/api/auth/guest", new GuestLoginHandler());
        server.createContext("/api/auth/profile", new ProfileHandler());
        server.createContext("/api/auth/forgot-password", new ForgotPasswordHandler());
        server.createContext("/api/auth/reset-password", new ResetPasswordHandler());

        // --- Administrator APIs ---
        server.createContext("/api/admin/users", new AdminUsersHandler());
        server.createContext("/api/admin/audit-logs", new AdminAuditLogsHandler());
        server.createContext("/api/admin/system-health", new AdminHealthHandler());
        server.createContext("/api/admin/consistency-check", new AdminConsistencyCheckHandler());

        // --- Dataset & Index Management APIs ---
        server.createContext("/api/datasets", new DatasetsHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/upload", new UploadHandler());
        server.createContext("/api/load-sample", new LoadSampleHandler());
        server.createContext("/api/rebuild", new RebuildHandler());
        server.createContext("/api/dataset/remove", new RemoveDatasetHandler());
        server.createContext("/api/statistics", new StatisticsHandler());
        server.createContext("/api/tree", new TreeHandler());
        server.createContext("/api/tree/traversal", new TreeTraversalHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());
        server.createContext("/api/composition", new CompositionHandler());
        server.createContext("/api/reads", new ReadsHandler());
        server.createContext("/api/read-statistics", new ReadStatisticsHandler());
        server.createContext("/api/export/nucleotides", new ExportNucleotidesHandler());
        server.createContext("/api/export/reads", new ExportReadsHandler());
        server.createContext("/api/export/search", new ExportSearchHandler());
        server.createContext("/api/export/report", new ExportReportHandler());

        // --- Genomic Operations Lab APIs ---
        server.createContext("/api/access", new AccessHandler());
        server.createContext("/api/rank", new RankHandler());
        server.createContext("/api/select", new SelectHandler());
        server.createContext("/api/count-base", new CountBaseHandler());
        server.createContext("/api/range-count", new RangeCountHandler());
        server.createContext("/api/read-count", new ReadCountHandler());
        server.createContext("/api/read-start", new ReadStartHandler());
        server.createContext("/api/read-end", new ReadEndHandler());
        server.createContext("/api/read-of", new ReadOfHandler());
        server.createContext("/api/extract-read", new ExtractReadHandler());
        server.createContext("/api/gc-content", new GcContentHandler());
        server.createContext("/api/find", new FindHandler());

        // --- Static UI ---
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // =========================================================================
    // Security & Session Resolution
    // =========================================================================

    private SessionManager.Session resolveSession(HttpExchange exchange) {
        String token = null;

        // 1. Check Cookie header
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String part : cookieHeader.split(";")) {
                part = part.trim();
                if (part.startsWith("session_token=")) {
                    token = part.substring("session_token=".length()).trim();
                    break;
                }
            }
        }

        // 2. Check Authorization Bearer header
        if (token == null) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }
        }

        SessionManager.Session session = sessionManager.getSession(token);
        if (session == null) {
            // Default to transient guest session so public queries work seamlessly
            session = sessionManager.createGuestSession();
            exchange.getResponseHeaders().add("Set-Cookie", "session_token=" + session.getSessionId() + "; Path=/; SameSite=Lax");
        }
        return session;
    }

    private String getClientIp(HttpExchange exchange) {
        if (exchange.getRemoteAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    private String resolveUserId(SessionManager.Session session) {
        if (session == null) return "guest";
        if (session.isGuest()) {
            return "guest_" + session.getSessionId().substring(0, Math.min(12, session.getSessionId().length()));
        }
        return session.getUser().getId();
    }

    // =========================================================================
    // Authentication Handlers
    // =========================================================================

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, errorJson("Method Not Allowed. Use POST."));
                return;
            }

            String body = readBody(exchange);
            Map<String, String> data = parseJsonMap(body);
            String email = data.get("email");
            String password = data.get("password");
            boolean rememberMe = "true".equalsIgnoreCase(data.get("rememberMe"));
            String ip = getClientIp(exchange);

            if (email == null || password == null) {
                sendJsonResponse(exchange, 400, errorJson("Email and password are required."));
                return;
            }

            if (!sessionManager.checkRateLimit(email) || !sessionManager.checkRateLimit(ip)) {
                int remaining = Math.max(sessionManager.getRemainingLockoutSeconds(email), sessionManager.getRemainingLockoutSeconds(ip));
                userDatabase.recordAudit("LOGIN_LOCKOUT", email, ip, "Rate limit exceeded. Locked out for " + remaining + "s", false);
                sendJsonResponse(exchange, 429, errorJson("Too many failed attempts. Account temporarily locked for " + (remaining / 60 + 1) + " minutes."));
                return;
            }

            User user = userDatabase.findByEmail(email);
            if (user == null || !PasswordHasher.verifyPassword(password, user.getSalt(), user.getPasswordHash())) {
                sessionManager.recordFailedLogin(email);
                sessionManager.recordFailedLogin(ip);
                if (user != null) {
                    user.incrementFailedAttempts();
                    userDatabase.updateUser(user);
                }
                userDatabase.recordAudit("LOGIN_FAILURE", email, ip, "Invalid credentials provided", false);
                sendJsonResponse(exchange, 401, errorJson("Invalid email or password. Please try again."));
                return;
            }

            if (!user.isActive()) {
                userDatabase.recordAudit("LOGIN_BLOCKED", email, ip, "Account is deactivated", false);
                sendJsonResponse(exchange, 403, errorJson("Your account has been deactivated. Please contact an administrator."));
                return;
            }

            // Success
            sessionManager.recordSuccessfulLogin(email);
            sessionManager.recordSuccessfulLogin(ip);
            user.resetFailedAttempts();
            user.setLastLoginAt(System.currentTimeMillis());
            userDatabase.updateUser(user);

            SessionManager.Session session = sessionManager.createSession(user, rememberMe);
            exchange.getResponseHeaders().add("Set-Cookie", sessionManager.buildSetCookieHeader(session.getSessionId(), rememberMe));

            userDatabase.recordAudit("LOGIN_SUCCESS", email, ip, "Logged in as " + user.getCategory().getDisplayName(), true);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Login successful. Welcome back, " + user.getFullName() + "!");
            resp.put("user", user.toPublicProfile());
            resp.put("csrfToken", session.getCsrfToken());
            resp.put("token", session.getSessionId());
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, errorJson("Method Not Allowed. Use POST."));
                return;
            }

            try {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonMap(body);
                String email = data.get("email");
                String fullName = data.get("fullName");
                String password = data.get("password");
                String categoryStr = data.get("category");
                String org = data.get("organization");

                User.Category category = User.Category.fromString(categoryStr);
                User newUser = userDatabase.registerUser(email, fullName, password, category, org);

                // Auto-login upon successful registration
                SessionManager.Session session = sessionManager.createSession(newUser, false);
                exchange.getResponseHeaders().add("Set-Cookie", sessionManager.buildSetCookieHeader(session.getSessionId(), false));

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("authenticated", true);
                resp.put("message", "Registration successful. Welcome to WaveletDNA!");
                resp.put("user", newUser.toPublicProfile());
                resp.put("csrfToken", session.getCsrfToken());
                resp.put("token", session.getSessionId());
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            if (session != null) {
                if (!session.isGuest()) {
                    userDatabase.recordAudit("LOGOUT", session.getUser().getEmail(), getClientIp(exchange), "User logged out", true);
                }
                sessionManager.invalidateSession(session.getSessionId());
            }
            exchange.getResponseHeaders().add("Set-Cookie", sessionManager.buildClearCookieHeader());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Logged out successfully.");
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class MeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("authenticated", !session.isGuest());
            resp.put("user", session.getUser().toPublicProfile());
            resp.put("csrfToken", session.getCsrfToken());
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class GuestLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = sessionManager.createGuestSession();
            exchange.getResponseHeaders().add("Set-Cookie", sessionManager.buildSetCookieHeader(session.getSessionId(), false));

            userDatabase.recordAudit("GUEST_ACCESS", "guest", getClientIp(exchange), "Guest session created", true);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Guest mode enabled. Public sample dataset loaded.");
            resp.put("user", session.getUser().toPublicProfile());
            resp.put("csrfToken", session.getCsrfToken());
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class ForgotPasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            String body = readBody(exchange);
            Map<String, String> data = parseJsonMap(body);
            String email = data.get("email");

            String token = userDatabase.createPasswordResetToken(email);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "If an account matches that email address, password reset instructions have been generated.");
            if (token != null) {
                // Return token in dev environment for easy demonstration
                resp.put("devResetToken", token);
                resp.put("note", "[DEV MODE] Reset token generated: " + token + " (valid for 15 mins)");
            }
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class ResetPasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            String body = readBody(exchange);
            Map<String, String> data = parseJsonMap(body);
            String token = data.get("token");
            String newPassword = data.get("newPassword");

            boolean ok = userDatabase.resetPasswordWithToken(token, newPassword);
            if (ok) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("message", "Your password has been reset successfully. Please log in with your new password.");
                sendJsonResponse(exchange, 200, toJson(resp));
            } else {
                sendJsonResponse(exchange, 400, errorJson("Invalid or expired password reset token."));
            }
        }
    }

    private class ProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }

            SessionManager.Session session = resolveSession(exchange);
            if (session == null || session.isGuest()) {
                sendJsonResponse(exchange, 401, errorJson("Authentication required. Please sign in."));
                return;
            }

            User user = session.getUser();

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("user", user.toPublicProfile());
                sendJsonResponse(exchange, 200, toJson(resp));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) || "PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);
                    Map<String, String> data = parseJsonMap(body);

                    boolean updated = false;

                    // Update Name
                    if (data.containsKey("fullName")) {
                        String name = data.get("fullName");
                        if (name != null && !name.trim().isEmpty()) {
                            user.setFullName(name.trim());
                            updated = true;
                        }
                    }

                    // Update Organization
                    if (data.containsKey("organization")) {
                        String org = data.get("organization");
                        user.setOrganization(org != null ? org.trim() : "");
                        updated = true;
                    }

                    // Update Password
                    if (data.containsKey("newPassword")) {
                        String newPassword = data.get("newPassword");
                        String currentPassword = data.get("currentPassword");

                        if (newPassword != null && !newPassword.trim().isEmpty()) {
                            if (currentPassword == null || currentPassword.isEmpty()) {
                                sendJsonResponse(exchange, 400, errorJson("Current password is required to set a new password."));
                                return;
                            }

                            if (!PasswordHasher.verifyPassword(currentPassword, user.getSalt(), user.getPasswordHash())) {
                                sendJsonResponse(exchange, 400, errorJson("Current password is incorrect."));
                                return;
                            }

                            if (newPassword.length() < 8) {
                                sendJsonResponse(exchange, 400, errorJson("New password must be at least 8 characters long."));
                                return;
                            }

                            PasswordHasher.HashResult hashRes = PasswordHasher.hashPassword(newPassword);
                            user.setPassword(hashRes.hashHex, hashRes.saltHex);
                            userDatabase.recordAudit("PASSWORD_CHANGE", user.getEmail(), getClientIp(exchange), "User updated password from profile", true);
                            updated = true;
                        }
                    }

                    if (updated) {
                        userDatabase.updateUser(user);
                        userDatabase.recordAudit("PROFILE_UPDATE", user.getEmail(), getClientIp(exchange), "User updated profile information", true);
                    }

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("message", "Profile updated successfully.");
                    resp.put("user", user.toPublicProfile());
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, errorJson("Failed to update profile: " + e.getMessage()));
                }
                return;
            }

            sendJsonResponse(exchange, 405, errorJson("Method Not Allowed. Use GET or POST."));
        }
    }

    // =========================================================================
    // Administrator Handlers (RBAC Protected)
    // =========================================================================

    private class AdminUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            if (!session.getUser().isAdmin()) {
                sendJsonResponse(exchange, 403, errorJson("Access denied. Administrator privileges required."));
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<User> users = userDatabase.getAllUsers();
                List<Map<String, Object>> userList = new ArrayList<>();
                for (User u : users) {
                    userList.add(u.toPublicProfile());
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("totalUsers", users.size());
                resp.put("users", userList);
                sendJsonResponse(exchange, 200, toJson(resp));

            } else if ("PATCH".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonMap(body);
                String userId = data.get("id");
                String activeStr = data.get("active");
                String roleStr = data.get("role");

                User target = userDatabase.findById(userId);
                if (target == null) {
                    sendJsonResponse(exchange, 404, errorJson("User not found: " + userId));
                    return;
                }

                if (activeStr != null) {
                    target.setActive("true".equalsIgnoreCase(activeStr));
                }
                if (roleStr != null) {
                    try {
                        target.setRole(User.Role.valueOf(roleStr));
                    } catch (Exception ignored) {
                    }
                }
                userDatabase.updateUser(target);
                userDatabase.recordAudit("ADMIN_UPDATE_USER", session.getUser().getEmail(), getClientIp(exchange),
                        "Updated account " + target.getEmail() + " (active=" + target.isActive() + ")", true);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("message", "User account updated successfully.");
                resp.put("user", target.toPublicProfile());
                sendJsonResponse(exchange, 200, toJson(resp));

            } else {
                sendJsonResponse(exchange, 405, errorJson("Method Not Allowed"));
            }
        }
    }

    private class AdminAuditLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            if (!session.getUser().isAdmin()) {
                sendJsonResponse(exchange, 403, errorJson("Access denied. Administrator privileges required."));
                return;
            }

            List<Map<String, Object>> logs = userDatabase.getRecentAuditLogs(50);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("logs", logs);
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class AdminHealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            if (!session.getUser().isAdmin()) {
                sendJsonResponse(exchange, 403, errorJson("Access denied. Administrator privileges required."));
                return;
            }

            Runtime rt = Runtime.getRuntime();
            Map<String, Object> health = new LinkedHashMap<>();
            health.put("status", "HEALTHY");
            health.put("jvmFreeMemoryMb", Math.round((rt.freeMemory() / (1024.0 * 1024.0)) * 10.0) / 10.0);
            health.put("jvmTotalMemoryMb", Math.round((rt.totalMemory() / (1024.0 * 1024.0)) * 10.0) / 10.0);
            health.put("jvmMaxMemoryMb", Math.round((rt.maxMemory() / (1024.0 * 1024.0)) * 10.0) / 10.0);
            health.put("availableProcessors", rt.availableProcessors());
            health.put("javaVersion", System.getProperty("java.version"));
            health.put("osName", System.getProperty("os.name"));

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("health", health);
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class AdminConsistencyCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            if (!session.getUser().isAdmin()) {
                sendJsonResponse(exchange, 403, errorJson("Access denied. Administrator privileges required."));
                return;
            }

            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            Map<String, Object> result = im.runConsistencyCheck();
            userDatabase.recordAudit("INDEX_CONSISTENCY_CHECK", session.getUser().getEmail(), getClientIp(exchange),
                    "Consistency check status: " + result.get("status") + " on " + im.getDatasetName(), (Boolean) result.get("allPassed"));
            sendJsonResponse(exchange, 200, toJson(result));
        }
    }

    // =========================================================================
    // Genomic Index & Operation Handlers (Isolated Per User Session!)
    // =========================================================================

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("indexLoaded", im.isIndexLoaded());
            resp.put("datasetName", im.getDatasetName());
            resp.put("datasetSource", im.getDatasetSource());
            resp.put("lastBuildTimestamp", im.getLastBuildTimestamp());
            resp.put("totalReads", im.getTotalReadCount());
            resp.put("totalSymbols", im.getTotalIndexedSymbols());
            resp.put("totalDnaBases", im.getTotalDnaBases());
            resp.put("user", session.getUser().toPublicProfile());
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, errorJson("Method Not Allowed. Use POST."));
                return;
            }

            SessionManager.Session session = resolveSession(exchange);
            String userId = resolveUserId(session);
            try {
                String requestBody = readBody(exchange);
                String fastqContent = extractFastqContent(requestBody);
                String datasetName = "Uploaded_" + System.currentTimeMillis() + ".fastq";

                DatasetRegistry.DatasetEntry entry = datasetRegistry.saveDataset(userId, datasetName, "Uploaded File", fastqContent);
                IndexManager im = session.getIndexManager();
                im.loadFromFastqContent(entry.getName(), fastqContent);
                session.setActiveDatasetId(entry.getId());

                if (!session.isGuest()) {
                    userDatabase.recordAudit("FASTQ_UPLOAD", session.getUser().getEmail(), getClientIp(exchange),
                            "Uploaded FASTQ: " + im.getTotalReadCount() + " reads, " + im.getTotalIndexedSymbols() + " symbols", true);
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("message", "FASTQ file successfully parsed and prepared for analysis.");
                resp.put("datasetName", entry.getName());
                resp.put("datasetId", entry.getId());
                resp.put("stats", im.getStatistics());
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Throwable t) {
                String msg = t.getMessage();
                if (msg == null || msg.trim().isEmpty()) {
                    msg = (t instanceof OutOfMemoryError) ? "Server ran out of available memory." : t.getClass().getSimpleName();
                }
                sendJsonResponse(exchange, 400, errorJson(msg));
            }
        }
    }

    private static String extractFastqContent(String rawBody) {
        if (rawBody == null) return "";
        if (rawBody.contains("------WebKitFormBoundary") || rawBody.contains("Content-Disposition:")) {
            int headerEnd = rawBody.indexOf("\r\n\r\n");
            if (headerEnd == -1) headerEnd = rawBody.indexOf("\n\n");
            if (headerEnd != -1) {
                int contentStart = headerEnd + (rawBody.charAt(headerEnd) == '\r' ? 4 : 2);
                int boundaryEnd = rawBody.lastIndexOf("------");
                if (boundaryEnd > contentStart) {
                    return rawBody.substring(contentStart, boundaryEnd).trim();
                }
                return rawBody.substring(contentStart).trim();
            }
        }
        return rawBody.trim();
    }

    private class DatasetsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            String userId = resolveUserId(session);
            String path = exchange.getRequestURI().getPath();

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (path.endsWith("/download")) {
                    Map<String, String> qp = parseQueryParams(exchange.getRequestURI().getQuery());
                    String dsId = qp.get("id");
                    if (dsId == null || dsId.isEmpty()) {
                        dsId = session.getActiveDatasetId();
                    }
                    DatasetRegistry.DatasetEntry entry = datasetRegistry.findDataset(userId, dsId);
                    String filename = (entry != null) ? entry.getName() : "dataset.fastq";
                    String content;
                    try {
                        content = datasetRegistry.getFastqContent(userId, dsId);
                    } catch (Exception e) {
                        content = im.getLastFastqContent();
                    }
                    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }

                if (path.contains("/reads/")) {
                    String sub = path.substring(path.indexOf("/reads/") + 7);
                    boolean isRecord = sub.endsWith("/record");
                    if (isRecord) {
                        sub = sub.substring(0, sub.length() - 7);
                    }
                    if (sub.endsWith("/")) sub = sub.substring(0, sub.length() - 1);
                    int k = im.resolveReadIndex(sub);
                    if (k < 0) {
                        sendJsonResponse(exchange, 404, errorJson("Read '" + sub + "' not found"));
                        return;
                    }
                    Map<String, Object> stats = im.getReadStatistics(k);
                    if (isRecord) {
                        Map<String, Object> recResp = new LinkedHashMap<>();
                        recResp.put("success", true);
                        recResp.put("readId", sub);
                        recResp.put("readIndex", k);
                        recResp.put("header", stats.get("header"));
                        recResp.put("sequence", stats.get("sequence"));
                        recResp.put("plusLine", stats.get("plusLine"));
                        recResp.put("quality", stats.get("quality"));
                        recResp.put("rawRecord", stats.get("rawRecord"));
                        recResp.put("avgPhredScore", stats.get("avgPhredScore"));
                        recResp.put("phredScores", stats.get("phredScores"));
                        sendJsonResponse(exchange, 200, toJson(recResp));
                    } else {
                        Map<String, Object> baseResp = new LinkedHashMap<>();
                        baseResp.put("success", true);
                        baseResp.put("readId", sub);
                        baseResp.put("readIndex", k);
                        baseResp.put("sequence", stats.get("sequence"));
                        baseResp.put("length", stats.get("length"));
                        baseResp.put("startPos", stats.get("startPos"));
                        baseResp.put("endPos", stats.get("endPos"));
                        sendJsonResponse(exchange, 200, toJson(baseResp));
                    }
                    return;
                }

                if (path.contains("/composition")) {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    String symbol = params.get("symbol");
                    Integer s = null;
                    Integer e = null;
                    if (params.containsKey("s")) {
                        try { s = Integer.parseInt(params.get("s")); } catch (Exception ignored) {}
                    }
                    if (params.containsKey("e")) {
                        try { e = Integer.parseInt(params.get("e")); } catch (Exception ignored) {}
                    }
                    Map<String, Object> comp = im.getBaseComposition(symbol, s, e);
                    sendJsonResponse(exchange, 200, toJson(comp));
                    return;
                }

                String activeId = session.getActiveDatasetId();
                if (activeId == null || activeId.isEmpty()) activeId = "demo_report";

                List<DatasetRegistry.DatasetEntry> allDs = datasetRegistry.getAllDatasetsForUser(userId);
                List<Map<String, Object>> datasets = new ArrayList<>();
                for (DatasetRegistry.DatasetEntry ds : allDs) {
                    datasets.add(ds.toMap(ds.getId().equalsIgnoreCase(activeId)));
                }

                String activeName = im.getDatasetName();
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);

                Map<String, Object> activeInfo = new LinkedHashMap<>();
                activeInfo.put("id", activeId);
                activeInfo.put("name", activeName);
                activeInfo.put("source", im.getDatasetSource());
                activeInfo.put("totalReads", im.getTotalReadCount());
                activeInfo.put("totalSymbols", im.getTotalIndexedSymbols());
                activeInfo.put("totalBases", im.getTotalDnaBases());
                activeInfo.put("lastBuildTimestamp", im.getLastBuildTimestamp());
                activeInfo.put("isLoaded", im.isIndexLoaded());
                if (im.isIndexLoaded()) {
                    activeInfo.put("statistics", im.getStatistics());
                }
                resp.put("activeDataset", activeInfo);
                resp.put("datasets", datasets);
                sendJsonResponse(exchange, 200, toJson(resp));
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readBody(exchange);
                String trimmed = body.trim();
                Map<String, String> jsonMap = null;
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    jsonMap = parseJsonMap(trimmed);
                }

                if (path.endsWith("/active") || (jsonMap != null && (jsonMap.containsKey("datasetId") || (jsonMap.containsKey("id") && !jsonMap.containsKey("content"))))) {
                    String datasetId = (jsonMap != null) ? jsonMap.get("datasetId") : null;
                    if (datasetId == null || datasetId.isEmpty()) {
                        datasetId = (jsonMap != null) ? jsonMap.get("id") : null;
                    }
                    try {
                        DatasetRegistry.DatasetEntry target = datasetRegistry.findDataset(userId, datasetId);
                        if (target == null) {
                            sendJsonResponse(exchange, 404, errorJson("Dataset not found: " + datasetId));
                            return;
                        }
                        String fastq = datasetRegistry.getFastqContent(userId, datasetId);
                        im.loadFromFastqContent(target.getName(), fastq);
                        session.setActiveDatasetId(target.getId());

                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("success", true);
                        resp.put("message", "Switched active dataset to " + im.getDatasetName());
                        resp.put("activeDataset", im.getStatistics());
                        sendJsonResponse(exchange, 200, toJson(resp));
                    } catch (Exception e) {
                        sendJsonResponse(exchange, 400, errorJson("Failed to switch dataset: " + e.getMessage()));
                    }
                    return;
                }

                // Ingestion endpoint (handles both file uploads and pasted text)
                try {
                    String fastqContent = null;
                    String datasetName = null;
                    if (jsonMap != null) {
                        fastqContent = jsonMap.get("content");
                        datasetName = jsonMap.get("name");
                    }
                    if (fastqContent == null || fastqContent.isEmpty()) {
                        fastqContent = extractFastqContent(body);
                    }

                    if (datasetName == null || datasetName.trim().isEmpty()) {
                        datasetName = "Dataset_" + System.currentTimeMillis() + ".fastq";
                    } else {
                        datasetName = datasetName.trim();
                        if (!datasetName.endsWith(".fastq") && !datasetName.endsWith(".fq") && !datasetName.endsWith(".txt")) {
                            datasetName += ".fastq";
                        }
                    }

                    String source = (jsonMap != null && jsonMap.containsKey("content")) ? "Pasted Text" : "Uploaded File";
                    DatasetRegistry.DatasetEntry entry = datasetRegistry.saveDataset(userId, datasetName, source, fastqContent);
                    im.loadFromFastqContent(entry.getName(), fastqContent);
                    session.setActiveDatasetId(entry.getId());

                    if (!session.isGuest()) {
                        userDatabase.recordAudit("FASTQ_UPLOAD", session.getUser().getEmail(), getClientIp(exchange),
                                "Ingested dataset: " + im.getTotalReadCount() + " reads, " + im.getTotalIndexedSymbols() + " symbols", true);
                    }

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("message", "Your dataset has been added successfully and is ready for analysis.");
                    resp.put("datasetName", entry.getName());
                    resp.put("datasetId", entry.getId());
                    resp.put("stats", im.getStatistics());
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Throwable t) {
                    String msg = t.getMessage();
                    if (msg == null || msg.trim().isEmpty()) {
                        msg = (t instanceof OutOfMemoryError) ? "Server ran out of available memory." : t.getClass().getSimpleName();
                    }
                    sendJsonResponse(exchange, 400, errorJson(msg));
                }
                return;
            }

            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, String> qp = parseQueryParams(exchange.getRequestURI().getQuery());
                    String dsId = qp.get("id");
                    if (dsId == null || dsId.isEmpty()) {
                        String body = readBody(exchange);
                        if (body != null && !body.trim().isEmpty()) {
                            Map<String, String> data = parseJsonMap(body);
                            dsId = data.get("datasetId");
                            if (dsId == null) dsId = data.get("id");
                        }
                    }

                    if (dsId == null || dsId.isEmpty()) {
                        dsId = session.getActiveDatasetId();
                    }

                    boolean deleted = datasetRegistry.deleteDataset(userId, dsId);
                    if (dsId != null && dsId.equalsIgnoreCase(session.getActiveDatasetId())) {
                        im.loadDemoReportDataset();
                        session.setActiveDatasetId("demo_report");
                    }

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("deleted", deleted);
                    resp.put("message", deleted ? "Dataset deleted successfully." : "Active dataset reset to default report demo.");
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, errorJson("Delete failed: " + e.getMessage()));
                }
                return;
            }

            sendJsonResponse(exchange, 405, errorJson("Method Not Allowed"));
        }
    }

    private class LoadSampleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, errorJson("Method Not Allowed. Use POST."));
                return;
            }
            try {
                SessionManager.Session session = resolveSession(exchange);
                IndexManager im = session.getIndexManager();
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                if ("extended".equalsIgnoreCase(params.get("type"))) {
                    im.loadExtendedSampleDataset();
                    session.setActiveDatasetId("extended");
                } else {
                    im.loadSampleDataset();
                    session.setActiveDatasetId("sample");
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("message", "Sample FASTQ dataset loaded successfully.");
                resp.put("stats", im.getStatistics());
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson("Failed to load sample dataset: " + e.getMessage()));
            }
        }
    }

    private class RebuildHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, errorJson("Method Not Allowed. Use POST."));
                return;
            }
            try {
                SessionManager.Session session = resolveSession(exchange);
                IndexManager im = session.getIndexManager();
                im.rebuildIndex();

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("message", "Wavelet tree rebuilt successfully.");
                resp.put("stats", im.getStatistics());
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson("Rebuild failed: " + e.getMessage()));
            }
        }
    }

    private class RemoveDatasetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, errorJson("Method Not Allowed. Use POST."));
                return;
            }
            try {
                SessionManager.Session session = resolveSession(exchange);
                IndexManager im = session.getIndexManager();
                im.removeDataset();
                session.setActiveDatasetId(null);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("message", "Dataset and Wavelet Tree index removed successfully.");
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson("Failed to remove dataset: " + e.getMessage()));
            }
        }
    }

    private class StatisticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();

            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded. Load sample dataset or upload FASTQ."));
                return;
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("statistics", im.getStatistics());
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class TreeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();

            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            WaveletTree tree = im.getWaveletTree();
            Map<String, Object> treeJson = serializeSubtree(tree.getRoot());

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("sequenceLength", tree.length());
            resp.put("nodeCount", tree.getNodeCount());
            resp.put("height", tree.getHeight());
            resp.put("alphabet", Alphabet.SYMBOLS);
            resp.put("tree", treeJson);
            sendJsonResponse(exchange, 200, toJson(resp));
        }

        private Map<String, Object> serializeSubtree(WaveletTreeNode node) {
            if (node == null) return null;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", node.getId());
            map.put("alphabetLabel", node.getAlphabetLabel());
            map.put("depth", node.getDepth());
            map.put("isLeaf", node.isLeaf());

            if (node.isLeaf()) {
                map.put("leafSymbol", String.valueOf(node.getLeafSymbol()));
            } else {
                BitVector bv = node.getBitVector();
                map.put("length", bv.length());
                map.put("ones", bv.countOnes());
                map.put("zeros", bv.countZeros());
                map.put("bitString", bv.toBitString(64));
                map.put("left", serializeSubtree(node.getLeft()));
                map.put("right", serializeSubtree(node.getRight()));
            }
            return map;
        }
    }

    private class AccessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String iStr = params.get("i");
            if (iStr == null) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'i' (0-based index)"));
                return;
            }

            try {
                int index = Integer.parseInt(iStr.trim());
                WaveletTree.AccessResult result = im.getWaveletTree().accessWithTrace(index);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("operation", "ACCESS(" + index + ")");
                resp.put("index", index);
                resp.put("symbol", String.valueOf(result.symbol));
                resp.put("executionTimeMicros", result.executionTimeNanos / 1000.0);
                resp.put("timeComplexity", "O(log sigma) = 3 steps");
                resp.put("steps", result.steps);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson("ACCESS error: " + e.getMessage()));
            }
        }
    }

    private class RankHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String cStr = params.get("c");
            String iStr = params.get("i");
            if (cStr == null || iStr == null || cStr.isEmpty()) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameters 'c' and 'i'"));
                return;
            }

            try {
                char symbol = cStr.charAt(0);
                int index = Integer.parseInt(iStr.trim());
                WaveletTree.RankResult result = im.getWaveletTree().rankWithTrace(symbol, index);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("operation", "RANK(" + symbol + ", " + index + ")");
                resp.put("symbol", String.valueOf(symbol));
                resp.put("index", index);
                resp.put("count", result.count);
                resp.put("executionTimeMicros", result.executionTimeNanos / 1000.0);
                resp.put("timeComplexity", "O(log sigma) = 3 steps");
                resp.put("steps", result.steps);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson("RANK error: " + e.getMessage()));
            }
        }
    }

    private class SelectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String cStr = params.get("c");
            String kStr = params.get("k");
            if (cStr == null || kStr == null || cStr.isEmpty()) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameters 'c' and 'k'"));
                return;
            }

            try {
                char symbol = cStr.charAt(0);
                int k = Integer.parseInt(kStr.trim());
                WaveletTree.SelectResult result = im.getWaveletTree().selectWithTrace(symbol, k);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("operation", "SELECT(" + symbol + ", " + k + ")");
                resp.put("symbol", String.valueOf(symbol));
                resp.put("occurrence", k);
                resp.put("globalPosition", result.globalPosition);
                resp.put("executionTimeMicros", result.executionTimeNanos / 1000.0);
                resp.put("timeComplexity", "O(log sigma * log n)");
                resp.put("steps", result.steps);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson("SELECT error: " + e.getMessage()));
            }
        }
    }

    private class CountBaseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String cStr = params.get("c");
            if (cStr == null || cStr.isEmpty()) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'c'"));
                return;
            }
            try {
                char c = cStr.charAt(0);
                long start = System.nanoTime();
                int count = im.getQueryEngine().countBase(c);
                long elapsed = System.nanoTime() - start;

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("operation", "COUNT-BASE(" + c + ")");
                resp.put("symbol", String.valueOf(c));
                resp.put("count", count);
                resp.put("formula", "RANK('" + c + "', " + im.getWaveletTree().length() + ")");
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class RangeCountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String cStr = params.get("c");
            String sStr = params.get("start");
            String eStr = params.get("end");
            if (cStr == null || sStr == null || eStr == null || cStr.isEmpty()) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameters 'c', 'start', 'end'"));
                return;
            }
            try {
                char c = cStr.charAt(0);
                int s = Integer.parseInt(sStr.trim());
                int e = Integer.parseInt(eStr.trim());
                long start = System.nanoTime();
                int count = im.getQueryEngine().rangeCount(c, s, e);
                long elapsed = System.nanoTime() - start;

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("operation", "RANGE-COUNT(" + c + ", " + s + ", " + e + ")");
                resp.put("symbol", String.valueOf(c));
                resp.put("start", s);
                resp.put("end", e);
                resp.put("count", count);
                resp.put("formula", "RANK('" + c + "', " + e + ") - RANK('" + c + "', " + s + ")");
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class ReadCountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            try {
                long start = System.nanoTime();
                int count = im.getQueryEngine().readCount();
                long elapsed = System.nanoTime() - start;

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("count", count);
                resp.put("formula", "RANK('#', " + im.getWaveletTree().length() + ")");
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class ReadStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String kStr = params.get("k");
            if (kStr == null) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'k'"));
                return;
            }
            try {
                int k = Integer.parseInt(kStr.trim());
                long start = System.nanoTime();
                int pos = im.getQueryEngine().readStart(k);
                long elapsed = System.nanoTime() - start;

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("readIndex", k);
                resp.put("startPosition", pos);
                resp.put("formula", k == 0 ? "0" : "SELECT('#', " + k + ") + 1");
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class ReadEndHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String kStr = params.get("k");
            if (kStr == null) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'k'"));
                return;
            }
            try {
                int k = Integer.parseInt(kStr.trim());
                long start = System.nanoTime();
                int pos = im.getQueryEngine().readEnd(k);
                long elapsed = System.nanoTime() - start;

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("readIndex", k);
                resp.put("endPosition", pos);
                resp.put("formula", "SELECT('#', " + (k + 1) + ")");
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class ReadOfHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String pStr = params.get("pos");
            if (pStr == null) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'pos'"));
                return;
            }
            try {
                int pos = Integer.parseInt(pStr.trim());
                long start = System.nanoTime();
                boolean isSep = im.getQueryEngine().isSeparator(pos);
                int readIdx = im.getQueryEngine().readOf(pos);
                long elapsed = System.nanoTime() - start;

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("position", pos);
                resp.put("isSeparator", isSep);
                resp.put("readIndex", readIdx);
                resp.put("note", isSep ? "Position " + pos + " is '#' delimiter" : "Belongs to read " + readIdx);
                resp.put("formula", "RANK('#', " + pos + ")");
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class ExtractReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String kStr = params.get("k");
            if (kStr == null) kStr = params.get("query");
            if (kStr == null) kStr = params.get("readId");
            if (kStr == null) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'k' or 'readId'"));
                return;
            }
            try {
                int k = im.resolveReadIndex(kStr.trim());
                if (k < 0) {
                    sendJsonResponse(exchange, 404, errorJson("Read '" + kStr + "' not found"));
                    return;
                }
                long start = System.nanoTime();
                String readSeq = im.getQueryEngine().extractRead(k);
                long elapsed = System.nanoTime() - start;

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("readIndex", k);
                resp.put("sequence", readSeq);
                resp.put("length", readSeq.length());
                resp.put("startPos", im.getQueryEngine().readStart(k));
                resp.put("endPos", im.getQueryEngine().readEnd(k));
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class GcContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String kStr = params.get("k");
            if (kStr == null) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'k'"));
                return;
            }
            try {
                int k = Integer.parseInt(kStr.trim());
                long start = System.nanoTime();
                double gc = im.getQueryEngine().gcContent(k);
                long elapsed = System.nanoTime() - start;

                int s = im.getQueryEngine().readStart(k);
                int e = im.getQueryEngine().readEnd(k);
                int g = im.getQueryEngine().rangeCount(Alphabet.G, s, e);
                int c = im.getQueryEngine().rangeCount(Alphabet.C, s, e);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("readIndex", k);
                resp.put("gcPercentage", Math.round(gc * 100.0) / 100.0);
                resp.put("gCount", g);
                resp.put("cCount", c);
                resp.put("readLength", e - s);
                resp.put("formula", "(RANGE('G', " + s + ", " + e + ") + RANGE('C', " + s + ", " + e + ")) / " + (e - s) + " * 100");
                resp.put("executionTimeMicros", elapsed / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class FindHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String pattern = params.get("pattern");
            if (pattern == null || pattern.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'pattern'"));
                return;
            }
            try {
                pattern = pattern.trim();
                QueryEngine.FindResult result = im.getQueryEngine().find(pattern);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("pattern", result.pattern);
                resp.put("matchCount", result.matches.size());
                resp.put("rarestChar", String.valueOf(result.rarestChar));
                resp.put("rarestCharIndex", result.rarestCharIndexInPattern);
                resp.put("rarestCharTotalCount", result.rarestCharTotalCount);
                resp.put("patternCharFrequencies", result.patternCharFrequencies);
                resp.put("matches", result.matches);
                resp.put("candidates", result.candidates);
                resp.put("executionTimeMicros", result.executionTimeNanos / 1000.0);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson("FIND error: " + e.getMessage()));
            }
        }
    }

    private class AnalyticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            Map<String, Object> an = im.getAnalytics();
            resp.put("analytics", an);
            if (an != null) {
                resp.putAll(an);
            }
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class CompositionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String symbol = params.get("symbol");
            Integer s = null;
            Integer e = null;

            // Check if single read scope is specified (by readIndex or readId)
            String readQuery = params.get("readIndex");
            if (readQuery == null) readQuery = params.get("readId");
            if (readQuery != null && !readQuery.trim().isEmpty()) {
                int k = im.resolveReadIndex(readQuery.trim());
                if (k >= 0) {
                    s = im.getQueryEngine().readStart(k);
                    e = im.getQueryEngine().readEnd(k);
                }
            }

            if (s == null && params.containsKey("s")) {
                try { s = Integer.parseInt(params.get("s")); } catch (Exception ignored) {}
            }
            if (e == null && params.containsKey("e")) {
                try { e = Integer.parseInt(params.get("e")); } catch (Exception ignored) {}
            }

            try {
                Map<String, Object> comp = im.getBaseComposition(symbol, s, e);
                sendJsonResponse(exchange, 200, toJson(comp));
            } catch (Exception ex) {
                sendJsonResponse(exchange, 400, errorJson(ex.getMessage()));
            }
        }
    }

    private class ReadStatisticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String kStr = params.get("k");
            if (kStr == null) kStr = params.get("query");
            if (kStr == null) kStr = params.get("readId");
            if (kStr == null) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'k' or 'readId'"));
                return;
            }
            try {
                int k = im.resolveReadIndex(kStr.trim());
                if (k < 0) {
                    sendJsonResponse(exchange, 404, errorJson("Read '" + kStr + "' not found"));
                    return;
                }
                Map<String, Object> stats = im.getReadStatistics(k);
                if (stats == null) {
                    sendJsonResponse(exchange, 404, errorJson("Read index " + k + " out of bounds."));
                    return;
                }
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("read", stats);
                resp.put("readStatistics", stats);
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class ReadsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String query = params.get("query");
            if (query == null) query = params.get("readId");
            if (query == null) query = params.get("k");

            // If a specific read query is provided, return its full record
            if (query != null && !query.trim().isEmpty()) {
                int k = im.resolveReadIndex(query.trim());
                if (k < 0) {
                    sendJsonResponse(exchange, 404, errorJson("Read '" + query + "' not found."));
                    return;
                }
                Map<String, Object> stats = im.getReadStatistics(k);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("read", stats);
                sendJsonResponse(exchange, 200, toJson(resp));
                return;
            }

            // Otherwise return paginated reads table
            int page = 1;
            int pageSize = 10;
            if (params.containsKey("page")) {
                try { page = Math.max(1, Integer.parseInt(params.get("page"))); } catch (Exception ignored) {}
            }
            if (params.containsKey("pageSize")) {
                try { pageSize = Math.max(1, Integer.parseInt(params.get("pageSize"))); } catch (Exception ignored) {}
            }

            int totalReads = im.getTotalReadCount();
            int totalPages = totalReads == 0 ? 0 : (int) Math.ceil((double) totalReads / pageSize);
            List<Map<String, Object>> reads = im.getReadsSummary(page, pageSize);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("page", page);
            resp.put("pageSize", pageSize);
            resp.put("totalReads", totalReads);
            resp.put("totalPages", totalPages);
            resp.put("reads", reads);
            sendJsonResponse(exchange, 200, toJson(resp));
        }
    }

    private class TreeTraversalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String op = params.get("op");
            if (op == null || op.isEmpty()) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'op' (access, rank, select)"));
                return;
            }
            try {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("operation", op.toUpperCase());
                if ("access".equalsIgnoreCase(op)) {
                    String iStr = params.get("i");
                    if (iStr == null) throw new IllegalArgumentException("Missing parameter 'i'");
                    int index = Integer.parseInt(iStr.trim());
                    WaveletTree.AccessResult res = im.getWaveletTree().accessWithTrace(index);
                    resp.put("index", index);
                    resp.put("symbol", String.valueOf(res.symbol));
                    resp.put("steps", res.steps);
                } else if ("rank".equalsIgnoreCase(op)) {
                    String cStr = params.get("c");
                    String iStr = params.get("i");
                    if (cStr == null || iStr == null || cStr.isEmpty()) throw new IllegalArgumentException("Missing 'c' or 'i'");
                    char c = cStr.charAt(0);
                    int index = Integer.parseInt(iStr.trim());
                    WaveletTree.RankResult res = im.getWaveletTree().rankWithTrace(c, index);
                    resp.put("symbol", String.valueOf(c));
                    resp.put("index", index);
                    resp.put("count", res.count);
                    resp.put("steps", res.steps);
                } else if ("select".equalsIgnoreCase(op)) {
                    String cStr = params.get("c");
                    String kStr = params.get("k");
                    if (cStr == null || kStr == null || cStr.isEmpty()) throw new IllegalArgumentException("Missing 'c' or 'k'");
                    char c = cStr.charAt(0);
                    int k = Integer.parseInt(kStr.trim());
                    WaveletTree.SelectResult res = im.getWaveletTree().selectWithTrace(c, k);
                    resp.put("symbol", String.valueOf(c));
                    resp.put("k", k);
                    resp.put("globalPosition", res.globalPosition);
                    resp.put("steps", res.steps);
                } else {
                    throw new IllegalArgumentException("Unsupported traversal op: " + op);
                }
                sendJsonResponse(exchange, 200, toJson(resp));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, errorJson(e.getMessage()));
            }
        }
    }

    private class ExportNucleotidesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            StringBuilder csv = new StringBuilder("Symbol,Name,Count,Percentage\n");
            Map<String, Object> analytics = im.getAnalytics();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> compList = (List<Map<String, Object>>) analytics.get("composition");
            if (compList != null) {
                for (Map<String, Object> item : compList) {
                    String sym = String.valueOf(item.get("symbol"));
                    String name = "#".equals(sym) ? "Delimiter (#)" : ("N".equals(sym) ? "Ambiguous (N)" : sym);
                    csv.append(sym).append(",\"").append(name).append("\",")
                       .append(item.get("count")).append(",")
                       .append(item.get("percentage")).append("\n");
                }
            }
            sendCsvResponse(exchange, "nucleotide_composition.csv", csv.toString());
        }
    }

    private class ExportReadsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            StringBuilder csv = new StringBuilder("Read_Index,Header,Start_Pos,End_Pos,Length,GC_Percent,Sequence\n");
            int totalReads = im.getTotalReadCount();
            for (int k = 0; k < totalReads; k++) {
                Map<String, Object> r = im.getReadStatistics(k);
                if (r != null) {
                    csv.append(r.get("readIndex")).append(",")
                       .append("\"").append(r.get("header")).append("\",")
                       .append(r.get("startPos")).append(",")
                       .append(r.get("endPos")).append(",")
                       .append(r.get("length")).append(",")
                       .append(r.get("gcPercentage")).append(",")
                       .append("\"").append(r.get("sequence")).append("\"\n");
                }
            }
            sendCsvResponse(exchange, "read_statistics.csv", csv.toString());
        }
    }

    private class ExportSearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String pattern = params.get("pattern");
            if (pattern == null || pattern.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, errorJson("Missing parameter 'pattern'"));
                return;
            }
            QueryEngine.FindResult result = im.getQueryEngine().find(pattern.trim());
            StringBuilder csv = new StringBuilder("Pattern,Match_Index,Read_Index,Offset_In_Read,Global_Position,Read_Sequence\n");
            int idx = 1;
            for (Match m : result.matches) {
                String readSeq = im.getQueryEngine().extractRead(m.getReadIndex());
                csv.append("\"").append(pattern.trim()).append("\",")
                   .append(idx++).append(",")
                   .append(m.getReadIndex()).append(",")
                   .append(m.getOffsetInRead()).append(",")
                   .append(m.getGlobalPosition()).append(",")
                   .append("\"").append(readSeq).append("\"\n");
            }
            sendCsvResponse(exchange, "search_results_" + pattern.trim() + ".csv", csv.toString());
        }
    }

    private class ExportReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsOk(exchange);
                return;
            }
            SessionManager.Session session = resolveSession(exchange);
            IndexManager im = session.getIndexManager();
            if (!im.isIndexLoaded()) {
                sendJsonResponse(exchange, 400, errorJson("No index loaded."));
                return;
            }

            StringBuilder csv = new StringBuilder();
            csv.append("# GENEDEX GENOMIC RESEARCH REPORT\n");
            csv.append("# Generated on: ").append(new java.util.Date()).append("\n");
            csv.append("# Dataset: ").append(im.getDatasetName()).append("\n\n");

            // 1. Executive Summary
            csv.append("--- EXECUTIVE SUMMARY ---\n");
            csv.append("Metric,Value\n");
            csv.append("Dataset Name,").append(im.getDatasetName()).append("\n");
            csv.append("Dataset Source,").append(im.getDatasetSource()).append("\n");
            csv.append("Total Reads,").append(im.getTotalReadCount()).append("\n");
            csv.append("Total DNA Bases,").append(im.getTotalDnaBases()).append("\n");
            csv.append("Total Indexed Symbols,").append(im.getTotalIndexedSymbols()).append("\n\n");

            // 2. Index Telemetry
            Map<String, Object> stats = im.getStatistics();
            csv.append("--- INDEX TELEMETRY ---\n");
            csv.append("Parameter,Value\n");
            csv.append("Compressed Size (Bytes),").append(stats.get("compressedSizeBytes")).append("\n");
            csv.append("Bits per Base,").append(stats.get("bitsPerBase")).append("\n");
            csv.append("Alphabet Size,").append(stats.get("alphabetSize")).append("\n");
            csv.append("Build Time (ms),").append(stats.get("buildTimeMs")).append("\n\n");

            // 3. Nucleotide Composition
            Map<String, Object> an = im.getAnalytics();
            csv.append("--- NUCLEOTIDE COMPOSITION ---\n");
            csv.append("Symbol,Count,Percentage\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> compList = (List<Map<String, Object>>) an.get("composition");
            if (compList != null) {
                for (Map<String, Object> item : compList) {
                    csv.append(item.get("symbol")).append(",")
                       .append(item.get("count")).append(",")
                       .append(item.get("percentage")).append("%\n");
                }
            }
            csv.append("Overall GC%,").append(an.get("overallGcPercentage")).append("%\n\n");

            // 4. Reads Catalog
            csv.append("--- READS CATALOG ---\n");
            csv.append("Read_Index,Header,Start_Pos,End_Pos,Length,GC_Percent\n");
            int totalReads = im.getTotalReadCount();
            for (int k = 0; k < totalReads; k++) {
                Map<String, Object> r = im.getReadStatistics(k);
                if (r != null) {
                    csv.append(r.get("readIndex")).append(",")
                       .append("\"").append(r.get("header")).append("\",")
                       .append(r.get("startPos")).append(",")
                       .append(r.get("endPos")).append(",")
                       .append(r.get("length")).append(",")
                       .append(r.get("gcPercentage")).append("\n");
                }
            }

            String cleanName = im.getDatasetName().replaceAll("[^a-zA-Z0-9._-]", "_");
            String filename = "genedex_report_" + cleanName + ".csv";
            sendCsvResponse(exchange, filename, csv.toString());
        }
    }

    private void sendCsvResponse(HttpExchange exchange, String filename, String csvContent) throws IOException {
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // =========================================================================
    // Static Web Resources
    // =========================================================================

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            byte[] content = null;
            String contentType = "text/html; charset=UTF-8";

            Path localPath = Paths.get("src", "main", "resources", "web", path.startsWith("/") ? path.substring(1) : path);
            if (!Files.exists(localPath) || Files.isDirectory(localPath)) {
                localPath = Paths.get("public", path.startsWith("/") ? path.substring(1) : path);
            }
            if (Files.exists(localPath) && !Files.isDirectory(localPath)) {
                content = Files.readAllBytes(localPath);
            } else {
                try (InputStream is = getClass().getResourceAsStream("/web" + path)) {
                    if (is != null) {
                        content = is.readAllBytes();
                    }
                }
            }

            if (content == null) {
                String notFound = "<h1>404 Not Found</h1><p>Resource " + path + " not found.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            if (path.endsWith(".css")) {
                contentType = "text/css; charset=UTF-8";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript; charset=UTF-8";
            } else if (path.endsWith(".json")) {
                contentType = "application/json; charset=UTF-8";
            } else if (path.endsWith(".svg")) {
                contentType = "image/svg+xml";
            } else if (path.endsWith(".png")) {
                contentType = "image/png";
            } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (path.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (path.endsWith(".ico")) {
                contentType = "image/x-icon";
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void sendCorsOk(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-CSRF-Token");
        exchange.sendResponseHeaders(204, -1);
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private Map<String, String> parseJsonMap(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return map;

        int len = json.length();
        int i = 0;
        // Skip leading whitespace and opening '{'
        while (i < len && Character.isWhitespace(json.charAt(i))) i++;
        if (i < len && json.charAt(i) == '{') i++;

        while (i < len) {
            // Skip whitespace and commas
            while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            if (i >= len || json.charAt(i) == '}') break;

            // Expect key (quoted string)
            if (json.charAt(i) != '"') {
                i++;
                continue;
            }
            i++; // skip open quote of key
            StringBuilder keySb = new StringBuilder();
            boolean escaped = false;
            while (i < len) {
                char c = json.charAt(i++);
                if (escaped) {
                    keySb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    keySb.append(c);
                }
            }
            String key = keySb.toString();

            // Find colon ':'
            while (i < len && json.charAt(i) != ':') i++;
            if (i < len && json.charAt(i) == ':') i++;

            // Skip whitespace before value
            while (i < len && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= len) break;

            char valStart = json.charAt(i);
            StringBuilder valSb = new StringBuilder();
            if (valStart == '"') {
                // Quoted string value
                i++; // skip opening quote
                escaped = false;
                while (i < len) {
                    char c = json.charAt(i++);
                    if (escaped) {
                        if (c == 'n') valSb.append('\n');
                        else if (c == 'r') valSb.append('\r');
                        else if (c == 't') valSb.append('\t');
                        else if (c == 'b') valSb.append('\b');
                        else if (c == 'f') valSb.append('\f');
                        else if (c == '"') valSb.append('"');
                        else if (c == '\\') valSb.append('\\');
                        else if (c == '/') valSb.append('/');
                        else if (c == 'u' && i + 4 <= len) {
                            String hex = json.substring(i, i + 4);
                            try {
                                valSb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                valSb.append('u');
                            }
                        } else valSb.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break; // End of string value
                    } else {
                        valSb.append(c);
                    }
                }
                map.put(key, valSb.toString());
            } else {
                // Primitive value (number, boolean, null)
                while (i < len && json.charAt(i) != ',' && json.charAt(i) != '}') {
                    valSb.append(json.charAt(i++));
                }
                map.put(key, valSb.toString().trim());
            }
        }

        return map;
    }

    private String errorJson(String message) {
        return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}";
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Character) return "\"" + escapeJson(String.valueOf(obj)) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(String.valueOf(e.getKey()))).append("\":");
                sb.append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            Collection<?> coll = (Collection<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : coll) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Match) {
            Match m = (Match) obj;
            return "{\"readIndex\":" + m.getReadIndex() + ",\"offsetInRead\":" + m.getOffsetInRead()
                    + ",\"globalPosition\":" + m.getGlobalPosition() + ",\"pattern\":\"" + escapeJson(m.getPattern()) + "\"}";
        }
        if (obj instanceof WaveletTree.TraversalStep) {
            WaveletTree.TraversalStep s = (WaveletTree.TraversalStep) obj;
            return "{\"nodeId\":\"" + s.nodeId + "\",\"alphabetLabel\":\"" + escapeJson(s.alphabetLabel)
                    + "\",\"depth\":" + s.depth + ",\"index\":" + s.index + ",\"bit\":" + s.bit
                    + ",\"nextIndex\":" + s.nextIndex + ",\"direction\":\"" + escapeJson(s.direction) + "\"}";
        }
        if (obj instanceof WaveletTree.SelectStep) {
            WaveletTree.SelectStep s = (WaveletTree.SelectStep) obj;
            return "{\"fromNodeId\":\"" + s.fromNodeId + "\",\"toNodeId\":\"" + s.toNodeId
                    + "\",\"nodeLabel\":\"" + escapeJson(s.nodeLabel) + "\",\"bit\":" + s.bit
                    + ",\"incomingOccurrence\":" + s.incomingOccurrence
                    + ",\"mappedPositionInParent\":" + s.mappedPositionInParent + "}";
        }
        if (obj instanceof QueryEngine.CandidateEvaluation) {
            QueryEngine.CandidateEvaluation c = (QueryEngine.CandidateEvaluation) obj;
            return "{\"candidateIndex\":" + c.candidateIndex + ",\"candidateStart\":" + c.candidateStart
                    + ",\"targetCharGlobalPos\":" + c.targetCharGlobalPos + ",\"accepted\":" + c.accepted
                    + ",\"statusReason\":\"" + escapeJson(c.statusReason) + "\",\"readIndex\":" + c.readIndex
                    + ",\"offsetInRead\":" + c.offsetInRead + "}";
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
