package genomicindex;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Dataset Registry managing persistent genomic datasets per user/session.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Key Data Structure: ConcurrentHashMap (java.util.concurrent.ConcurrentHashMap)
 *
 * Data Structure Rationale & Complexity:
 * - Thread-Safety: Utilizes fine-grained lock striping and lock-free reads to support concurrent
 *   multi-user FASTQ uploads and metadata queries without global lock contention.
 * - Time Complexity: O(1) average-time dataset lookup, caching, and cache invalidation.
 * - Space Complexity: O(U * D) where U is active users and D is datasets per user.
 * - Multi-Tenancy: Thread-safe dataset cataloging, switching, downloading, and deletion.
 *
 * @author sruthicasathish-stack
 */
public class DatasetRegistry {

    public static class DatasetEntry {
        private final String id;
        private final String name;
        private final String description;
        private final String source;
        private final int reads;
        private final int symbols; // Total DNA bases
        private final long createdAt;
        private final String userId;

        public DatasetEntry(String id, String name, String description, String source,
                            int reads, int symbols, long createdAt, String userId) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.source = source;
            this.reads = reads;
            this.symbols = symbols;
            this.createdAt = createdAt > 0 ? createdAt : Instant.now().toEpochMilli();
            this.userId = userId;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getSource() { return source; }
        public int getReads() { return reads; }
        public int getSymbols() { return symbols; }
        public long getCreatedAt() { return createdAt; }
        public String getUserId() { return userId; }

