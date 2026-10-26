# GeneDex — High-Performance Genomic Sequence Indexing Platform

## Module 2: Wavelet Tree & Alphabet Decomposition
* **Author & Contributor**: Mahasri (Member 2)
* **Primary Data Structure**: Wavelet Tree over DNA Alphabet $\Sigma = \{\#, A, C, G, N, T\}$
* **Core Algorithm**: Recursive Alphabet Binary Partitioning & $O(\log |\Sigma|)$ Character Rank/Access/Select

---

### Member 2 (Mahasri) Files Committed

| Layer | File Path | Description |
| :--- | :--- | :--- |
| **Backend Core** | `src/main/java/genomicindex/Alphabet.java` | Defines the canonical 6-symbol DNA alphabet $\Sigma = \{\#, A, C, G, N, T\}$, symbol indexing, and validation rules. |
| **Backend Core** | `src/main/java/genomicindex/WaveletTreeNode.java` | Represents recursive tree nodes, sub-alphabet range partitioning, and bit-vector routing decisions. |
| **Backend Core** | `src/main/java/genomicindex/WaveletTree.java` | Main Wavelet Tree engine providing $O(\log |\Sigma|)$ rank, access, and select queries across the genomic alphabet. |
| **Frontend UI** | `public/pages/analytics.js` | Interactive Genomic Analytics dashboard for nucleotide composition ($A, C, G, T, N$), GC/AT ratios, and read length distributions. |
| **Frontend Mirror** | `src/main/resources/web/pages/analytics.js` | Mirror for embedded deployment in the standalone Java WebServer. |

---

### Mathematical Model & Complexities

1. **Space Complexity**:
   $$N \log_2 |\Sigma| + o(N \log_2 |\Sigma|) \text{ bits}$$
   For DNA ($|\Sigma| = 6$), the tree depth is $\lceil \log_2 6 \rceil = 3$, achieving massive compression over raw ASCII bytes.

2. **Query Complexities**:
   - $\text{Rank}_c(i)$: $O(\log |\Sigma|)$ — traverses 3 levels of bit vectors.
   - $\text{Access}(i)$: $O(\log |\Sigma|)$ — reconstructs symbol at index $i$ without decompressing.
   - $\text{Select}_c(k)$: $O(\log |\Sigma|)$ — bottom-up traversal to find the $k$-th occurrence of character $c$.
