package genomicindex;

import java.util.Collections;
import java.util.List;

/**
 * Represents a single node in the Wavelet Tree.
 *
 * Each internal node contains:
 * - The sub-alphabet range it governs (partitioned into lower and upper halves).
 * - A BitVector recording routing decisions (0 -> lower half / left child, 1 -> upper half / right child).
 * - References to its left and right children.
 * - Reference to its parent (used during bottom-up SELECT traversal).
 *
 * Each leaf node represents exactly one terminal alphabet symbol and has no bit-vector.
 */
public class WaveletTreeNode {

    private final String id;
    private final List<Character> symbols;
    private final boolean isLeaf;
    private final char leafSymbol;
    private final int depth;

    private BitVector bitVector;
    private WaveletTreeNode left;
    private WaveletTreeNode right;
    private WaveletTreeNode parent;

    /**
     * Constructs an internal node or leaf node for the given symbol set.
     */
    public WaveletTreeNode(String id, List<Character> symbols, int depth) {
        this.id = id;
        this.symbols = Collections.unmodifiableList(symbols);
        this.depth = depth;
        if (symbols.size() == 1) {
            this.isLeaf = true;
            this.leafSymbol = symbols.get(0);
            this.bitVector = null;
        } else {
            this.isLeaf = false;
            this.leafSymbol = '\0';
            this.bitVector = null; // populated during build
        }
    }

    public String getId() {
        return id;
    }

    public List<Character> getSymbols() {
        return symbols;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public char getLeafSymbol() {
        return leafSymbol;
    }

    public int getDepth() {
        return depth;
    }

    public BitVector getBitVector() {
        return bitVector;
    }

    public void setBitVector(BitVector bitVector) {
        this.bitVector = bitVector;
    }

    public WaveletTreeNode getLeft() {
        return left;
    }

    public void setLeft(WaveletTreeNode left) {
        this.left = left;
        if (left != null) {
            left.setParent(this);
        }
    }

    public WaveletTreeNode getRight() {
        return right;
    }

    public void setRight(WaveletTreeNode right) {
        this.right = right;
        if (right != null) {
            right.setParent(this);
        }
    }

    public WaveletTreeNode getParent() {
        return parent;
    }

    public void setParent(WaveletTreeNode parent) {
        this.parent = parent;
    }

    /**
     * Checks if a character belongs to the upper half of this node's alphabet.
     * With h = ceil(|syms| / 2), lower is syms[0..h-1], upper is syms[h..end].
     * @param ch symbol
     * @return true if ch is in upper half (bit 1), false if in lower half (bit 0)
     */
    public boolean isInUpperHalf(char ch) {
        int h = (symbols.size() + 1) / 2;
        for (int i = h; i < symbols.size(); i++) {
            if (symbols.get(i) == ch) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a character belongs to this node's symbol set.
     */
    public boolean containsSymbol(char ch) {
        return symbols.contains(ch);
    }

    /**
     * Alphabet label for display (e.g., "{#, A, C}" or "G").
     */
    public String getAlphabetLabel() {
        if (isLeaf) {
            return String.valueOf(leafSymbol);
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < symbols.size(); i++) {
            sb.append(symbols.get(i));
            if (i < symbols.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Recursively computes the number of nodes in this subtree.
     */
    public int countSubtreeNodes() {
        int count = 1;
        if (left != null) count += left.countSubtreeNodes();
        if (right != null) count += right.countSubtreeNodes();
        return count;
    }

    /**
     * Recursively computes the maximum root-to-leaf edge depth in this subtree.
     */
    public int getMaxDepth() {
        if (isLeaf) return depth;
        int max = depth;
        if (left != null) max = Math.max(max, left.getMaxDepth());
        if (right != null) max = Math.max(max, right.getMaxDepth());
        return max;
    }

    /**
     * Recursively computes the actual stored bit entries across internal node bit-vectors.
     */
    public long countStoredBits() {
        long bits = bitVector != null ? bitVector.length() : 0L;
        if (left != null) bits += left.countStoredBits();
        if (right != null) bits += right.countStoredBits();
        return bits;
    }

    /**
     * Recursively computes the packed 64-bit word allocation in bits across internal nodes.
     */
    public long countAllocatedBits() {
        long bits = bitVector != null ? bitVector.allocatedBits() : 0L;
        if (left != null) bits += left.countAllocatedBits();
        if (right != null) bits += right.countAllocatedBits();
        return bits;
    }

    /**
     * Recursively computes the rank metadata prefix rank array bytes across internal nodes.
     */
    public long countPrefixRankBytes() {
        long bytes = bitVector != null ? ((long) bitVector.getPrefixRanksLength() * 4L) : 0L;
        if (left != null) bytes += left.countPrefixRankBytes();
        if (right != null) bytes += right.countPrefixRankBytes();
        return bytes;
    }

    /**
     * Recursively computes theoretical bit storage of bit-vectors in this subtree.
     */
    public long countTheoreticalBits() {
        long bits = bitVector != null ? bitVector.theoreticalBits() : 0L;
        if (left != null) bits += left.countTheoreticalBits();
        if (right != null) bits += right.countTheoreticalBits();
        return bits;
    }

    /**
     * Recursively computes approximate JVM memory consumption in bytes.
     */
    public long countApproximateMemoryBytes() {
        // Node object overhead: ~48 bytes
        long bytes = 48L;
        if (bitVector != null) {
            bytes += bitVector.approximateMemoryBytes();
        }
        if (left != null) bytes += left.countApproximateMemoryBytes();
        if (right != null) bytes += right.countApproximateMemoryBytes();
        return bytes;
    }
}
