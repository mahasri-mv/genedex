# 🧬 GeneDex — Compressed Genomic Sequence Indexing Using a Wavelet Tree

**Fast DNA pattern search over FASTQ data using a single Wavelet Tree, built in pure Java SE with a web dashboard.**

🌐 **Live demo:** https://gen-dex.onrender.com
*(Hosted on Render's free tier. The first request after inactivity may take 30–60 seconds while the server wakes up.)*

---

## 📌 Problem Statement

High-throughput sequencing produces FASTQ files with millions of short DNA reads. Downstream analysis keeps asking the same questions:

1. How often does a nucleotide occur in the whole dataset?
2. What is the base composition and GC content of one read?
3. Where is the *k*-th occurrence of a nucleotide?
4. Where does a DNA motif occur in any read?

Scanning plain text answers each of these in **O(n)** per query. GeneDex answers them all from one compact index.

## 💡 Why a Wavelet Tree? (Data Structure Justification)

All reads are concatenated with `#` as a separator into one string:

```
S = read₀ # read₁ # … # read_{r-1} #        Σ = { #, A, C, G, N, T }   (σ = 6)
```

A single Wavelet Tree is built over `S`. Because σ is tiny, the tree height is fixed at **⌈log₂ 6⌉ = 3**, whether the file holds 10³ or 10⁹ bases.

| Alternative | Why it was rejected |
| :--- | :--- |
| **Plain array / text scan** | O(n) per query |
| **Prefix-sum tables** (one count array per symbol) | ~5 × 32 bits per base, so memory is huge |
| **BST** | Degenerates into a linear chain on ordered data |
| **AVL tree** | Pointers and counters at every node cost roughly 250+ bits per base, and height grows with n |
| **Suffix array / k-mer hash index** | Secondary indices stored *beside* the text, which multiplies storage |

**Wavelet Tree:** about `n·⌈log₂σ⌉` bits of payload, constant height, and every query reduces to three primitives, **ACCESS, RANK and SELECT**. No auxiliary persistent index is stored.

### Tree shape (11 nodes: 5 internal, 6 leaves)

```
                    Root {#, A, C, G, N, T}
                  0 /                       \ 1
          {#, A, C}                           {G, N, T}
         0 /      \ 1                        0 /      \ 1
     {#, A}       Leaf C                  {G, N}      Leaf T
    0 /   \ 1                            0 /   \ 1
 Leaf #   Leaf A                      Leaf G   Leaf N
```

## ⚙️ Implementation Strategy

| Component | Choice | Trade-off |
| :--- | :--- | :--- |
| **Tree nodes** (`WaveletTreeNode`) | Pointer-based nodes (left, right, parent) | The tree has only 11 nodes, so pointer overhead is negligible. Parent links give a simple bottom-up SELECT. |
| **Bit-vectors** (`BitVector`) | Packed `long[]` words (64 bits per word) with a precomputed `int[]` prefix-rank array per word | RANK is **O(1)**: one array lookup plus `Long.bitCount` on a masked word. Costs about 32 extra bits per 64 bits stored. |
| **SELECT** | Binary search over word prefix counts, then a scan of ≤ 64 bits inside one word | O(log n) with no extra select-structure memory. |
| **Alphabet** (`Alphabet`) | Fixed canonical order `# < A < C < G < N < T` | Keeps the tree balanced and the height constant. |
| **FIND** (`QueryEngine`) | Rarest-character heuristic: pick the pattern symbol with the smallest RANK count, SELECT each of its occurrences, then verify the rest with ACCESS | Avoids scanning the full text. Worst case occurs when every symbol is equally common. |

## ⏱️ Time Complexity

Let σ = 6 (so log σ = 3, a constant), n = |S|, L = read length, m = pattern length.

| Operation | Time | How |
| :--- | :--- | :--- |
| **BUILD** | O(n log σ) | 3 levels of linear partitioning |
| **ACCESS(i)** | O(log σ) | One bit-vector access + rank per level, top-down |
| **RANK(c, i)** | O(log σ) | O(1) bit-vector rank per level |
| **SELECT(c, k)** | O(log σ · log n) | Bottom-up climb, one binary-search select per level |
| **COUNT-BASE(c)** | O(log σ) | `RANK(c, n)` |
| **RANGE-COUNT(c, s, e)** | O(log σ) | `RANK(c, e) − RANK(c, s)` |
| **READ-COUNT()** | O(log σ) | `RANK('#', n)` |
| **READ-START(k)** | O(log σ · log n) | `k == 0 ? 0 : SELECT('#', k) + 1` |
| **READ-END(k)** | O(log σ · log n) | `SELECT('#', k + 1)` |
| **READ-OF(pos)** | O(log σ) | `RANK('#', pos)` |
| **EXTRACT-READ(k)** | O(L · log σ) | `ACCESS` on each of the L positions |
| **GC-CONTENT(k)** | O(log σ) per RANK call | A fixed number of RANK calls over the read's range, independent of read length |
| **FIND(P)** | O((m + cnt·m) · log σ) | Rarest-character heuristic via RANK, SELECT, ACCESS |

Since σ is a constant, these reduce to **O(1)** for ACCESS / RANK and **O(log n)** for SELECT-based operations.

## 🚀 Features

- **13 genomic operations:** ACCESS, RANK, SELECT, COUNT-BASE, RANGE-COUNT, READ-COUNT, READ-START, READ-END, READ-OF, EXTRACT-READ, GC-CONTENT, FIND and statistics.
- **FASTQ upload** by file or live text editor, with strict 4-line validation.
- **Wavelet Tree visualizer:** interactive SVG with animated query traversal paths.
- **Operations Lab:** a test bench for every operation.
- **DNA pattern search** with candidate verification breakdown and timing.
- **Analytics:** nucleotide frequencies and per-read GC% profiles.
- **Reports and exports:** nucleotides, reads, search results and a full report.
- **Authentication and RBAC:** `ADMIN`, `REGISTERED_USER` and `GUEST` roles.
- **Admin dashboard:** user management, system health and audit logs.

### Security highlights

- PBKDF2WithHmacSHA256 password hashing (65,536 iterations, random 16-byte salt, constant-time comparison)
- HttpOnly, SameSite=Lax session cookies with 32-byte random tokens
- Per-session CSRF tokens on state-changing requests
- Account lockout after 5 failed logins (15 minutes)
- Single-use password reset tokens (30-minute expiry)
- Append-only JSONL audit log
- Per-user dataset isolation

## 🗂️ Project Structure

```
GeneDex/
├── src/
│   ├── main/
│   │   ├── java/genomicindex/
│   │   │   ├── Alphabet.java              # Alphabet and canonical order
│   │   │   ├── RankSelectBitVector.java   # Bit-vector interface
│   │   │   ├── BitVector.java             # Packed words + prefix-rank
│   │   │   ├── WaveletTreeNode.java       # Internal nodes / leaves
│   │   │   ├── WaveletTree.java           # Build, ACCESS, RANK, SELECT
│   │   │   ├── FastqParser.java           # FASTQ validation and parsing
│   │   │   ├── Match.java                 # Pattern match result
│   │   │   ├── QueryEngine.java           # Derived genomic operations
│   │   │   ├── IndexManager.java          # Facade, telemetry, rebuilds
│   │   │   ├── DatasetRegistry.java       # Per-user dataset storage
│   │   │   ├── WebServer.java             # Built-in HTTP server + REST API
│   │   │   └── auth/                      # PasswordHasher, User, UserDatabase, SessionManager
│   │   └── resources/web/                 # Frontend (HTML, CSS, JS modules)
│   └── test/java/genomicindex/
│       ├── TestRunner.java                # Wavelet Tree unit + fuzz tests
│       ├── AuthTest.java                  # Auth, RBAC, isolation tests
│       └── FastqValidationTest.java       # FASTQ validation tests
├── public/                                # Static frontend served in deployment
├── sample-data/                           # sample.fastq, extended_test.fastq
├── Dockerfile                             # Container build (Render)
├── render.yaml                            # Render service config
├── vercel.json                            # Frontend rewrites to the Render API
├── build.bat · run.bat · test.bat         # Windows scripts
└── README.md
```

## 🛠️ Getting Started

**Prerequisites:** JDK 17 or higher. No Maven, Gradle or external JARs are needed.

### Windows

```cmd
build.bat      :: compile sources and tests into bin\
test.bat       :: run all test suites
run.bat        :: start server on port 8080 and open the browser
run.bat 8085   :: use a different port
```

### Linux / macOS

```bash
mkdir -p bin
javac -encoding UTF-8 -d bin src/main/java/genomicindex/*.java src/main/java/genomicindex/auth/*.java
javac -encoding UTF-8 -cp bin -d bin src/test/java/genomicindex/*.java

java -cp bin genomicindex.TestRunner      # Wavelet Tree tests
java -cp bin genomicindex.AuthTest        # Auth / RBAC tests
java -cp bin genomicindex.WebServer 8080  # then open http://localhost:8080
```

The server reads the `PORT` environment variable when set, otherwise the first argument, otherwise 8080.

### Docker

```bash
docker build -t genedex .
docker run -p 8080:8080 genedex
```

## ✅ Sample Verification

Dataset (`sample-data/sample.fastq`):

```fastq
@read0
GATTACA
+
IIIIIII
@read1
CAGT
+
IIII
@read2
TTAGC
+
IIIII
```

`S = GATTACA#CAGT#TTAGC#` (n = 19)

| Operation | Input | Result |
| :--- | :--- | :---: |
| READ-COUNT() | — | `3` |
| Total symbols / bases | — | `19` / `16` |
| ACCESS(10) | index 10 | `G` |
| RANK(T, 12) | symbol T, prefix 12 | `3` |
| SELECT(A, 3) | 3rd A | `6` |
| COUNT-BASE(A) | A | `5` |
| READ-START(1) | read 1 | `8` |
| READ-END(1) | read 1 | `12` |
| READ-OF(15) | position 15 | `2` |
| EXTRACT-READ(1) | read 1 | `CAGT` |
| GC-CONTENT(2) | read 2 | `40.0%` |
| FIND("AGC") | pattern | read 2, offset 2, global 15 |

These values are asserted in `TestRunner.java`.

## 🔌 REST API (selected)

| Group | Endpoints |
| :--- | :--- |
| Auth | `/api/auth/login`, `register`, `logout`, `me`, `guest`, `profile`, `forgot-password`, `reset-password` |
| Admin | `/api/admin/users`, `audit-logs`, `system-health`, `consistency-check` |
| Data | `/api/upload`, `load-sample`, `datasets`, `rebuild`, `status`, `statistics`, `tree`, `analytics`, `reads` |
| Queries | `/api/access`, `rank`, `select`, `count-base`, `range-count`, `read-count`, `read-start`, `read-end`, `read-of`, `extract-read`, `gc-content`, `find` |
| Export | `/api/export/nucleotides`, `reads`, `search`, `report` |

## 🔑 Demo Accounts

Seeded automatically for evaluation. The Sign In modal also has quick-fill buttons and a one-click **Guest** option (read-only).

| Role | Email | Password |
| :--- | :--- | :--- |
| Admin | `admin@genomicdemo.local` | `AdminDemo@2026!` |
| Researcher | `researcher@genomicdemo.local` | `ResearchDemo@2026!` |
| Lab Technician | `lab@genomicdemo.local` | `LabDemo@2026!` |
| Clinical Researcher | `clinical@genomicdemo.local` | `ClinicalDemo@2026!` |
| Student | `student@genomicdemo.local` | `StudentDemo@2026!` |

> ⚠️ These are demo credentials only. Change or remove the seeding in `UserDatabase.java` before any real deployment.

## ☁️ Deployment

- **Backend:** Docker image deployed on Render (`render.yaml`, `Dockerfile`).
- **Frontend (optional):** `vercel.json` serves `public/` and proxies `/api/*` to the Render backend.

## 👥 Team

| Name | GitHub |
| Gowshik|https://github.com/gowshik-cyber7 |
| Mahasri Murugesan | mahasri-mv |
| Mahitha S| https://github.com/Mahitha2501|
| Sruthica M S | sruthicasathish-stack|


*Each member contributes from their own GitHub account. See the commit history for individual contributions.*

## 📚 Course Context

Developed for **DSA Lab Evaluation 3**: data structure justification, implementation strategy, live demo, time-complexity analysis, and individual contribution.
