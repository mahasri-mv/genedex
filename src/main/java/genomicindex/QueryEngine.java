package genomicindex;

import java.util.*;

/**
 * Executes high-level genomic queries strictly using a single WaveletTree.
 *
 * Implements:
 * - COUNT-BASE(c)
 * - RANGE-COUNT(c, start, end)
 * - READ-COUNT()
 * - READ-START(k)
 * - READ-END(k)
 * - READ-OF(pos)
 * - EXTRACT-READ(k)
 * - GC-CONTENT(k)
 * - FIND(pattern) [rarest-character heuristic]
 *
 * This class NEVER maintains or queries any secondary persistent sequence index.
 * All queries are answered by ACCESS, RANK, and SELECT on the single authoritative WaveletTree.
 */
public class QueryEngine {

    private final WaveletTree tree;

    public QueryEngine(WaveletTree tree) {
        if (tree == null) {
            throw new IllegalArgumentException("WaveletTree instance cannot be null");
        }
        this.tree = tree;
    }

    public WaveletTree getTree() {
        return tree;
    }

    /**
     * OPERATION 5: COUNT-BASE(c)
     * Counts occurrences of a nucleotide across the complete dataset.
     * Formula: RANK(c, n)
     * Complexity: O(log σ)
     */
    public int countBase(char c) {
        return tree.rank(c, tree.length());
    }

    /**
     * OPERATION 6: RANGE-COUNT(c, start, end)
     * Counts occurrences of symbol c in the half-open interval [start, end).
     * Formula: RANK(c, end) - RANK(c, start)
     * Complexity: O(log σ)
     */
    public int rangeCount(char c, int start, int end) {
        if (start < 0 || end < start || end > tree.length()) {
            throw new IndexOutOfBoundsException("Invalid range [" + start + ", " + end + ") for length " + tree.length());
        }
        return tree.rank(c, end) - tree.rank(c, start);
    }

    /**
     * OPERATION 7: READ-COUNT()
     * Returns total number of reads.
     * Formula: RANK('#', n)
     * Complexity: O(log σ)
     */
    public int readCount() {
        return tree.rank(Alphabet.SEPARATOR, tree.length());
    }

    /**
     * OPERATION 8: READ-START(k)
     * Returns 0-based global start position of read k (0-based k).
     * Formula: k == 0 ? 0 : SELECT('#', k) + 1
     * Complexity: O(log σ · log n)
     */
    public int readStart(int k) {
        int totalReads = readCount();
        if (k < 0 || k >= totalReads) {
            throw new IndexOutOfBoundsException("Read index " + k + " out of bounds [0, " + (totalReads - 1) + "]");
        }
        if (k == 0) {
            return 0;
        }
        return tree.select(Alphabet.SEPARATOR, k) + 1;
    }

    /**
     * OPERATION 9: READ-END(k)
     * Returns 0-based exclusive end position of read k (the position of its terminating '#').
     * Formula: SELECT('#', k + 1)
     * Complexity: O(log σ · log n)
     */
    public int readEnd(int k) {
        int totalReads = readCount();
        if (k < 0 || k >= totalReads) {
            throw new IndexOutOfBoundsException("Read index " + k + " out of bounds [0, " + (totalReads - 1) + "]");
        }
        return tree.select(Alphabet.SEPARATOR, k + 1);
    }

    /**
     * OPERATION 10: READ-OF(pos)
     * Returns the 0-based read number containing global position pos.
     * Formula: RANK('#', pos)
     * If pos falls on a '#' separator, it is not a base inside any read.
     * Complexity: O(log σ)
     */
    public int readOf(int pos) {
        if (pos < 0 || pos >= tree.length()) {
            throw new IndexOutOfBoundsException("Position " + pos + " out of range [0, " + (tree.length() - 1) + "]");
        }
        return tree.rank(Alphabet.SEPARATOR, pos);
    }

