package genomicindex;

import java.util.*;

/**
 * Wavelet Tree implementation over the alphabet Σ = {#, A, C, G, N, T}.
 *
 * Properties:
 * - Alphabet size: σ = 6.
 * - Maximum tree height: ⌈log₂ 6⌉ = 3.
 * - Balanced binary partitioning over the alphabet:
 *     Root: {#, A, C, G, N, T} -> left: {#, A, C}, right: {G, N, T}
 *     Left: {#, A, C} -> left: {#, A}, right: {C}
 *       Left-Left: {#, A} -> left: {#}, right: {A}
 *     Right: {G, N, T} -> left: {G, N}, right: {T}
 *       Right-Left: {G, N} -> left: {G}, right: {N}
 * - Bit-vectors in internal nodes are packed BitVectors supporting O(1) rank and O(log n) select.
 * - Leaves represent single characters and have direct references in leafMap.
 * - The original sequence string is NOT stored after construction.
 */
public class WaveletTree {

    private final int length;
    private final WaveletTreeNode root;
    private final Map<Character, WaveletTreeNode> leafMap;
    private int nextNodeId = 0;

    /**
     * Builds a single Wavelet Tree over the given sequence S.
     * @param sequence concatenated genomic sequence S (e.g. "GATTACA#CAGT#TTAGC#")
     */
    public WaveletTree(String sequence) {
        if (sequence == null) {
            throw new IllegalArgumentException("Input sequence cannot be null");
        }
        this.length = sequence.length();
        this.leafMap = new HashMap<>();

        // Validate all characters in sequence against Alphabet
        for (int i = 0; i < sequence.length(); i++) {
            char ch = sequence.charAt(i);
            if (!Alphabet.isValidSymbol(ch)) {
                throw new IllegalArgumentException("Invalid symbol in sequence at index " + i + ": '" + ch + "'");
            }
        }

        // Recursive build starting with the full alphabet
        this.root = buildSubtree(Alphabet.SYMBOLS, sequence, 0);
    }

    private WaveletTreeNode buildSubtree(List<Character> syms, String seq, int depth) {
        String nodeId = "node_" + (nextNodeId++);
        WaveletTreeNode node = new WaveletTreeNode(nodeId, syms, depth);

        if (syms.size() == 1) {
            char leafSym = syms.get(0);
            leafMap.put(leafSym, node);
            return node;
        }

        int h = (syms.size() + 1) / 2; // ceil(|syms| / 2)
        List<Character> lower = syms.subList(0, h);
        List<Character> upper = syms.subList(h, syms.size());

        int[] bits = new int[seq.length()];
        StringBuilder leftSeq = new StringBuilder();
        StringBuilder rightSeq = new StringBuilder();

        for (int i = 0; i < seq.length(); i++) {
            char ch = seq.charAt(i);
            if (node.isInUpperHalf(ch)) {
                bits[i] = 1;
                rightSeq.append(ch);
            } else {
                bits[i] = 0;
                leftSeq.append(ch);
            }
        }

        node.setBitVector(new BitVector(bits));
        node.setLeft(buildSubtree(lower, leftSeq.toString(), depth + 1));
        node.setRight(buildSubtree(upper, rightSeq.toString(), depth + 1));

        return node;
    }

    /**
     * ACCESS(i): Returns the symbol at zero-based index i in S[0..n-1].
     * Time Complexity: O(log σ) = 3 steps.
     * @param index 0 <= index < length
     * @return character at position index
     */
    public char access(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("ACCESS position out of range [0, " + (length - 1) + "]: " + index);
        }

        WaveletTreeNode node = root;
        int currIndex = index;

        while (!node.isLeaf()) {
            int bit = node.getBitVector().access(currIndex);
            currIndex = node.getBitVector().rank(bit, currIndex);
            node = (bit == 0) ? node.getLeft() : node.getRight();
        }

