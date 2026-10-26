package genomicindex;

import java.util.Arrays;

/**
 * Packed 64-bit word implementation of RankSelectBitVector.
 *
 * Internal representation:
 * - Bits are packed into a primitive long[] array, 64 bits per word.
 * - Bit index i maps to wordIndex = i >> 6, bitPosition = i & 63.
 * - Rank support: precomputed cumulative prefix counts per word, providing O(1) rank.
 * - Select support: binary search over cumulative prefix counts followed by
 *   bit-level search within the 64-bit word, providing O(log n) select time.
 */
public class BitVector implements RankSelectBitVector {

    private final int length;
    private final long[] words;
    private final int[] prefixRanks; // cumulative count of 1-bits before words[w]
    private final int totalOnes;

    /**
     * Constructs a BitVector from a boolean array.
     * @param bits boolean array representing bits
     */
    public BitVector(boolean[] bits) {
        this.length = bits != null ? bits.length : 0;
        int numWords = (length + 63) >>> 6;
        this.words = new long[numWords];

        for (int i = 0; i < length; i++) {
            if (bits[i]) {
                words[i >>> 6] |= (1L << (i & 63));
            }
        }

        this.prefixRanks = new int[numWords + 1];
        int runningOnes = 0;
        for (int w = 0; w < numWords; w++) {
            prefixRanks[w] = runningOnes;
            runningOnes += Long.bitCount(words[w]);
        }
        prefixRanks[numWords] = runningOnes;
        this.totalOnes = runningOnes;
    }

    /**
     * Constructs a BitVector from an array of integer bits (0 or 1).
     * @param bits int array
     */
    public BitVector(int[] bits) {
        this.length = bits != null ? bits.length : 0;
        int numWords = (length + 63) >>> 6;
        this.words = new long[numWords];

        for (int i = 0; i < length; i++) {
            if (bits[i] != 0) {
                words[i >>> 6] |= (1L << (i & 63));
            }
        }

        this.prefixRanks = new int[numWords + 1];
        int runningOnes = 0;
        for (int w = 0; w < numWords; w++) {
            prefixRanks[w] = runningOnes;
            runningOnes += Long.bitCount(words[w]);
        }
        prefixRanks[numWords] = runningOnes;
        this.totalOnes = runningOnes;
    }

    /**
     * Constructs a BitVector from a raw binary string consisting of '0' and '1'.
     * @param binaryString string of '0' and '1'
     */
    public BitVector(String binaryString) {
        this.length = binaryString != null ? binaryString.length() : 0;
        int numWords = (length + 63) >>> 6;
        this.words = new long[numWords];

        for (int i = 0; i < length; i++) {
            char ch = binaryString.charAt(i);
            if (ch == '1') {
                words[i >>> 6] |= (1L << (i & 63));
            } else if (ch != '0') {
                throw new IllegalArgumentException("Invalid binary character at position " + i + ": '" + ch + "'");
            }
        }

        this.prefixRanks = new int[numWords + 1];
        int runningOnes = 0;
        for (int w = 0; w < numWords; w++) {
            prefixRanks[w] = runningOnes;
            runningOnes += Long.bitCount(words[w]);
        }
        prefixRanks[numWords] = runningOnes;
        this.totalOnes = runningOnes;
    }

    /**
     * Creates an empty BitVector of length 0.
     */
    public static BitVector empty() {
        return new BitVector(new int[0]);
    }

    @Override
    public int access(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("BitVector index out of range [0, " + (length - 1) + "]: " + index);
        }
        int wordIndex = index >>> 6;
        int bitOffset = index & 63;
        return (int) ((words[wordIndex] >>> bitOffset) & 1L);
    }

    @Override
    public int rank(int bit, int index) {
        if (bit != 0 && bit != 1) {
            throw new IllegalArgumentException("Rank query bit must be 0 or 1, received: " + bit);
        }
        if (index < 0 || index > length) {
            throw new IndexOutOfBoundsException("Rank query index out of range [0, " + length + "]: " + index);
        }
        if (index == 0) {
            return 0;
        }

        int wordIndex = index >>> 6;
        int bitOffset = index & 63;

        // Sum 1-bits in all complete preceding words
        int onesBeforeWord = prefixRanks[wordIndex];

        // Mask for remaining bits in current word: lowest 'bitOffset' bits
        long mask = bitOffset == 0 ? 0L : ((bitOffset == 64) ? -1L : ((1L << bitOffset) - 1L));
        int onesInWord = Long.bitCount(words[wordIndex] & mask);
        int rank1 = onesBeforeWord + onesInWord;

        return bit == 1 ? rank1 : (index - rank1);
    }

    @Override
    public int select(int bit, int occurrence) {
        if (bit != 0 && bit != 1) {
            throw new IllegalArgumentException("Select query bit must be 0 or 1, received: " + bit);
        }
        int maxOccurrences = (bit == 1) ? totalOnes : (length - totalOnes);
        if (occurrence <= 0 || occurrence > maxOccurrences) {
            throw new IllegalArgumentException("Select(" + bit + ", " + occurrence + ") invalid: total occurrences of bit "
                    + bit + " is " + maxOccurrences + ", requested k=" + occurrence);
        }

        // Binary search over prefix words: find word containing the target occurrence
        int low = 0;
        int high = words.length - 1;
        int targetWord = 0;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int countBeforeMid = (bit == 1) ? prefixRanks[mid] : (mid * 64 - prefixRanks[mid]);
            if (countBeforeMid < occurrence) {
                targetWord = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        // In words[targetWord], find which exact bit is the (occurrence - countBeforeTarget) th bit
        int countBefore = (bit == 1) ? prefixRanks[targetWord] : (targetWord * 64 - prefixRanks[targetWord]);
        int remaining = occurrence - countBefore;

        long word = words[targetWord];
        if (bit == 0) {
            word = ~word;
        }

        int baseIndex = targetWord * 64;
        for (int b = 0; b < 64 && (baseIndex + b) < length; b++) {
            if (((word >>> b) & 1L) == 1L) {
                remaining--;
                if (remaining == 0) {
                    return baseIndex + b;
                }
            }
        }

        throw new IllegalStateException("Select failed to locate occurrence: internal state inconsistency");
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public int countOnes() {
        return totalOnes;
    }

    @Override
    public int countZeros() {
        return length - totalOnes;
    }

    @Override
    public long theoreticalBits() {
        return length;
    }

    @Override
    public long approximateMemoryBytes() {
        // Object header: 16 bytes
        // int length, int totalOnes: 8 bytes
        // words reference (8 bytes) + words array (16 bytes header + 8 * length)
        // prefixRanks ref (8 bytes) + prefixRanks array (16 bytes header + 4 * length)
        long wordsBytes = 24L + ((long) words.length * 8L);
        long ranksBytes = 24L + ((long) prefixRanks.length * 4L);
        return 32L + wordsBytes + ranksBytes;
    }

    @Override
    public long allocatedBits() {
        return (long) words.length * 64L;
    }

    public int getPrefixRanksLength() {
        return prefixRanks.length;
    }

    public int getNumWords() {
        return words.length;
    }

    /**
     * Returns a compact string representation of the bit vector (up to limit bits).
     */
    public String toBitString(int maxBits) {
        StringBuilder sb = new StringBuilder();
        int show = Math.min(length, maxBits);
        for (int i = 0; i < show; i++) {
            sb.append(access(i));
        }
        if (length > maxBits) {
            sb.append("... [").append(length - maxBits).append(" more bits]");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toBitString(64);
    }
}