    /**
     * Checks if a global position is a '#' separator boundary.
     */
    public boolean isSeparator(int pos) {
        if (pos < 0 || pos >= tree.length()) {
            throw new IndexOutOfBoundsException("Position " + pos + " out of range [0, " + (tree.length() - 1) + "]");
        }
        return tree.access(pos) == Alphabet.SEPARATOR;
    }

    /**
     * OPERATION 11: EXTRACT-READ(k)
     * Reconstructs the DNA sequence of read k using iterative ACCESS calls.
     * Complexity: O(L log σ), where L is read length.
     */
    public String extractRead(int k) {
        int s = readStart(k);
        int e = readEnd(k);
        StringBuilder sb = new StringBuilder(e - s);
        for (int pos = s; pos < e; pos++) {
            sb.append(tree.access(pos));
        }
        return sb.toString();
    }

    /**
     * OPERATION 12: GC-CONTENT(k)
     * Calculates the GC percentage for read k.
     * Formula: ((RANGE-COUNT('G', s, e) + RANGE-COUNT('C', s, e)) / (e - s)) * 100.0
     * Complexity: O(log σ) - exactly 4 RANK calls, independent of read length!
     */
    public double gcContent(int k) {
        int s = readStart(k);
        int e = readEnd(k);
        int readLen = e - s;
        if (readLen == 0) {
            return 0.0;
        }
        int gCount = rangeCount(Alphabet.G, s, e);
        int cCount = rangeCount(Alphabet.C, s, e);
        return ((double) (gCount + cCount) / (double) readLen) * 100.0;
    }

    /**
     * Detailed candidate trace for FIND algorithm.
     */
    public static class CandidateEvaluation {
        public final int candidateIndex;
        public final int candidateStart;
        public final int targetCharGlobalPos;
        public final boolean accepted;
        public final String statusReason;
        public final Integer readIndex;
        public final Integer offsetInRead;

        public CandidateEvaluation(int candidateIndex, int candidateStart, int targetCharGlobalPos,
                                   boolean accepted, String statusReason, Integer readIndex, Integer offsetInRead) {
            this.candidateIndex = candidateIndex;
            this.candidateStart = candidateStart;
            this.targetCharGlobalPos = targetCharGlobalPos;
            this.accepted = accepted;
            this.statusReason = statusReason;
            this.readIndex = readIndex;
            this.offsetInRead = offsetInRead;
        }
    }

    public static class FindResult {
        public final String pattern;
        public final char rarestChar;
        public final int rarestCharIndexInPattern;
        public final int rarestCharTotalCount;
        public final Map<Character, Integer> patternCharFrequencies;
        public final List<Match> matches;
        public final List<CandidateEvaluation> candidates;
        public final long executionTimeNanos;

        public FindResult(String pattern, char rarestChar, int rarestCharIndexInPattern,
                          int rarestCharTotalCount, Map<Character, Integer> patternCharFrequencies,
                          List<Match> matches, List<CandidateEvaluation> candidates, long executionTimeNanos) {
            this.pattern = pattern;
            this.rarestChar = rarestChar;
            this.rarestCharIndexInPattern = rarestCharIndexInPattern;
            this.rarestCharTotalCount = rarestCharTotalCount;
            this.patternCharFrequencies = patternCharFrequencies;
            this.matches = matches;
            this.candidates = candidates;
            this.executionTimeNanos = executionTimeNanos;
        }
    }

    /**
     * OPERATION 13: FIND(pattern)
     * Searches for all exact occurrences of pattern P across the dataset.
     *
     * Algorithm (Rarest-Character Heuristic):
     * 1. Validate pattern: non-empty, contains only valid nucleotides {A, C, G, N, T}.
     * 2. Compute frequency of each distinct character in P using RANK.
     * 3. Select rarest character a = P[r] with smallest RANK(a, n).
     * 4. Enumerate occurrences of a via SELECT(a, j) for j = 1..cnt.
     * 5. Generate candidate start positions: start = pos - r.
     * 6. Verify each candidate using ACCESS: check character matches and ensure no '#' boundary is crossed.
     * 7. Map valid matches to (readIndex, offsetInRead, globalPosition).
     *
     * Complexity: O((m + cnt * m) log σ)
     */
    public FindResult find(String pattern) {
        long startTime = System.nanoTime();

        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }
        pattern = pattern.toUpperCase();
        int m = pattern.length();
        int n = tree.length();