        return node.getLeafSymbol();
    }

    /**
     * Step information for traversal visualization.
     */
    public static class TraversalStep {
        public final String nodeId;
        public final String alphabetLabel;
        public final int depth;
        public final int index;
        public final int bit;
        public final int nextIndex;
        public final String direction; // "LEFT" or "RIGHT" or "LEAF"

        public TraversalStep(String nodeId, String alphabetLabel, int depth, int index, int bit, int nextIndex, String direction) {
            this.nodeId = nodeId;
            this.alphabetLabel = alphabetLabel;
            this.depth = depth;
            this.index = index;
            this.bit = bit;
            this.nextIndex = nextIndex;
            this.direction = direction;
        }
    }

    public static class AccessResult {
        public final int originalIndex;
        public final char symbol;
        public final List<TraversalStep> steps;
        public final long executionTimeNanos;

        public AccessResult(int originalIndex, char symbol, List<TraversalStep> steps, long executionTimeNanos) {
            this.originalIndex = originalIndex;
            this.symbol = symbol;
            this.steps = steps;
            this.executionTimeNanos = executionTimeNanos;
        }
    }

    /**
     * Detailed ACCESS query with complete tree traversal path for frontend visualizer.
     */
    public AccessResult accessWithTrace(int index) {
        long startTime = System.nanoTime();
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("ACCESS position out of range [0, " + (length - 1) + "]: " + index);
        }

        List<TraversalStep> steps = new ArrayList<>();
        WaveletTreeNode node = root;
        int currIndex = index;

        while (!node.isLeaf()) {
            int bit = node.getBitVector().access(currIndex);
            int nextIndex = node.getBitVector().rank(bit, currIndex);
            String dir = (bit == 0) ? "LEFT" : "RIGHT";
            steps.add(new TraversalStep(node.getId(), node.getAlphabetLabel(), node.getDepth(), currIndex, bit, nextIndex, dir));
            currIndex = nextIndex;
            node = (bit == 0) ? node.getLeft() : node.getRight();
        }

        steps.add(new TraversalStep(node.getId(), node.getAlphabetLabel(), node.getDepth(), currIndex, -1, currIndex, "LEAF: " + node.getLeafSymbol()));
        long elapsed = System.nanoTime() - startTime;
        return new AccessResult(index, node.getLeafSymbol(), steps, elapsed);
    }

    /**
     * RANK(c, i): Returns number of occurrences of symbol c in prefix S[0..i-1].
     * Time Complexity: O(log σ) = 3 steps.
     * @param c character in Σ
     * @param index 0 <= index <= length
     * @return count of character c before position index
     */
    public int rank(char c, int index) {
        if (!Alphabet.isValidSymbol(c)) {
            throw new IllegalArgumentException("Symbol '" + c + "' is not in alphabet " + Alphabet.SYMBOLS);
        }
        if (index < 0 || index > length) {
            throw new IndexOutOfBoundsException("RANK index out of range [0, " + length + "]: " + index);
        }

        WaveletTreeNode node = root;
        int currIndex = index;

        while (!node.isLeaf()) {
            int bit = node.isInUpperHalf(c) ? 1 : 0;
            currIndex = node.getBitVector().rank(bit, currIndex);
            node = (bit == 0) ? node.getLeft() : node.getRight();
        }

        return currIndex;
    }

    public static class RankResult {
        public final char symbol;
        public final int prefixIndex;
        public final int count;
        public final List<TraversalStep> steps;
        public final long executionTimeNanos;

        public RankResult(char symbol, int prefixIndex, int count, List<TraversalStep> steps, long executionTimeNanos) {
            this.symbol = symbol;
            this.prefixIndex = prefixIndex;
            this.count = count;
            this.steps = steps;
            this.executionTimeNanos = executionTimeNanos;
        }
    }

    /**
     * Detailed RANK query with traversal path.
     */
    public RankResult rankWithTrace(char c, int index) {
        long startTime = System.nanoTime();
        if (!Alphabet.isValidSymbol(c)) {
            throw new IllegalArgumentException("Symbol '" + c + "' is not in alphabet " + Alphabet.SYMBOLS);
        }
        if (index < 0 || index > length) {
            throw new IndexOutOfBoundsException("RANK index out of range [0, " + length + "]: " + index);
        }

        List<TraversalStep> steps = new ArrayList<>();
        WaveletTreeNode node = root;
        int currIndex = index;

        while (!node.isLeaf()) {
            int bit = node.isInUpperHalf(c) ? 1 : 0;
            int nextIndex = node.getBitVector().rank(bit, currIndex);
            String dir = (bit == 0) ? "LEFT" : "RIGHT";
            steps.add(new TraversalStep(node.getId(), node.getAlphabetLabel(), node.getDepth(), currIndex, bit, nextIndex, dir));
            currIndex = nextIndex;
            node = (bit == 0) ? node.getLeft() : node.getRight();
        }

        steps.add(new TraversalStep(node.getId(), node.getAlphabetLabel(), node.getDepth(), currIndex, -1, currIndex, "RESULT: " + currIndex));
        long elapsed = System.nanoTime() - startTime;
        return new RankResult(c, index, currIndex, steps, elapsed);
    }

    /**
     * SELECT(c, k): Returns 0-based position of the k-th occurrence of symbol c.
     * k is 1-based (k = 1, 2, ...).
     * Time Complexity: O(log σ · log n) with binary-search select on bit-vectors.
     * @param c character in Σ
     * @param k occurrence number (1-based)
     * @return 0-based position in S
     */
    public int select(char c, int k) {
        if (!Alphabet.isValidSymbol(c)) {
            throw new IllegalArgumentException("Symbol '" + c + "' is not in alphabet " + Alphabet.SYMBOLS);
        }
        int totalOccurrences = rank(c, length);
        if (k <= 0 || k > totalOccurrences) {
            throw new IllegalArgumentException("SELECT(" + c + ", " + k + ") invalid: total occurrences of '"
                    + c + "' is " + totalOccurrences + ", requested k=" + k);
        }

        WaveletTreeNode node = leafMap.get(c);
        if (node == null) {
            throw new IllegalStateException("Leaf node for symbol '" + c + "' not found in tree");
        }

        int j = k;
        while (node != root) {
            WaveletTreeNode parent = node.getParent();
            int bit = (node == parent.getLeft()) ? 0 : 1;
            j = parent.getBitVector().select(bit, j) + 1;
            node = parent;
        }

        return j - 1;
    }

    public static class SelectStep {
        public final String fromNodeId;
        public final String toNodeId;
        public final String nodeLabel;
        public final int bit;
        public final int incomingOccurrence;
        public final int mappedPositionInParent;

        public SelectStep(String fromNodeId, String toNodeId, String nodeLabel, int bit, int incomingOccurrence, int mappedPositionInParent) {
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.nodeLabel = nodeLabel;
            this.bit = bit;
            this.incomingOccurrence = incomingOccurrence;
            this.mappedPositionInParent = mappedPositionInParent;
        }
    }

    public static class SelectResult {
        public final char symbol;
        public final int occurrence;
        public final int globalPosition;
        public final List<SelectStep> steps;
        public final long executionTimeNanos;

        public SelectResult(char symbol, int occurrence, int globalPosition, List<SelectStep> steps, long executionTimeNanos) {
            this.symbol = symbol;
            this.occurrence = occurrence;
            this.globalPosition = globalPosition;
            this.steps = steps;
            this.executionTimeNanos = executionTimeNanos;
        }
    }

    /**
     * Detailed SELECT query with bottom-up upward traversal path.
     */
    public SelectResult selectWithTrace(char c, int k) {
        long startTime = System.nanoTime();
        if (!Alphabet.isValidSymbol(c)) {
            throw new IllegalArgumentException("Symbol '" + c + "' is not in alphabet " + Alphabet.SYMBOLS);
        }
        int totalOccurrences = rank(c, length);
        if (k <= 0 || k > totalOccurrences) {
            throw new IllegalArgumentException("SELECT(" + c + ", " + k + ") invalid: total occurrences of '"
                    + c + "' is " + totalOccurrences + ", requested k=" + k);
        }

        WaveletTreeNode node = leafMap.get(c);
        List<SelectStep> steps = new ArrayList<>();
        int j = k;

        while (node != root) {
            WaveletTreeNode parent = node.getParent();
            int bit = (node == parent.getLeft()) ? 0 : 1;
            int parentPos0Based = parent.getBitVector().select(bit, j);
            int nextJ = parentPos0Based + 1;
            steps.add(new SelectStep(node.getId(), parent.getId(), parent.getAlphabetLabel(), bit, j, parentPos0Based));
            j = nextJ;
            node = parent;
        }

        int finalPos = j - 1;
        long elapsed = System.nanoTime() - startTime;
        return new SelectResult(c, k, finalPos, steps, elapsed);
    }

    public int length() {
        return length;
    }

    public WaveletTreeNode getRoot() {
        return root;
    }

    public int getNodeCount() {
        return root != null ? root.countSubtreeNodes() : 0;
    }

    public int getHeight() {
        return root != null ? root.getMaxDepth() : 0;
    }

    public long getStoredBits() {
        return root != null ? root.countStoredBits() : 0L;
    }

    public long getAllocatedBits() {
        return root != null ? root.countAllocatedBits() : 0L;
    }

    public long getRankMetadataBits() {
        return root != null ? (root.countPrefixRankBytes() * 8L) : 0L;
    }

    public int getAlphabetCapacity() {
        return Alphabet.SIZE;
    }

    public int getDistinctSymbolsPresent() {
        int present = 0;
        for (char ch : Alphabet.SYMBOLS) {
            if (rank(ch, length) > 0) {
                present++;
            }
        }
        return present;
    }

    public long getTheoreticalBits() {
        return (long) length * 3L; // n * ceil(log2 sigma)
    }

    public long getApproximateMemoryBytes() {
        long treeHeader = 32L;
        return treeHeader + (root != null ? root.countApproximateMemoryBytes() : 0L);
    }
}
