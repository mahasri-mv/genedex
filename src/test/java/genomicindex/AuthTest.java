package genomicindex;

import genomicindex.auth.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Automated Test Suite for Authentication, Cryptography, and Dataset Isolation.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Module: Security Test Harness (PBKDF2, Session, Rate Limiting, RBAC)
 *
 * @author sruthicasathish-stack
 */
public class AuthTest {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("    AUTHENTICATION & SECURITY AUTOMATED TEST SUITE");
        System.out.println("=================================================================\n");

        Path testDataDir = Paths.get("data", "test_auth_" + System.currentTimeMillis());

        try {
            testPasswordHashing();
            testUserDatabaseAndRegistration(testDataDir);
            testRateLimiting();
            testDatasetIsolation();
            testPasswordReset(testDataDir);
        } finally {
            // Clean up test data directory
            try {
                if (Files.exists(testDataDir)) {
                    Files.walk(testDataDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            } catch (Exception ignored) {}
        }

        System.out.println("\n=================================================================");
        System.out.println("AUTH TEST SUMMARY: " + passedTests + " / " + totalTests + " passed. Failed: " + failedTests);
        System.out.println("=================================================================");

        if (failedTests > 0) {
            System.exit(1);
        }
    }

    private static void testPasswordHashing() {
        System.out.println("[AUTH TEST 1] PBKDF2 Password Hashing & Verification");
        String password = "SecretPassword@2026!";
        PasswordHasher.HashResult res = PasswordHasher.hashPassword(password);

        assertTrue("Hash is not empty", res.hashHex != null && !res.hashHex.isEmpty());
        assertTrue("Salt is 32 hex chars (16 bytes)", res.saltHex.length() == 32);

        // Verification with correct password
        assertTrue("Verify correct password", PasswordHasher.verifyPassword(password, res.saltHex, res.hashHex));

        // Verification with incorrect password
        assertTrue("Reject wrong password", !PasswordHasher.verifyPassword("WrongPassword", res.saltHex, res.hashHex));
    }

    private static void testUserDatabaseAndRegistration(Path dataDir) {
        System.out.println("[AUTH TEST 2] User Registration & Dev Account Seeding");
        UserDatabase db = new UserDatabase(dataDir);

        // Check seeded accounts
        User admin = db.findByEmail("admin@genomicdemo.local");
        assertTrue("Seeded admin exists", admin != null);
        assertTrue("Admin role is ADMIN", admin.isAdmin());
        assertTrue("Admin category is Administrator", admin.getCategory() == User.Category.ADMINISTRATOR);

        User researcher = db.findByEmail("researcher@genomicdemo.local");
        assertTrue("Seeded researcher exists", researcher != null);
        assertTrue("Researcher category", researcher.getCategory() == User.Category.BIOINFORMATICS_RESEARCHER);

        // Register new valid user
        User newUser = db.registerUser("newdoc@hospital.org", "Dr. Jane Doe", "JaneDoc@2026!",
                User.Category.CLINICAL_RESEARCHER, "General Hospital");
        assertTrue("New user registered", newUser != null);
        assertEquals("User email stored in lowercase", "newdoc@hospital.org", newUser.getEmail());

        // Attempt duplicate registration
        boolean caughtDuplicate = false;
        try {
            db.registerUser("newdoc@hospital.org", "Duplicate", "JaneDoc@2026!",
                    User.Category.CLINICAL_RESEARCHER, "General Hospital");
        } catch (IllegalArgumentException e) {
            caughtDuplicate = true;
        }
        assertTrue("Duplicate email rejected", caughtDuplicate);

        // Attempt self-registration as Administrator
        boolean caughtAdminReg = false;
        try {
            db.registerUser("hacker@fake.org", "Bad Actor", "HackerPassword@123",
                    User.Category.ADMINISTRATOR, "Shadow Org");
        } catch (IllegalArgumentException e) {
            caughtAdminReg = true;
        }
        assertTrue("Self-registration as ADMIN rejected", caughtAdminReg);
    }

    private static void testRateLimiting() {
        System.out.println("[AUTH TEST 3] Login Rate Limiting & Lockout");
        SessionManager sm = new SessionManager();
        String testEmail = "victim@example.com";

        assertTrue("Initial rate limit check passes", sm.checkRateLimit(testEmail));

        // Fail 4 times (threshold is 5)
        for (int i = 0; i < 4; i++) {
            sm.recordFailedLogin(testEmail);
        }
        assertTrue("4 failures still below threshold", sm.checkRateLimit(testEmail));

        // 5th failure triggers lockout
        sm.recordFailedLogin(testEmail);
        assertTrue("5th failure triggers lockout", !sm.checkRateLimit(testEmail));
        assertTrue("Remaining lockout seconds > 0", sm.getRemainingLockoutSeconds(testEmail) > 0);

        // Successful login resets
        sm.recordSuccessfulLogin(testEmail);
        assertTrue("Reset after success allows access", sm.checkRateLimit(testEmail));
    }

    private static void testDatasetIsolation() {
        System.out.println("[AUTH TEST 4] Per-User Dataset Isolation");
        SessionManager sm = new SessionManager();

        User userA = new User("u1", "usera@test.local", "User A", "", "",
                User.Role.REGISTERED_USER, User.Category.BIOINFORMATICS_RESEARCHER, "Lab A", true, 0, 0);
        User userB = new User("u2", "userb@test.local", "User B", "", "",
                User.Role.REGISTERED_USER, User.Category.STUDENT_EDUCATOR, "Uni B", true, 0, 0);

        SessionManager.Session sessionA = sm.createSession(userA, false);
        SessionManager.Session sessionB = sm.createSession(userB, false);

        // User A uploads dataset A: 1 read GATTACA
        String fastqA = "@read0\nGATTACA\n+\nIIIIIII\n";
        try {
            sessionA.getIndexManager().loadFromFastqContent("Dataset_A.fastq", fastqA);
        } catch (Exception e) {
            fail("Upload A failed: " + e.getMessage());
        }

        // User B uploads dataset B: 1 read TTAGC
        String fastqB = "@read0\nTTAGC\n+\nIIIII\n";
        try {
            sessionB.getIndexManager().loadFromFastqContent("Dataset_B.fastq", fastqB);
        } catch (Exception e) {
            fail("Upload B failed: " + e.getMessage());
        }

        // Verify User A's index contains GATTACA (length 8 with #)
        IndexManager imA = sessionA.getIndexManager();
        assertEquals("User A dataset length", 8, imA.getTotalIndexedSymbols());
        assertEquals("User A ACCESS(0)", 'G', imA.getWaveletTree().access(0));
        assertEquals("User A ACCESS(1)", 'A', imA.getWaveletTree().access(1));

        // Verify User B's index contains TTAGC (length 6 with #)
        IndexManager imB = sessionB.getIndexManager();
        assertEquals("User B dataset length", 6, imB.getTotalIndexedSymbols());
        assertEquals("User B ACCESS(0)", 'T', imB.getWaveletTree().access(0));
        assertEquals("User B ACCESS(1)", 'T', imB.getWaveletTree().access(1));

        // Strict isolation confirmed: User A cannot see User B's bases, and vice-versa!
        assertTrue("Datasets are isolated", imA.getTotalIndexedSymbols() != imB.getTotalIndexedSymbols());
    }

    private static void testPasswordReset(Path dataDir) {
        System.out.println("[AUTH TEST 5] Single-Use Password Reset Tokens");
        UserDatabase db = new UserDatabase(dataDir);
        User user = db.findByEmail("student@genomicdemo.local");
        assertTrue("Student exists", user != null);

        String token = db.createPasswordResetToken(user.getEmail());
        assertTrue("Token generated", token != null && !token.isEmpty());

        // Reset password using token
        String newPassword = "NewStudentDemo@2026!";
        boolean resetOk = db.resetPasswordWithToken(token, newPassword);
        assertTrue("Password reset succeeds with valid token", resetOk);

        // Verify new password works
        User updated = db.findByEmail(user.getEmail());
        assertTrue("New password verifies", PasswordHasher.verifyPassword(newPassword, updated.getSalt(), updated.getPasswordHash()));

        // Verify token is single-use: cannot be reused!
        boolean secondReset = db.resetPasswordWithToken(token, "AnotherPassword@123");
        assertTrue("Single-use token rejected on second attempt", !secondReset);
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        totalTests++;
        if (java.util.Objects.equals(expected, actual)) {
            passedTests++;
        } else {
            failedTests++;
            System.err.println("  [FAIL] " + testName + " -> Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        totalTests++;
        if (condition) {
            passedTests++;
        } else {
            failedTests++;
            System.err.println("  [FAIL] " + testName + " -> Condition was FALSE");
        }
    }

    private static void fail(String message) {
        totalTests++;
        failedTests++;
        System.err.println("  [FAIL] " + message);
    }
}
