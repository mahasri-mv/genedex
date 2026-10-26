package genomicindex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Defines the canonical genomic alphabet for the Wavelet Tree.
 * Alphabet: Σ = {#, A, C, G, N, T}
 * Canonical Ordering: # < A < C < G < N < T
 * Alphabet Size: σ = 6
 * Maximum Tree Height: ⌈log₂ 6⌉ = 3
 */
public final class Alphabet {
    public static final char SEPARATOR = '#';
    public static final char A = 'A';
    public static final char C = 'C';
    public static final char G = 'G';
    public static final char N = 'N';
    public static final char T = 'T';

    public static final List<Character> SYMBOLS = Collections.unmodifiableList(
            Arrays.asList(SEPARATOR, A, C, G, N, T)
    );

    public static final int SIZE = SYMBOLS.size(); // 6

    private Alphabet() {
        // Prevent instantiation of utility class
    }

    /**
     * Checks if a character belongs to the supported alphabet Σ.
     * @param ch character to validate
     * @return true if ch is in {#, A, C, G, N, T}
     */
    public static boolean isValidSymbol(char ch) {
        return ch == SEPARATOR || ch == A || ch == C || ch == G || ch == N || ch == T;
    }

    /**
     * Checks if a character is a valid nucleotide (excludes separator).
     * @param ch character to validate
     * @return true if ch is in {A, C, G, N, T}
     */
    public static boolean isValidNucleotide(char ch) {
        return ch == A || ch == C || ch == G || ch == N || ch == T;
    }

    /**
     * Returns the 0-based index of the symbol according to the canonical ordering.
     * @param ch character
     * @return 0 for '#', 1 for 'A', 2 for 'C', 3 for 'G', 4 for 'N', 5 for 'T'
     * @throws IllegalArgumentException if ch is not in the alphabet
     */
    public static int getSymbolIndex(char ch) {
        switch (ch) {
            case SEPARATOR: return 0;
            case A: return 1;
            case C: return 2;
            case G: return 3;
            case N: return 4;
            case T: return 5;
            default:
                throw new IllegalArgumentException("Symbol '" + ch + "' is not in the supported alphabet " + SYMBOLS);
        }
    }

    /**
     * Returns the symbol at the specified index in the canonical ordering.
     * @param index 0 to 5
     * @return character
     */
    public static char getSymbolAt(int index) {
        if (index < 0 || index >= SIZE) {
            throw new IndexOutOfBoundsException("Alphabet index out of range [0, " + (SIZE - 1) + "]: " + index);
        }
        return SYMBOLS.get(index);
    }
}
