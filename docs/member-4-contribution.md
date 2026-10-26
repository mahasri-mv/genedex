# Member 4 Individual Contribution & Architecture Specification

**Student / Contributor:** `sruthicasathish-stack` (Member 4)  
**Project Repository:** [GeneDex — High-Performance Succinct Genomic Indexing Platform](https://github.com/mahasri-mv/genedex)  
**Lab Component:** DSA Lab Evaluation 3 — Component 5 (Individual Contribution & GitHub Proof - 2 Marks)

---

## 1. Summary Checklist of Responsibilities

According to the group's approved allocation matrix:

| Category | Assigned Components & Artifacts | Primary Technologies & Algorithms |
| :--- | :--- | :--- |
| **Backend Files** | `FastqParser.java`<br>`DatasetRegistry.java`<br>`WebServer.java`<br>`auth/*` (`User.java`, `UserDatabase.java`, `SessionManager.java`, `PasswordHasher.java`) | Java 17+, `com.sun.net.httpserver`, NIST PBKDF2-HMAC-SHA256, Multi-tenant File Storage |
| **Frontend Pages** | `upload.js` (Dataset Upload & Management)<br>`reports.js` (Genomic Analysis Reports)<br>`api.js` (Centralized Client API Client & State) | ES6 JavaScript, HTML5 Fetch, File API, Dynamic Reactive UI |
| **Key Data Structure** | **Concurrent Hash Map** (`java.util.concurrent.ConcurrentHashMap`) | Lock-free volatile reads, bucket-level CAS synchronization, striped multi-user caching |
| **Automated Tests** | `FastqValidationTest.java`<br>`AuthTest.java`<br>`Member4EvaluationTest.java` | Automated JUnit-style standalone test harnesses, multi-threaded concurrency stress testing |

---

## 2. Key Data Structure: Concurrent Hash Map

### 2.1 Why `ConcurrentHashMap` Was Chosen
In a multi-user genomic platform where multiple bioinformaticians, clinical researchers, and lab technicians upload and analyze gigabyte-scale genomic sequence files simultaneously, concurrency bottlenecks must be eliminated:

1. **Standard `HashMap` (Unsynchronized)**:
   - Not thread-safe. Concurrent modifications lead to race conditions, silent data corruption, or infinite loops during bucket rehashing.
2. **`Collections.synchronizedMap()` / `Hashtable` (Coarse-Grained Locking)**:
   - Uses a single mutual-exclusion lock across the entire table.
   - Any read operation blocks all other reads and writes, creating massive latency spikes under concurrent loads.
3. **`ConcurrentHashMap` (Fine-Grained Striped Concurrency - Chosen)**:
   - **Lock-Free Reads**: Read operations (`get`, `containsKey`) are volatile and completely non-blocking, allowing unlimited simultaneous reads.
   - **Bucket-Level CAS Synchronization**: Write operations (`put`, `computeIfAbsent`, `remove`) lock only the specific hash bin / node (via Compare-And-Swap instructions), leaving all other buckets freely accessible.

### 2.2 Complexity Analysis

| Operation | Time Complexity (Average) | Time Complexity (Worst-Case) | Theoretical Space | Code Implementation Mechanism |
| :--- | :---: | :---: | :---: | :--- |
| **Dataset Lookup** (`get`) | $O(1)$ | $O(\log k)$ | $O(1)$ | Non-blocking volatile read with red-black tree bin fallback |
| **Dataset Registration** (`put`) | $O(1)$ | $O(\log k)$ | $O(1)$ | Bucket-level CAS synchronization without global lock |
| **Cache Invalidation** (`remove`) | $O(1)$ | $O(\log k)$ | $O(1)$ | Isolated node unlinking |
| **Session Retrieval** | $O(1)$ | $O(\log k)$ | $O(1)$ | Sub-millisecond session lookup for incoming REST calls |

*(Where $k$ is the collision count in a single hash bucket, bounded by red-black tree restructuring at $k \ge 8$)*.

### 2.3 Concrete Usage Across Member 4 Modules

1. **`DatasetRegistry.java`**:
   - `userManifestCache`: Caches user dataset manifests in-memory to eliminate disk I/O latency when listing or switching datasets.
2. **`SessionManager.java`**:
   - `activeSessions`: Tracks active authenticated sessions and per-user Wavelet Tree indexes across concurrent HTTP requests.
   - `rateLimitMap`: Tracks brute-force login attempts per IP/Email with atomic count increments.
3. **`UserDatabase.java`**:
   - `usersByEmail` & `usersById`: Fast dual-key in-memory user index ensuring instant authentication lookups.

---

## 3. Backend Module Specifications

### 3.1 FASTQ File Parser (`FastqParser.java`)
Implements strict 4-line syntax validation for standard sequencing data:
- **Line 1 (Header)**: Must begin with `@` character; extracts read identifier.
- **Line 2 (Sequence)**: Validates nucleotides against $\{A, C, G, T, N\}$; normalizes lowercase $\{a, c, g, t, n\} \to \{A, C, G, T, N\}$.
- **Line 3 (Separator)**: Must begin with `+` character.
- **Line 4 (Quality)**: Verifies exact character length match: $|\text{sequence}| = |\text{quality}|$.
- Strips non-sequence lines and returns clean DNA sequences for Wavelet Tree indexing.

### 3.2 Dataset Registry & Isolation (`DatasetRegistry.java`)
- **Directory Layout**: Each user receives an isolated workspace under `data/datasets/<userId>/`.
- **Manifest Tracking**: Maintains `manifest.tsv` per user containing dataset metadata (ID, name, description, read count, base count, timestamp).
- **Built-in Datasets**: Pre-registers 3 benchmark datasets (`demo_report.fastq`, `sample.fastq`, `extended_sample.fastq`).

### 3.3 Authentication & Cryptographic Security (`auth/*`)
- **Password Hashing (`PasswordHasher.java`)**:
  - Implements **PBKDF2WithHmacSHA256** compliant with NIST SP 800-132.
  - 65,536 iterations with a 128-bit cryptographically secure salt (`SecureRandom`).
  - Constant-time hash verification via `MessageDigest.isEqual` to prevent timing attacks.
- **Session Management (`SessionManager.java`)**:
  - Issues cryptographically random session tokens and CSRF tokens.
  - Enforces 30-minute idle session timeout and 7-day "Remember Me" sessions.
  - Account lockout after 5 consecutive failed attempts (15-minute cooldown).
- **User Database (`UserDatabase.java`)**:
  - Atomic JSON persistence via temporary file write and `ATOMIC_MOVE` to prevent file corruption during sudden shutdown.

### 3.4 REST API Server (`WebServer.java`)
Implements lightweight, non-blocking HTTP REST endpoints using Java's built-in `HttpServer`:
- `POST /api/upload`: Handles FASTQ file upload, validation, and dataset creation.
- `GET /api/datasets`: Returns catalog of user and built-in datasets.
- `POST /api/datasets/active`: Switches active dataset and initializes private Wavelet Tree index.
- `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/logout`: User authentication with HttpOnly cookies.
- `GET /api/auth/me`: Validates session and returns user profile and permissions.
- `POST /api/reports/summary`, `GET /api/reports/export`: Generates analytical sequencing reports and exports to PDF, CSV, or Markdown.

---

## 4. Frontend Module Specifications

### 4.1 Dataset Upload & Management Page (`upload.js`)
- Drag-and-drop file uploader with client-side FASTQ pre-flight validation.
- Direct sequence pasting tab for quick testing of short genomic snippets.
- Real-time display of total reads, total base pairs, and average read length.
- Dataset selector card allowing researchers to switch active datasets with a single click.

### 4.2 Genomic Analysis Reports Page (`reports.js`)
- Interactive report configurator allowing researchers to select report sections:
  - Executive Dataset Summary
  - Nucleotide Composition ($A, C, G, T$ counts and percentages)
  - GC-Content Analysis with genomic classification (Low, Balanced, High GC)
  - Pattern Search findings with global coordinate mapping
  - Read-by-Read statistical breakdown
- Publication-ready modal preview with export buttons for PDF print, CSV data export, and Markdown report generation.

### 4.3 Centralized API Client Service (`api.js`)
- Dual-mode architecture:
  1. **Live Backend Mode**: Communicates with the Java backend via `/api/*` endpoints.
  2. **Static / Serverless Fallback Mode**: When deployed to static hosts (Vercel, GitHub Pages) or running offline, seamlessly routes to an in-browser bioinformatics engine so all upload, search, analytics, and reporting capabilities remain fully interactive.
- Automatic CSRF token injection and session state synchronization.

---

## 5. Automated Verification & Test Harness

Member 4 includes 3 dedicated test harnesses:

1. **`FastqValidationTest.java`**:
   - Tests reference 3-read FASTQ parsing
   - Blank line handling and accurate line numbering
   - Missing `@` header rejection
   - Sequence/quality length mismatch rejection
   - Lowercase-to-uppercase nucleotide normalization
2. **`AuthTest.java`**:
   - PBKDF2 hash generation and salt uniqueness
   - User registration and role assignments
   - Brute-force rate limiting and account lockout
   - Per-user dataset isolation
3. **`Member4EvaluationTest.java`**:
   - Multi-threaded stress test executing 200 concurrent dataset operations across 8 worker threads on `ConcurrentHashMap` with zero race conditions.

---

## 6. Faculty Viva / Interview Quick Reference Guide

**Q1: What was your specific contribution to the GeneDex project?**  
> *"As Member 4, I was responsible for the data intake and management pipeline, user authentication subsystem, and REST API services. Specifically, I implemented `FastqParser.java`, `DatasetRegistry.java`, `WebServer.java`, and the `auth` module, along with the `upload.js`, `reports.js`, and `api.js` frontend pages. My key data structure was `ConcurrentHashMap`."*

**Q2: Why did you choose `ConcurrentHashMap` instead of a regular `HashMap` or `Hashtable`?**  
> *"In a multi-user genomic platform, multiple users upload datasets, query session states, and check dataset catalogs simultaneously. A standard `HashMap` is not thread-safe and crashes under concurrent writes. A `Hashtable` or `synchronizedMap` locks the entire table for every operation, causing massive latency. `ConcurrentHashMap` uses lock-free volatile reads and bucket-level Compare-And-Swap (CAS) locking, allowing unlimited concurrent reads and non-blocking parallel writes with $O(1)$ average time complexity."*

**Q3: How does your FASTQ parser validate incoming genomic files?**  
> *"FASTQ is a 4-line format. The parser verifies that Line 1 starts with '@', Line 2 contains only valid nucleotides $\{A, C, G, T, N\}$, Line 3 starts with '+', and Line 4 has a quality string whose length exactly matches the sequence length. It also normalizes lowercase nucleotides to uppercase and strips non-sequence data to prepare clean reads for the Wavelet Tree."*

**Q4: How do you guarantee dataset privacy and isolation between users?**  
> *"Every user has an isolated workspace under `data/datasets/<userId>/` managed by `DatasetRegistry`. When a user logs in, `SessionManager` binds their session to their private dataset catalog and Wavelet Tree instance, ensuring researchers cannot view or overwrite datasets belonging to other users."*