        // 1. Validate pattern nucleotides
        for (int i = 0; i < m; i++) {
            char ch = pattern.charAt(i);
            if (!Alphabet.isValidNucleotide(ch)) {
                throw new IllegalArgumentException("Invalid nucleotide in pattern at position " + i + ": '" + ch
                        + "'. Only {A, C, G, N, T} are permitted.");
            }
        }

        // 2. Count frequency of each character in pattern using RANK
        Map<Character, Integer> charFreqs = new LinkedHashMap<>();
        int rarestIndex = 0;
        char rarestChar = pattern.charAt(0);
        int rarestCount = Integer.MAX_VALUE;

        for (int i = 0; i < m; i++) {
            char ch = pattern.charAt(i);
            if (!charFreqs.containsKey(ch)) {
                int count = countBase(ch);
                charFreqs.put(ch, count);
                if (count < rarestCount) {
                    rarestCount = count;
                    rarestChar = ch;
                    rarestIndex = i;
                }
            }
        }

        List<Match> matches = new ArrayList<>();
        List<CandidateEvaluation> candidates = new ArrayList<>();

        // If rarest character never occurs in the sequence, no match can exist
        if (rarestCount == 0) {
            long elapsed = System.nanoTime() - startTime;
            return new FindResult(pattern, rarestChar, rarestIndex, 0, charFreqs, matches, candidates, elapsed);
        }

        // 4. Enumerate candidates via SELECT
        for (int j = 1; j <= rarestCount; j++) {
            int posOfRarest = tree.select(rarestChar, j);
            int start = posOfRarest - rarestIndex;

            // Boundary bounds check
            if (start < 0 || start + m > n) {
                candidates.add(new CandidateEvaluation(j, start, posOfRarest, false,
                        "Candidate start position " + start + " out of sequence bounds [0, " + (n - m) + "]", null, null));
                continue;
            }

            // Verify characters using ACCESS and ensure no separator '#' is crossed
            boolean matchOk = true;
            String rejectReason = null;

            for (int t = 0; t < m; t++) {
                char seqChar = tree.access(start + t);
                if (seqChar == Alphabet.SEPARATOR) {
                    matchOk = false;
                    rejectReason = "Candidate crosses read boundary '#' at global position " + (start + t);
                    break;
                }
                if (seqChar != pattern.charAt(t)) {
                    matchOk = false;
                    rejectReason = "Mismatch at offset " + t + ": expected '" + pattern.charAt(t) + "', found '" + seqChar + "'";
                    break;
                }
            }

            if (matchOk) {
                // Ensure the match is strictly within a single read
                int startRead = readOf(start);
                int endRead = readOf(start + m - 1);

                if (startRead != endRead) {
                    candidates.add(new CandidateEvaluation(j, start, posOfRarest, false,
                            "Match spans multiple reads (" + startRead + " to " + endRead + ")", null, null));
                } else {
                    int offset = start - readStart(startRead);
                    Match match = new Match(startRead, offset, start, pattern);
                    matches.add(match);
                    candidates.add(new CandidateEvaluation(j, start, posOfRarest, true,
                            "Exact match verified in Read " + startRead + " at offset " + offset, startRead, offset));
                }
            } else {
                candidates.add(new CandidateEvaluation(j, start, posOfRarest, false, rejectReason, null, null));
            }
        }

        long elapsed = System.nanoTime() - startTime;
        return new FindResult(pattern, rarestChar, rarestIndex, rarestCount, charFreqs, matches, candidates, elapsed);
    }
}
