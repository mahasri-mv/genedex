package genomicindex.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * Secure password hashing utility implementing PBKDF2 with HMAC-SHA256.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Module: Cryptographic Authentication & NIST SP 800-132 Compliance
 *
 * Security Properties:
 * - PBKDF2WithHmacSHA256 algorithm (NIST SP 800-132 compliant).
 * - 65,536 hash iterations (CPU and memory intensive for brute-force resistance).
 * - 128-bit (16-byte) cryptographically secure pseudo-random salt via SecureRandom.
 * - 256-bit derived key length.
 * - Constant-time comparison using MessageDigest.isEqual to prevent timing attacks.
 *
 * @author sruthicasathish-stack
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
        // Prevent instantiation
    }

    /**
     * Encapsulates a generated salt and hashed password.
     */
    public static class HashResult {
        public final String saltHex;
        public final String hashHex;

        public HashResult(String saltHex, String hashHex) {
            this.saltHex = saltHex;
            this.hashHex = hashHex;
        }

        public String toStoredFormat() {
            return saltHex + "$" + hashHex;
        }
    }

    /**
     * Hashes a plaintext password with a freshly generated cryptographically secure salt.
     * @param password plaintext password
     * @return HashResult containing hex-encoded salt and hash
     */
    public static HashResult hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        return new HashResult(HexFormat.of().formatHex(salt), HexFormat.of().formatHex(hash));
    }

    /**
     * Hashes a password with a pre-existing salt.
     */
    public static String hashWithSalt(String password, String saltHex) {
        byte[] salt = HexFormat.of().parseHex(saltHex);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Verifies a candidate password against the stored salt and hash in constant time.
     * @param candidatePassword plaintext candidate password
     * @param storedSaltHex hex-encoded salt
     * @param storedHashHex hex-encoded expected hash
     * @return true if candidate password matches, false otherwise
     */
    public static boolean verifyPassword(String candidatePassword, String storedSaltHex, String storedHashHex) {
        if (candidatePassword == null || storedSaltHex == null || storedHashHex == null) {
            return false;
        }
        try {
            byte[] salt = HexFormat.of().parseHex(storedSaltHex);
            byte[] expectedHash = HexFormat.of().parseHex(storedHashHex);
            byte[] candidateHash = pbkdf2(candidatePassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);

            // Constant-time array comparison to prevent side-channel timing attacks
            return MessageDigest.isEqual(expectedHash, candidateHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 key generation error: " + e.getMessage(), e);
        }
    }
}