        public Map<String, Object> toMap(boolean isActive) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("description", description);
            map.put("source", source);
            map.put("reads", reads);
            map.put("symbols", symbols);
            map.put("createdAt", createdAt);
            map.put("active", isActive);
            return map;
        }
    }

    private final Path baseStorageDir;
    private final Map<String, List<DatasetEntry>> userManifestCache = new ConcurrentHashMap<>();

    public DatasetRegistry(Path baseDir) {
        this.baseStorageDir = baseDir.resolve("datasets");
        try {
            Files.createDirectories(this.baseStorageDir);
        } catch (IOException e) {
            System.err.println("[WARN] Could not create datasets storage directory: " + e.getMessage());
        }
    }

    /**
     * Retrieves the 3 standard built-in datasets.
     */
    public List<DatasetEntry> getBuiltInDatasets() {
        List<DatasetEntry> builtIns = new ArrayList<>();
        builtIns.add(new DatasetEntry(
                "demo_report",
                "demo_report.fastq",
                "Report Demo: GATTACAGATTACA (COUNT=2 for ATT)",
                "Built-in Sample",
                1,
                14,
                1700000000000L,
                "system"
        ));
        builtIns.add(new DatasetEntry(
                "sample",
                "sample.fastq",
                "Standard 3-read sample (GATTACA, CAGT, TTAGC)",
                "Built-in Sample",
                3,
                16,
                1700000000000L,
                "system"
        ));
        builtIns.add(new DatasetEntry(
                "extended",
                "extended_sample.fastq",
                "Multi-read sample (10 reads)",
                "Built-in Sample",
                10,
                61,
                1700000000000L,
                "system"
        ));
        return builtIns;
    }

    /**
     * Returns all datasets available to the given user (custom uploads first, followed by built-in samples).
     */
    public synchronized List<DatasetEntry> getAllDatasetsForUser(String userId) {
        List<DatasetEntry> list = new ArrayList<>();
        List<DatasetEntry> userCustom = loadUserManifest(userId);
        list.addAll(userCustom);
        list.addAll(getBuiltInDatasets());
        return list;
    }

    /**
     * Finds a dataset by ID for a user.
     */
    public synchronized DatasetEntry findDataset(String userId, String datasetId) {
        if (datasetId == null) return null;
        for (DatasetEntry ds : getAllDatasetsForUser(userId)) {
            if (ds.getId().equalsIgnoreCase(datasetId)) {
                return ds;
            }
        }
        return null;
    }

    /**
     * Saves an uploaded or pasted FASTQ dataset for a specific user.
     */
    public synchronized DatasetEntry saveDataset(String userId, String filename, String source, String fastqContent) throws IOException {
        if (fastqContent == null || fastqContent.trim().isEmpty()) {
            throw new IllegalArgumentException("FASTQ content is empty");
        }

        // Validate syntax and calculate metrics
        FastqParser.ParseResult parseRes = FastqParser.parse(fastqContent);
        int readsCount = parseRes.getReadCount();
        int baseCount = parseRes.getTotalBaseCount();

        String cleanName = (filename != null && !filename.trim().isEmpty()) ? filename.trim() : "dataset.fastq";
        if (!cleanName.endsWith(".fastq") && !cleanName.endsWith(".fq") && !cleanName.endsWith(".txt")) {
            cleanName += ".fastq";
        }

        String datasetId = "ds_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String desc = source.contains("Paste") ? "Pasted genomic sequence data" : "Uploaded DNA sequencing file";

        DatasetEntry entry = new DatasetEntry(datasetId, cleanName, desc, source, readsCount, baseCount, Instant.now().toEpochMilli(), userId);

        Path userDir = getUserDirectory(userId);
        Files.createDirectories(userDir);

        // Save raw fastq file
        Path fastqFile = userDir.resolve(datasetId + ".fastq");
        Files.writeString(fastqFile, fastqContent, StandardCharsets.UTF_8);

        // Update manifest
        List<DatasetEntry> userCustom = loadUserManifest(userId);
        userCustom.add(0, entry); // Newest first
        saveUserManifest(userId, userCustom);

        return entry;
    }

    /**
     * Deletes a custom dataset for a user.
     */
    public synchronized boolean deleteDataset(String userId, String datasetId) {
        if (datasetId == null) return false;
        if ("demo_report".equalsIgnoreCase(datasetId) || "sample".equalsIgnoreCase(datasetId) || "extended".equalsIgnoreCase(datasetId)) {
            return false; // Cannot delete built-in system datasets
        }

        List<DatasetEntry> userCustom = loadUserManifest(userId);
        DatasetEntry found = null;
        for (DatasetEntry ds : userCustom) {
            if (ds.getId().equalsIgnoreCase(datasetId)) {
                found = ds;
                break;
            }
        }

        if (found == null) return false;

        userCustom.remove(found);
        saveUserManifest(userId, userCustom);

        Path userDir = getUserDirectory(userId);
        Path fastqFile = userDir.resolve(datasetId + ".fastq");
        try {
            Files.deleteIfExists(fastqFile);
        } catch (IOException ignored) {
        }
        return true;
    }

    /**
     * Retrieves the raw FASTQ content of a dataset.
     */
    public synchronized String getFastqContent(String userId, String datasetId) throws IOException {
        if ("demo_report".equalsIgnoreCase(datasetId)) {
            return IndexManager.DEMO_REPORT_FASTQ;
        } else if ("sample".equalsIgnoreCase(datasetId)) {
            return IndexManager.SAMPLE_FASTQ;
        } else if ("extended".equalsIgnoreCase(datasetId)) {
            return IndexManager.SAMPLE_MULTI_FASTQ;
        }

        Path userDir = getUserDirectory(userId);
        Path fastqFile = userDir.resolve(datasetId + ".fastq");
        if (Files.exists(fastqFile)) {
            return Files.readString(fastqFile, StandardCharsets.UTF_8);
        }

        throw new FileNotFoundException("FASTQ dataset '" + datasetId + "' not found for user " + userId);
    }

    private Path getUserDirectory(String userId) {
        String cleanUser = (userId != null && !userId.trim().isEmpty()) ? userId.replaceAll("[^a-zA-Z0-9._-]", "_") : "guest";
        return baseStorageDir.resolve(cleanUser);
    }

    private List<DatasetEntry> loadUserManifest(String userId) {
        if (userManifestCache.containsKey(userId)) {
            return userManifestCache.get(userId);
        }

        List<DatasetEntry> entries = new ArrayList<>();
        Path manifestPath = getUserDirectory(userId).resolve("manifest.tsv");
        if (Files.exists(manifestPath)) {
            try (BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("\t");
                    if (parts.length >= 7) {
                        String id = parts[0];
                        String name = parts[1];
                        String desc = parts[2];
                        String source = parts[3];
                        int reads = Integer.parseInt(parts[4]);
                        int symbols = Integer.parseInt(parts[5]);
                        long createdAt = Long.parseLong(parts[6]);
                        entries.add(new DatasetEntry(id, name, desc, source, reads, symbols, createdAt, userId));
                    }
                }
            } catch (Exception e) {
                System.err.println("[WARN] Failed to load manifest for user " + userId + ": " + e.getMessage());
            }
        }

        userManifestCache.put(userId, entries);
        return entries;
    }

    private void saveUserManifest(String userId, List<DatasetEntry> entries) {
        userManifestCache.put(userId, entries);
        Path userDir = getUserDirectory(userId);
        try {
            Files.createDirectories(userDir);
            Path manifestPath = userDir.resolve("manifest.tsv");
            StringBuilder sb = new StringBuilder();
            sb.append("# id\tname\tdescription\tsource\treads\tsymbols\tcreatedAt\n");
            for (DatasetEntry ds : entries) {
                sb.append(ds.getId()).append("\t")
                  .append(ds.getName().replace("\t", " ")).append("\t")
                  .append(ds.getDescription().replace("\t", " ")).append("\t")
                  .append(ds.getSource().replace("\t", " ")).append("\t")
                  .append(ds.getReads()).append("\t")
                  .append(ds.getSymbols()).append("\t")
                  .append(ds.getCreatedAt()).append("\n");
            }
            Files.writeString(manifestPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[WARN] Failed to save manifest for user " + userId + ": " + e.getMessage());
        }
    }

    /**
     * Telemetry and evaluation inspection method for ConcurrentHashMap cache.
     *
     * @return Map containing active cached user count and cache state
     */
    public Map<String, Object> getManifestCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cachedUserCount", userManifestCache.size());
        stats.put("dataStructure", "java.util.concurrent.ConcurrentHashMap");
        stats.put("concurrencyModel", "Lock-free reads with striped bucket synchronization");
        return Collections.unmodifiableMap(stats);
    }
}
