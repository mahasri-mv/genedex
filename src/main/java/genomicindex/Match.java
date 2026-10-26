package genomicindex;

/**
 * Represents a single DNA pattern match location.
 */
public class Match {

    private final int readIndex;      // 0-based read index
    private final int offsetInRead;   // 0-based offset within the read
    private final int globalPosition; // 0-based global index in concatenated S
    private final String pattern;     // matched pattern text

    public Match(int readIndex, int offsetInRead, int globalPosition, String pattern) {
        this.readIndex = readIndex;
        this.offsetInRead = offsetInRead;
        this.globalPosition = globalPosition;
        this.pattern = pattern;
    }

    public int getReadIndex() {
        return readIndex;
    }

    public int getOffsetInRead() {
        return offsetInRead;
    }

    public int getGlobalPosition() {
        return globalPosition;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return "Match[read=" + readIndex + ", offset=" + offsetInRead + ", global=" + globalPosition + ", pattern=\"" + pattern + "\"]";
    }
}
