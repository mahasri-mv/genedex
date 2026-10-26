package genomicindex.auth;

import genomicindex.IndexManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Side Session Manager providing HttpOnly cookie session handling,
 * CSRF token generation, brute-force rate-limiting, and per-user dataset isolation.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Key Data Structure: ConcurrentHashMap (for active sessions & rate-limiting records)
 *
 * Concurrency Characteristics:
 * - Thread-safe O(1) concurrent session resolution for incoming REST API requests
 * - Non-blocking rate-limit verification preventing brute-force attack slowdowns
 *
 * @author sruthicasathish-stack
 */
public class SessionManager {

    private static final long DEFAULT_EXPIRY_MS = 30 * 60 * 1000L; // 30 mins
    private static final long REMEMBER_ME_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 mins
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Active Session encapsulating authenticated user state and their private IndexManager.
     */
    public static class Session {
        private final String sessionId;
        private final String csrfToken;
        private final User user;
        private final boolean guest;
        private final long createdAt;
        private final long expiryDurationMs;
        private long lastAccessedAt;
        private final IndexManager userIndexManager;
        private String activeDatasetId;

        public Session(String sessionId, String csrfToken, User user, boolean rememberMe) {
            this.sessionId = sessionId;
            this.csrfToken = csrfToken;
            this.user = user;
            this.guest = (user == null || user.isGuest());
            this.createdAt = Instant.now().toEpochMilli();
            this.lastAccessedAt = this.createdAt;
            this.expiryDurationMs = rememberMe ? REMEMBER_ME_EXPIRY_MS : DEFAULT_EXPIRY_MS;
            this.activeDatasetId = "demo_report";

            // Per-User Dataset Isolation: each registered user receives their own isolated IndexManager
            this.userIndexManager = new IndexManager();
            try {
                // Pre-load standard demo report dataset (GATTACAGATTACA) into the user's isolated workspace
                this.userIndexManager.loadDemoReportDataset();
            } catch (Exception ignored) {
            }
        }

        public String getSessionId() { return sessionId; }
        public String getCsrfToken() { return csrfToken; }
        public User getUser() { return user; }
        public boolean isGuest() { return guest; }
        public IndexManager getIndexManager() { return userIndexManager; }
        public String getActiveDatasetId() { return activeDatasetId; }
        public void setActiveDatasetId(String id) { this.activeDatasetId = id; }

        public synchronized boolean isExpired() {
            long now = Instant.now().toEpochMilli();
            return (now - lastAccessedAt) > expiryDurationMs;
        }

        public synchronized void touch() {
            this.lastAccessedAt = Instant.now().toEpochMilli();
        }
    }

    // Rate-limiting tracking per IP/Email
    private static class RateLimitEntry {
        int failedCount = 0;
        long lastFailedTime = 0;
        long lockoutUntil = 0;
    }

    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * Creates an authenticated session for a registered user.
     */
    public Session createSession(User user, boolean rememberMe) {
        String sessionId = generateSecureToken(32);
        String csrfToken = generateSecureToken(16);
        Session session = new Session(sessionId, csrfToken, user, rememberMe);
        activeSessions.put(sessionId, session);
        return session;
    }

    /**
     * Creates a Guest session.
     */
    public Session createGuestSession() {
        String sessionId = generateSecureToken(32);
        String csrfToken = generateSecureToken(16);
        Session session = new Session(sessionId, csrfToken, User.createGuest(), false);
        activeSessions.put(sessionId, session);
        return session;
    }

    /**
     * Retrieves an active session, extending its idle timeout.
     * Returns null if expired or not found.
     */
    public Session getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        Session session = activeSessions.get(sessionId);
        if (session == null) return null;

        if (session.isExpired()) {
            activeSessions.remove(sessionId);
            return null;
        }

        session.touch();
        return session;
    }

    /**
     * Invalidates and destroys a session upon logout.
     */
    public void invalidateSession(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
        }
    }

    /**
     * Checks rate limiting for an email or IP. Returns false if locked out.
     */
    public boolean checkRateLimit(String key) {
        if (key == null) return true;
        RateLimitEntry entry = rateLimitMap.get(key.toLowerCase());
        if (entry == null) return true;

        long now = Instant.now().toEpochMilli();
        if (entry.lockoutUntil > now) {
            return false; // Still locked out
        }
        return true;
    }

    public void recordFailedLogin(String key) {
        if (key == null) return;
        String cleanKey = key.toLowerCase();
        rateLimitMap.compute(cleanKey, (k, v) -> {
            if (v == null) v = new RateLimitEntry();
            long now = Instant.now().toEpochMilli();
            v.failedCount++;
            v.lastFailedTime = now;
            if (v.failedCount >= MAX_FAILED_ATTEMPTS) {
                v.lockoutUntil = now + LOCKOUT_DURATION_MS;
            }
            return v;
        });
    }

    public void recordSuccessfulLogin(String key) {
        if (key != null) {
            rateLimitMap.remove(key.toLowerCase());
        }
    }

    public int getRemainingLockoutSeconds(String key) {
        if (key == null) return 0;
        RateLimitEntry entry = rateLimitMap.get(key.toLowerCase());
        if (entry == null) return 0;
        long now = Instant.now().toEpochMilli();
        return (int) Math.max(0, (entry.lockoutUntil - now) / 1000);
    }

    public String buildSetCookieHeader(String sessionId, boolean rememberMe) {
        long maxAge = rememberMe ? (7 * 24 * 60 * 60) : (30 * 60);
        return String.format("session_token=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax", sessionId, maxAge);
    }

    public String buildClearCookieHeader() {
        return "session_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax";
    }

    private String generateSecureToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
