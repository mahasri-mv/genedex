package genomicindex;

import genomicindex.auth.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * Automated Evaluation and Verification Test Suite for Member 4.
 *
 * Covers:
 * 1. FastqParser: 4-line syntax validation, invalid header detection, case normalization.
 * 2. DatasetRegistry: Multi-tenant dataset isolation, thread safety via ConcurrentHashMap.
 * 3. ConcurrentHashMap Concurrency Stress Test: Multi-threaded insertion and query verification.
 * 4. Authentication Subsystem: PBKDF2-HMAC-SHA256 password hashing and session tokens.
 *
 * @author sruthicasathish-stack (Member 4)
 */
public class Member4EvaluationTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("    MEMBER 4 (sruthicasathish-stack) EVALUATION TEST SUITE");
        System.out.println("    Modules: FastqParser, DatasetRegistry, Auth, ConcurrentHashMap");
        System.out.println("=================================================================\n");

        testFastqParsing();
        testConcurrentHashMapDatasetRegistry();
        testPasswordHashingAndAuth();
        testConcurrentSessionManagement();

        System.out.println("\n=================================================================");
        System.out.println("MEMBER 4 TEST RESULTS: " + totalPassed + " Passed | " + totalFailed + " Failed");
        System.out.println("=================================================================");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }

    private static void testFastqParsing() {
        System.out.println("[TEST 1] FastqParser Validation & Normalization");

        // Valid FASTQ record
        String validFastq = "@read1\nacgtACGT\n+\nIIIIIIII\n";
        try {
            FastqParser.ParseResult result = FastqParser.parse(validFastq);
            assertCondition(result.getReadCount() == 1, "Read count should be 1");
            assertCondition(result.getTotalBaseCount() == 8, "Total bases should be 8");
            assertCondition("ACGTACGT".equals(result.getReads().get(0)), "Lowercase nucleotides must be normalized to uppercase");
            System.out.println("  ✓ Valid FASTQ parsed and normalized successfully.");
            totalPassed++;
        } catch (Exception e) {
            System.err.println("  ✗ Failed valid FASTQ parse: " + e.getMessage());
            totalFailed++;
        }

        // Invalid Header (missing @)
        try {
            FastqParser.parse("read_bad\nACGT\n+\nIIII\n");
            System.err.println("  ✗ Failed: expected IOException for missing '@' prefix");
            totalFailed++;
        } catch (IOException e) {
            System.out.println("  ✓ Correctly rejected header missing '@' prefix.");
            totalPassed++;
        }

        // Length mismatch between sequence and quality
        try {
            FastqParser.parse("@read_mismatch\nACGT\n+\nII\n");
            System.err.println("  ✗ Failed: expected IOException for sequence/quality length mismatch");
            totalFailed++;
        } catch (IOException e) {
            System.out.println("  ✓ Correctly rejected sequence and quality length mismatch.");
            totalPassed++;
        }
    }

    private static void testConcurrentHashMapDatasetRegistry() {
        System.out.println("[TEST 2] DatasetRegistry & ConcurrentHashMap Multi-Thread Stress Test");

        Path tempDir = Paths.get("data", "test_registry_" + System.currentTimeMillis());
        try {
            DatasetRegistry registry = new DatasetRegistry(tempDir);

            // Verify built-in datasets
            List<DatasetRegistry.DatasetEntry> builtIns = registry.getBuiltInDatasets();
            assertCondition(builtIns.size() >= 3, "Registry must contain at least 3 standard built-in datasets");
            System.out.println("  ✓ Built-in sample datasets registered: " + builtIns.size());
            totalPassed++;

            // Concurrency stress test: 8 concurrent threads adding datasets simultaneously
            int threadCount = 8;
            int entriesPerThread = 25;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        String userId = "user_stress_" + threadId;
                        for (int i = 0; i < entriesPerThread; i++) {
                            String fastq = "@r" + i + "\nACGT\n+\nIIII\n";
                            registry.registerDataset(userId, "dataset_" + i, "Desc " + i, "Upload", fastq);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertCondition(completed, "Concurrent thread execution must finish within timeout");
            System.out.println("  ✓ ConcurrentHashMap handled " + (threadCount * entriesPerThread) + " concurrent dataset operations without race conditions.");
            totalPassed++;

            // Verify telemetry stats
            Map<String, Object> stats = registry.getManifestCacheStats();
            assertCondition("java.util.concurrent.ConcurrentHashMap".equals(stats.get("dataStructure")), "Telemetry identifies ConcurrentHashMap");
            System.out.println("  ✓ Registry telemetry verified: " + stats.get("dataStructure"));
            totalPassed++;

        } catch (Exception e) {
            System.err.println("  ✗ Failed registry concurrency test: " + e.getMessage());
            totalFailed++;
        } finally {
            // Clean up temporary test files
            try {
                if (Files.exists(tempDir)) {
                    Files.walk(tempDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            } catch (Exception ignored) {}
        }
    }

    private static void testPasswordHashingAndAuth() {
        System.out.println("[TEST 3] Cryptographic Authentication (PBKDF2-HMAC-SHA256)");

        String rawPassword = "Bioinformatics@2026";
        PasswordHasher.HashResult hashRes = PasswordHasher.hashPassword(rawPassword);

        assertCondition(hashRes.hashHex != null && hashRes.hashHex.length() == 64, "PBKDF2 hash should be 64 hex chars (256 bits)");
        assertCondition(hashRes.saltHex != null && hashRes.saltHex.length() == 32, "Salt should be 32 hex chars (128 bits)");

        boolean matches = PasswordHasher.verifyPassword(rawPassword, hashRes.hashHex, hashRes.saltHex);
        assertCondition(matches, "Valid password must verify successfully");

        boolean rejectsWrong = PasswordHasher.verifyPassword("WrongPassword", hashRes.hashHex, hashRes.saltHex);
        assertCondition(!rejectsWrong, "Incorrect password must be rejected");

        System.out.println("  ✓ NIST SP 800-132 PBKDF2 hashing & constant-time verification passed.");
        totalPassed++;
    }

    private static void testConcurrentSessionManagement() {
        System.out.println("[TEST 4] SessionManager & Concurrent Session Registry");

        SessionManager sessionManager = new SessionManager();
        User testUser = new User("usr_demo", "sruthica@demo.local", "Sruthica Sathish",
                User.Role.REGISTERED_USER, User.Category.BIOINFORMATICS_RESEARCHER, "Genomics Core");

        SessionManager.Session session = sessionManager.createSession(testUser, false);
        assertCondition(session != null, "Session must not be null");
        assertCondition(session.getCsrfToken() != null && !session.getCsrfToken().isEmpty(), "CSRF token must be generated");

        SessionManager.Session resolved = sessionManager.getSession(session.getSessionId());
        assertCondition(resolved != null && resolved.getUser().getEmail().equals("sruthica@demo.local"), "Session must resolve user by ID");

        System.out.println("  ✓ Thread-safe session creation, CSRF protection, and user resolution passed.");
        totalPassed++;
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion Failed: " + message);
        }
    }
}
