package genomicindex.auth;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe persistent database for authentication, authorization, and audit logs.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Key Data Structure: ConcurrentHashMap (for email/id fast in-memory indexing)
 *
 * Storage Files:
 * - data/users.json: Persistent account registry with PBKDF2 hashes.
 * - data/audit_log.jsonl: Security audit trail of logins, registrations, and admin actions.
 *
 * Features:
 * - Atomic file updates using temporary files and ATOMIC_MOVE.
 * - Thread-safe read/write locking combined with ConcurrentHashMap indices.
 * - Pre-seeding of development demonstration accounts.
 * - Single-use expiring password reset tokens.
 *
 * @author sruthicasathish-stack
 */
public class UserDatabase {

    private final Path usersFilePath;
    private final Path auditFilePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
    private final Map<String, User> usersById = new ConcurrentHashMap<>();

    // Password reset tokens: token -> { email, expiresAt, used }
    public static class ResetToken {
        public final String token;
        public final String email;
        public final long expiresAt;
        public boolean used;

        public ResetToken(String token, String email, long expiresAt) {
            this.token = token;
            this.email = email;
            this.expiresAt = expiresAt;
            this.used = false;
        }
    }

    private final Map<String, ResetToken> resetTokens = new ConcurrentHashMap<>();

    public UserDatabase(Path dataDir) {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize data directory: " + e.getMessage(), e);
        }
        this.usersFilePath = dataDir.resolve("users.json");
        this.auditFilePath = dataDir.resolve("audit_log.jsonl");

