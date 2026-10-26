package genomicindex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Robust parser for FASTQ genomic files.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Module: FASTQ Format Parser, Quality String Validator & Sequence Normalizer
 *
 * A valid FASTQ record consists of 4 lines:
 * Line 1: Header beginning with '@'
 * Line 2: Nucleotide sequence (A, C, G, T, N)
 * Line 3: Separator line beginning with '+'
 * Line 4: Quality string (length must match sequence line)
 *
 * Validation policy:
 * - Verifies line prefixes ('@' and '+').
 * - Verifies sequence and quality string lengths match.
 * - Converts lowercase nucleotides (a, c, g, t, n) to uppercase.
 * - Validates nucleotide characters against {A, C, G, N, T}.
 * - Rejects empty files and incomplete records.
 * - Strips headers and quality strings, returning only pure DNA reads.
 *
 * @author sruthicasathish-stack
 */
public class FastqParser {

    public static class ParseResult {
        private final List<String> reads;
        private final List<String> headers;
        private final List<String> qualities;
        private final int totalBaseCount;

        public ParseResult(List<String> reads, List<String> headers) {
            this(reads, headers, new ArrayList<>());
        }

        public ParseResult(List<String> reads, List<String> headers, List<String> qualities) {
            this.reads = Collections.unmodifiableList(reads);
            this.headers = Collections.unmodifiableList(headers);
            this.qualities = Collections.unmodifiableList(qualities);
            int bases = 0;
            for (String r : reads) {
                bases += r.length();
            }
            this.totalBaseCount = bases;
        }

        public List<String> getReads() {
            return reads;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<String> getQualities() {
            return qualities;
        }

        public int getReadCount() {
            return reads.size();
        }

        public int getTotalBaseCount() {
            return totalBaseCount;
        }

        /**
         * Joins all reads with the '#' separator, appending a trailing '#' after each read.
         * Example: ["GATTACA", "CAGT", "TTAGC"] -> "GATTACA#CAGT#TTAGC#"
         */
        public String toConcatenatedSequence() {
            StringBuilder sb = new StringBuilder();
            for (String read : reads) {
                sb.append(read).append(Alphabet.SEPARATOR);
            }
            return sb.toString();
        }
    }

    /**
     * Parses FASTQ content from a raw string.
     */
    public static ParseResult parse(String fastqContent) throws IOException {
        if (fastqContent == null || fastqContent.trim().isEmpty()) {
            throw new IllegalArgumentException("FASTQ content is empty or contains only whitespace");
        }
        return parse(new BufferedReader(new StringReader(fastqContent)));
    }

    /**
     * Parses FASTQ content from an InputStream.
     */
    public static ParseResult parse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        return parse(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
    }

    /**
     * Internal line-by-line parser and validator.
     */
    public static ParseResult parse(BufferedReader reader) throws IOException {
        List<String> reads = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        List<String> qualities = new ArrayList<>();

        String line;
        int lineNumber = 0;
        int recordNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty()) {
                continue; // ignore blank spacing lines between records
            }

            // Line 1: Header
            if (!line.startsWith("@")) {
                throw new IOException("FASTQ syntax error at line " + lineNumber + ": Record " + recordNumber
                        + " header must start with '@', found: \"" + line + "\"");
            }
            String header = line.substring(1).trim();

            // Line 2: Sequence
            String seqLine = reader.readLine();
            lineNumber++;
            if (seqLine == null) {
                throw new IOException("FASTQ syntax error: Incomplete record " + recordNumber
                        + " (missing sequence line at line " + lineNumber + ")");
            }
            seqLine = seqLine.trim().toUpperCase();
            if (seqLine.isEmpty()) {
                throw new IOException("FASTQ validation error at line " + lineNumber + ": Sequence for record "
                        + recordNumber + " cannot be empty");
            }

            // Validate sequence characters
            for (int i = 0; i < seqLine.length(); i++) {
                char ch = seqLine.charAt(i);
                if (!Alphabet.isValidNucleotide(ch)) {
                    throw new IOException("FASTQ character validation error at line " + lineNumber
                            + ", character " + (i + 1) + ": Invalid nucleotide '" + ch
                            + "'. Supported nucleotides are {A, C, G, N, T}");
                }
            }

            // Line 3: Separator line '+'
            String plusLine = reader.readLine();
            lineNumber++;
            if (plusLine == null) {
                throw new IOException("FASTQ syntax error: Incomplete record " + recordNumber
                        + " (missing '+' separator at line " + lineNumber + ")");
            }
            plusLine = plusLine.trim();
            if (!plusLine.startsWith("+")) {
                throw new IOException("FASTQ syntax error at line " + lineNumber + ": Record " + recordNumber
                        + " separator must start with '+', found: \"" + plusLine + "\"");
            }

            // Line 4: Quality string
            String qualLine = reader.readLine();
            lineNumber++;
            if (qualLine == null) {
                throw new IOException("FASTQ syntax error: Incomplete record " + recordNumber
                        + " (missing quality score line at line " + lineNumber + ")");
            }
            qualLine = qualLine.trim();
            if (qualLine.length() != seqLine.length()) {
                throw new IOException("FASTQ length mismatch at line " + lineNumber + ": Record " + recordNumber
                        + " sequence length (" + seqLine.length() + ") != quality score length (" + qualLine.length() + ")");
            }

            // Validate Phred ASCII quality characters in range [33, 126]
            for (int i = 0; i < qualLine.length(); i++) {
                char q = qualLine.charAt(i);
                if (q < 33 || q > 126) {
                    throw new IOException("FASTQ quality score error at line " + lineNumber
                            + ", character " + (i + 1) + ": Quality character ASCII code " + (int)q
                            + " out of valid Sanger/Illumina range [33, 126]");
                }
            }

            headers.add(header);
            reads.add(seqLine);
            qualities.add(qualLine);
            recordNumber++;
        }

        if (reads.isEmpty()) {
            throw new IOException("FASTQ file contained no valid records");
        }

        return new ParseResult(reads, headers, qualities);
    }
}
