package genomicindex;

import java.io.IOException;

/**
 * FASTQ Parser Automated Validation Test Suite.
 *
 * Contribution: Member 4 (sruthicasathish-stack)
 * Tests syntax error detection, line numbering, quality matching, and normalization.
 *
 * @author sruthicasathish-stack
 */
public class FastqValidationTest {

    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;

        // Test 1: Valid Reference FASTQ (3 reads, 16 bases)
        String refFastq = "@read0\nGATTACA\n+\nIIIIIII\n@read1\nCAGT\n+\nIIII\n@read2\nTTAGC\n+\nIIIII\n";
        try {
            FastqParser.ParseResult res = FastqParser.parse(refFastq);
            assertEq(3, res.getReadCount(), "Ref read count");
            assertEq(16, res.getTotalBaseCount(), "Ref base count");
            assertEq("GATTACA", res.getReads().get(0), "Read 0 sequence");
            assertEq("CAGT", res.getReads().get(1), "Read 1 sequence");
            assertEq("TTAGC", res.getReads().get(2), "Read 2 sequence");
            System.out.println("[PASS] Test 1: Valid Reference FASTQ parsed correctly.");
            passed++;
        } catch (Exception e) {
            System.err.println("[FAIL] Test 1: " + e.getMessage());
            failed++;
        }

        // Test 2: Blank line handling & accurate line numbering
        String fastqWithBlanks = "\n\n@r1\nACGT\n+\nIIII\n\n@r2\nTGCA\n+\nIIII\n";
        try {
            FastqParser.ParseResult res = FastqParser.parse(fastqWithBlanks);
            assertEq(2, res.getReadCount(), "Blank line read count");
            System.out.println("[PASS] Test 2: Blank lines handled properly.");
            passed++;
        } catch (Exception e) {
            System.err.println("[FAIL] Test 2: " + e.getMessage());
            failed++;
        }

        // Test 3: Missing '@' in header
        String badHeader = "read0\nGATTACA\n+\nIIIIIII\n";
        try {
            FastqParser.parse(badHeader);
            System.err.println("[FAIL] Test 3: Expected IOException for missing '@'");
            failed++;
        } catch (IOException e) {
            if (e.getMessage().contains("header must start with '@'")) {
                System.out.println("[PASS] Test 3: Missing '@' correctly rejected with line syntax error.");
                passed++;
            } else {
                System.err.println("[FAIL] Test 3: Unexpected error message: " + e.getMessage());
                failed++;
            }
        }

        // Test 4: Missing sequence line
        String missingSeq = "@read0\n";
        try {
            FastqParser.parse(missingSeq);
            System.err.println("[FAIL] Test 4: Expected IOException for missing sequence");
            failed++;
        } catch (IOException e) {
            System.out.println("[PASS] Test 4: Missing sequence correctly rejected: " + e.getMessage());
            passed++;
        }

        // Test 5: Missing '+' separator
        String badSeparator = "@read0\nGATTACA\n-\nIIIIIII\n";
        try {
            FastqParser.parse(badSeparator);
            System.err.println("[FAIL] Test 5: Expected IOException for bad separator");
            failed++;
        } catch (IOException e) {
            if (e.getMessage().contains("separator must start with '+'")) {
                System.out.println("[PASS] Test 5: Bad separator correctly rejected.");
                passed++;
            } else {
                System.err.println("[FAIL] Test 5: Unexpected error message: " + e.getMessage());
                failed++;
            }
        }

        // Test 6: Sequence and quality length mismatch
        String lengthMismatch = "@read0\nGATTACA\n+\nIIII\n";
        try {
            FastqParser.parse(lengthMismatch);
            System.err.println("[FAIL] Test 6: Expected length mismatch error");
            failed++;
        } catch (IOException e) {
            if (e.getMessage().contains("length mismatch")) {
                System.out.println("[PASS] Test 6: Length mismatch correctly detected.");
                passed++;
            } else {
                System.err.println("[FAIL] Test 6: Unexpected error message: " + e.getMessage());
                failed++;
            }
        }

        // Test 7: Invalid nucleotide character
        String badChar = "@read0\nGATZACA\n+\nIIIIIII\n";
        try {
            FastqParser.parse(badChar);
            System.err.println("[FAIL] Test 7: Expected invalid nucleotide error");
            failed++;
        } catch (IOException e) {
            if (e.getMessage().contains("Invalid nucleotide 'Z'")) {
                System.out.println("[PASS] Test 7: Invalid nucleotide character rejected.");
                passed++;
            } else {
                System.err.println("[FAIL] Test 7: Unexpected error message: " + e.getMessage());
                failed++;
            }
        }

        // Test 8: Invalid quality character (ASCII < 33)
        String badQuality = "@read0\nGATTACA\n+\nIIII\u001FII\n"; // ASCII 31
        try {
            FastqParser.parse(badQuality);
            System.err.println("[FAIL] Test 8: Expected invalid Phred quality character error");
            failed++;
        } catch (IOException e) {
            if (e.getMessage().contains("Quality character ASCII code 31 out of valid Sanger/Illumina range")) {
                System.out.println("[PASS] Test 8: Invalid Phred ASCII character rejected.");
                passed++;
            } else {
                System.err.println("[FAIL] Test 8: Unexpected error message: " + e.getMessage());
                failed++;
            }
        }

        // Test 9: Independent Reference Search vs QueryEngine Search on Ref Dataset
        try {
            IndexManager im = new IndexManager();
            im.loadFromFastqContent("ref_test.fastq", refFastq);
            QueryEngine qe = im.getQueryEngine();

            // Check pattern 'AGC'
            QueryEngine.FindResult agcRes = qe.find("AGC");
            assertEq(1, agcRes.matches.size(), "AGC match count");
            Match m = agcRes.matches.get(0);
            assertEq(2, m.getReadIndex(), "AGC read index");
            assertEq(2, m.getOffsetInRead(), "AGC offset in read");

            // Check pattern 'ATT'
            QueryEngine.FindResult attRes = qe.find("ATT");
            assertEq(1, attRes.matches.size(), "ATT match count");
            assertEq(0, attRes.matches.get(0).getReadIndex(), "ATT read index");
            assertEq(1, attRes.matches.get(0).getOffsetInRead(), "ATT offset in read");

            // Check composition
            java.util.Map<String, Object> comp = im.getBaseComposition(null, null, null);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Integer> counts = (java.util.Map<String, Integer>) comp.get("counts");
            assertEq(5, counts.get("A"), "Count of A");
            assertEq(3, counts.get("C"), "Count of C");
            assertEq(3, counts.get("G"), "Count of G");
            assertEq(5, counts.get("T"), "Count of T");

            double gc = (Double) comp.get("gcPercentage");
            if (Math.abs(gc - 37.5) > 0.01) {
                throw new AssertionError("Expected GC 37.5%, got " + gc);
            }

            System.out.println("[PASS] Test 9: Reference search & metrics match mathematical ground truth.");
            passed++;
        } catch (Exception e) {
            System.err.println("[FAIL] Test 9: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("=================================================");
        System.out.println("  FASTQ & INDEXING VALIDATION: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertEq(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " failed: expected " + expected + ", got " + actual);
        }
    }

    private static void assertEq(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " failed: expected \"" + expected + "\", got \"" + actual + "\"");
        }
    }
}
