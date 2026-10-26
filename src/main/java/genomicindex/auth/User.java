package genomicindex.auth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User Entity Model representing registered accounts and authorization levels.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Module: Role-Based Access Control (RBAC) & Genomic Account Model
 *
 * @author sruthicasathish-stack
 */
public class User {

    public enum Role {
        ADMIN,
        REGISTERED_USER,
        GUEST
    }

    public enum Category {
        ADMINISTRATOR("Administrator"),
        BIOINFORMATICS_RESEARCHER("Bioinformatics Researcher"),
        LAB_TECHNICIAN("Genomic Lab Technician"),
        CLINICAL_RESEARCHER("Clinical Researcher"),
        STUDENT_EDUCATOR("Student / Educator"),
        GUEST("Guest");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Category fromString(String text) {
            if (text == null) return GUEST;
            String clean = text.trim();
            for (Category c : values()) {
                if (c.name().equalsIgnoreCase(clean) || c.displayName.equalsIgnoreCase(clean)) {
                    return c;
                }
            }
            return GUEST;
        }
    }

    private final String id;
    private final String email;
    private String fullName;
    private String passwordHash;
    private String salt;
    private Role role;
    private Category category;
    private String organization;
    private boolean active;
    private final long createdAt;
    private long lastLoginAt;
    private int failedLoginAttempts;
    private long lockoutUntil;

    public User(String id, String email, String fullName, String passwordHash, String salt,
                Role role, Category category, String organization, boolean active,
                long createdAt, long lastLoginAt) {
        this.id = id;
        this.email = email.toLowerCase().trim();
        this.fullName = fullName != null ? fullName.trim() : "";
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role != null ? role : Role.REGISTERED_USER;
        this.category = category != null ? category : Category.STUDENT_EDUCATOR;
        this.organization = organization != null ? organization.trim() : "";
        this.active = active;
        this.createdAt = createdAt > 0 ? createdAt : Instant.now().toEpochMilli();
        this.lastLoginAt = lastLoginAt;
        this.failedLoginAttempts = 0;
        this.lockoutUntil = 0;
    }

    /**
     * Factory for creating a public Guest user context.
     */
    public static User createGuest() {
        return new User("guest", "guest@genomicdemo.local", "Guest Visitor", "", "",
                Role.GUEST, Category.GUEST, "Public", true, Instant.now().toEpochMilli(), 0);
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPasswordHash() { return passwordHash; }
    public void setPassword(String hash, String salt) {
        this.passwordHash = hash;
        this.salt = salt;
    }

    public String getSalt() { return salt; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getCreatedAt() { return createdAt; }
    public long getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(long lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void incrementFailedAttempts() { this.failedLoginAttempts++; }
    public void resetFailedAttempts() { this.failedLoginAttempts = 0; this.lockoutUntil = 0; }

    public long getLockoutUntil() { return lockoutUntil; }
    public void setLockoutUntil(long lockoutUntil) { this.lockoutUntil = lockoutUntil; }

    public boolean isLockedOut() {
        return lockoutUntil > Instant.now().toEpochMilli();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isGuest() {
        return role == Role.GUEST;
    }

    /**
     * Serializes public profile data (safe for client responses; NEVER includes passwordHash or salt).
     */
    public Map<String, Object> toPublicProfile() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("email", email);
        map.put("fullName", fullName);
        map.put("role", role.name());
        map.put("category", category.getDisplayName());
        map.put("organization", organization);
        map.put("active", active);
        map.put("createdAt", createdAt);
        map.put("lastLoginAt", lastLoginAt);
        return map;
    }
}
