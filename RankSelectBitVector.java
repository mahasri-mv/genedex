package genomicindex;

/**
 * Interface for a Bit-Vector supporting constant-time or sublinear
 * ACCESS, RANK, and SELECT operations.
 *
 * Indexing conventions:
 * - Position indices are 0-based: [0 .. length - 1].
 * - For RANK(bit, index): counts occurrences of 'bit' in the prefix [0 .. index - 1].
 *   rank(bit, 0) == 0.
 *   rank(bit, length) == total occurrences of 'bit'.
 * - For SELECT(bit, occurrence): 'occurrence' is 1-based (k = 1, 2, ...).
 *   Returns the 0-based position of the k-th occurrence of 'bit'.
 */
public interface RankSelectBitVector {

    /**
     * Returns the bit value at the specified 0-based index.
     * @param index 0 <= index < length()
     * @return 0 or 1
     */
    int access(int index);

    /**
     * Returns the number of occurrences of 'bit' in prefix [0 .. index - 1].
     * @param bit 0 or 1
     * @param index 0 <= index <= length()
     * @return count of 'bit' before position 'index'
     */
    int rank(int bit, int index);

    /**
     * Returns the 0-based position of the k-th occurrence of 'bit'.
     * @param bit 0 or 1
     * @param occurrence 1-based occurrence number (k >= 1)
     * @return 0-based position in [0 .. length - 1]
     * @throws IllegalArgumentException if occurrence is <= 0 or > total occurrences of bit
     */
    int select(int bit, int occurrence);

    /**
     * Total number of bits in this bit-vector.
     * @return length n
     */
    int length();

    /**
     * Total count of 1-bits.
     * @return number of set bits
     */
    int countOnes();

    /**
     * Total count of 0-bits.
     * @return number of zero bits
     */
    int countZeros();

    /**
     * Theoretical memory consumption in bits.
     * @return number of bits
     */
    long theoreticalBits();

    /**
     * Approximate JVM memory consumption in bytes.
     * @return bytes
     */
    long approximateMemoryBytes();

    /**
     * Actual packed 64-bit word allocation in bits (numWords * 64).
     * @return allocated bits
     */
    long allocatedBits();
}