        loadUsers();
        if (usersByEmail.isEmpty()) {
            seedDevelopmentAccounts();
        }
    }

    /**
     * Seeds the faculty evaluation demo accounts.
     */
    private void seedDevelopmentAccounts() {
        System.out.println("[AUTH] Initializing development demonstration accounts...");
        createSeedUser("admin@genomicdemo.local", "Platform Administrator", "AdminDemo@2026!",
                User.Role.ADMIN, User.Category.ADMINISTRATOR, "Genomic Research Center");

        createSeedUser("researcher@genomicdemo.local", "Dr. Elena Rostova", "ResearchDemo@2026!",
                User.Role.REGISTERED_USER, User.Category.BIOINFORMATICS_RESEARCHER, "Max Planck Institute");

        createSeedUser("lab@genomicdemo.local", "Marcus Vance", "LabDemo@2026!",
                User.Role.REGISTERED_USER, User.Category.LAB_TECHNICIAN, "Illumina Core Facility");

        createSeedUser("clinical@genomicdemo.local", "Dr. Aisha Khan", "ClinicalDemo@2026!",
                User.Role.REGISTERED_USER, User.Category.CLINICAL_RESEARCHER, "Genomic Medicine Unit");

        createSeedUser("student@genomicdemo.local", "Alex Chen", "StudentDemo@2026!",
                User.Role.REGISTERED_USER, User.Category.STUDENT_EDUCATOR, "B.Tech Computer Science");

        saveUsers();
        recordAudit("DEV_SEED", "system", "127.0.0.1", "Initialized 5 demonstration accounts", true);
        System.out.println("[AUTH] 5 demonstration accounts successfully created with PBKDF2 hashes.");
    }

    private void createSeedUser(String email, String fullName, String password,
                                User.Role role, User.Category category, String organization) {
        PasswordHasher.HashResult hashRes = PasswordHasher.hashPassword(password);
        String id = "usr_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(id, email, fullName, hashRes.hashHex, hashRes.saltHex,
                role, category, organization, true, Instant.now().toEpochMilli(), 0);
        usersByEmail.put(user.getEmail(), user);
        usersById.put(user.getId(), user);
    }

    /**
     * Registers a new user account.
     * Prevents duplicate emails and forbids self-registration as ADMIN.
     */
    public User registerUser(String email, String fullName, String password,
                             User.Category category, String organization) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        String cleanEmail = email.toLowerCase().trim();

        lock.writeLock().lock();
        try {
            if (usersByEmail.containsKey(cleanEmail)) {
                throw new IllegalArgumentException("An account with this email address already exists");
            }
            if (category == User.Category.ADMINISTRATOR) {
                throw new IllegalArgumentException("Administrator registration is restricted. Contact a system admin.");
            }

            PasswordHasher.HashResult hashRes = PasswordHasher.hashPassword(password);
            String id = "usr_" + UUID.randomUUID().toString().substring(0, 8);
            User newUser = new User(id, cleanEmail, fullName, hashRes.hashHex, hashRes.saltHex,
                    User.Role.REGISTERED_USER, category, organization, true,
                    Instant.now().toEpochMilli(), 0);

            usersByEmail.put(cleanEmail, newUser);
            usersById.put(id, newUser);
            saveUsers();

            recordAudit("REGISTER", cleanEmail, "client", "Self-registered as " + category.getDisplayName(), true);
            return newUser;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public User findByEmail(String email) {
        if (email == null) return null;
        return usersByEmail.get(email.toLowerCase().trim());
    }

    public User findById(String id) {
        if (id == null) return null;
        return usersById.get(id);
    }

    public List<User> getAllUsers() {
        lock.readLock().lock();
        try {
            List<User> list = new ArrayList<>(usersById.values());
            list.sort(Comparator.comparingLong(User::getCreatedAt));
            return list;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateUser(User user) {
        lock.writeLock().lock();
        try {
            usersByEmail.put(user.getEmail(), user);
            usersById.put(user.getId(), user);
            saveUsers();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Generates a single-use password reset token valid for 15 minutes.
     */
    public String createPasswordResetToken(String email) {
        User user = findByEmail(email);
        if (user == null) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = Instant.now().toEpochMilli() + (15 * 60 * 1000); // 15 mins
        resetTokens.put(token, new ResetToken(token, user.getEmail(), expiresAt));
        recordAudit("PASSWORD_RESET_REQUEST", user.getEmail(), "client", "Reset token generated", true);
        return token;
    }

    /**
     * Resets user password using a verified token.
     */
    public boolean resetPasswordWithToken(String token, String newPassword) {
        if (token == null || newPassword == null || newPassword.length() < 8) {
            return false;
        }
        ResetToken rt = resetTokens.get(token);
        if (rt == null || rt.used || rt.expiresAt < Instant.now().toEpochMilli()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            User user = findByEmail(rt.email);
            if (user == null) return false;

            PasswordHasher.HashResult hashRes = PasswordHasher.hashPassword(newPassword);
            user.setPassword(hashRes.hashHex, hashRes.saltHex);
            user.resetFailedAttempts();
            rt.used = true;
            saveUsers();

            recordAudit("PASSWORD_RESET_COMPLETE", user.getEmail(), "client", "Password reset successfully", true);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Appends a security audit entry to data/audit_log.jsonl.
     */
    public void recordAudit(String eventType, String email, String ip, String details, boolean success) {
        long now = Instant.now().toEpochMilli();
        String jsonLine = String.format(
                "{\"timestamp\":%d,\"eventType\":\"%s\",\"email\":\"%s\",\"ip\":\"%s\",\"details\":\"%s\",\"success\":%b}\n",
                now, escapeJson(eventType), escapeJson(email), escapeJson(ip), escapeJson(details), success
        );

        try {
            Files.writeString(auditFilePath, jsonLine, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[WARN] Could not write audit log: " + e.getMessage());
        }
    }

    /**
     * Retrieves the most recent audit logs.
     */
    public List<Map<String, Object>> getRecentAuditLogs(int limit) {
        if (!Files.exists(auditFilePath)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(auditFilePath, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - limit);
            for (int i = lines.size() - 1; i >= start; i--) {
                String line = lines.get(i).trim();
                if (!line.isEmpty()) {
                    entries.add(parseAuditLine(line));
                }
            }
        } catch (IOException e) {
            System.err.println("[WARN] Could not read audit logs: " + e.getMessage());
        }
        return entries;
    }

    private Map<String, Object> parseAuditLine(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        // Simple JSON extractor for flat audit entries
        map.put("timestamp", extractLong(json, "timestamp"));
        map.put("eventType", extractString(json, "eventType"));
        map.put("email", extractString(json, "email"));
        map.put("ip", extractString(json, "ip"));
        map.put("details", extractString(json, "details"));
        map.put("success", json.contains("\"success\":true"));
        return map;
    }

    private void saveUsers() {
        StringBuilder sb = new StringBuilder("[\n");
        int count = 0;
        int total = usersById.size();
        for (User u : usersById.values()) {
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(u.getId())).append("\",\n");
            sb.append("    \"email\": \"").append(escapeJson(u.getEmail())).append("\",\n");
            sb.append("    \"fullName\": \"").append(escapeJson(u.getFullName())).append("\",\n");
            sb.append("    \"passwordHash\": \"").append(u.getPasswordHash()).append("\",\n");
            sb.append("    \"salt\": \"").append(u.getSalt()).append("\",\n");
            sb.append("    \"role\": \"").append(u.getRole().name()).append("\",\n");
            sb.append("    \"category\": \"").append(u.getCategory().name()).append("\",\n");
            sb.append("    \"organization\": \"").append(escapeJson(u.getOrganization())).append("\",\n");
            sb.append("    \"active\": ").append(u.isActive()).append(",\n");
            sb.append("    \"createdAt\": ").append(u.getCreatedAt()).append(",\n");
            sb.append("    \"lastLoginAt\": ").append(u.getLastLoginAt()).append("\n");
            sb.append("  }").append(++count < total ? ",\n" : "\n");
        }
        sb.append("]\n");

        Path tempPath = usersFilePath.resolveSibling("users.tmp");
        try {
            Files.writeString(tempPath, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempPath, usersFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to atomically save users: " + e.getMessage());
        }
    }

    private void loadUsers() {
        if (!Files.exists(usersFilePath)) {
            return;
        }
        try {
            String content = Files.readString(usersFilePath, StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || content.equals("[]")) return;

            // Simple parser for user JSON objects
            int start = content.indexOf('{');
            while (start != -1) {
                int end = content.indexOf('}', start);
                if (end == -1) break;
                String obj = content.substring(start, end + 1);
                User user = parseUserObject(obj);
                if (user != null) {
                    usersByEmail.put(user.getEmail(), user);
                    usersById.put(user.getId(), user);
                }
                start = content.indexOf('{', end);
            }
        } catch (Exception e) {
            System.err.println("[WARN] Error loading users.json: " + e.getMessage());
        }
    }

    private User parseUserObject(String obj) {
        try {
            String id = extractString(obj, "id");
            String email = extractString(obj, "email");
            String fullName = extractString(obj, "fullName");
            String passwordHash = extractString(obj, "passwordHash");
            String salt = extractString(obj, "salt");
            String roleStr = extractString(obj, "role");
            String catStr = extractString(obj, "category");
            String org = extractString(obj, "organization");
            boolean active = obj.contains("\"active\": true") || obj.contains("\"active\":true");
            long createdAt = extractLong(obj, "createdAt");
            long lastLoginAt = extractLong(obj, "lastLoginAt");

            User.Role role = User.Role.valueOf(roleStr);
            User.Category category = User.Category.valueOf(catStr);

            return new User(id, email, fullName, passwordHash, salt, role, category, org, active, createdAt, lastLoginAt);
        } catch (Exception e) {
            System.err.println("[WARN] Skipped malformed user entry: " + e.getMessage());
            return null;
        }
    }

    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\\s*\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            pattern = "\"" + key + "\": \"";
            idx = json.indexOf(pattern);
        }
        if (idx == -1) return "";
        int start = json.indexOf('"', idx + key.length() + 2) + 1;
        int end = json.indexOf('"', start);
        return end != -1 ? json.substring(start, end) : "";
    }

    private static long extractLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return 0L;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == ' ')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
