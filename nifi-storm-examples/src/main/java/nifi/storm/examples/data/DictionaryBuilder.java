package nifi.storm.examples.data;

import org.apache.nifi.storm.NiFiDataPacket;
import org.apache.nifi.storm.NiFiDataPacketBuilder;
import org.apache.nifi.storm.StandardNiFiDataPacket;
import org.apache.storm.tuple.Tuple;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Produces a dictionary file for NiFi containing the log levels that should be
 * collected based on the rate of error and warn messages coming in.
 *
 * @author bbende
 */
public class DictionaryBuilder implements NiFiDataPacketBuilder, Serializable {

    public static final String ERROR = "ERROR";
    public static final String WARN = "WARN";
    public static final String INFO = "INFO";
    public static final String DEBUG = "DEBUG";

    public static final String ERROR_WARN_TOTAL_ATTR = "error.warn.total";
    public static final String ERROR_WARN_RATE_ATTR = "error.warn.rate";
    public static final String WINDOW_MILLIS_ATTR = "window.size.millis";

    private final int windowSizeMillis;
    private final double minRatePerSecond;

    /**
     * @param windowSizeMillis the windowSize in milliseconds that the LogLevels were computed over
     *
     * @param minRatePerSecond the rate of error and warn messages over the window that indicates not to
     *                              collect other levels, if the actual rate is less we can collect more
     */
    public DictionaryBuilder(final int windowSizeMillis, final double minRatePerSecond) {
        this.windowSizeMillis = windowSizeMillis;
        this.minRatePerSecond = minRatePerSecond;
    }

    @Override
    public NiFiDataPacket createNiFiDataPacket(Tuple tuple) {
        final LogLevels logLevels = (LogLevels) tuple.getValue(0);

        Map<String,Integer> counts = logLevels.getLevels();

        // get the total number of error and warn messages
        int totalWarnError = 0;
        if (counts.containsKey(ERROR)) {
            totalWarnError += counts.get(ERROR);
        }
        if (counts.containsKey(WARN)) {
            totalWarnError += counts.get(WARN);
        }

        // calculate the number of ERROR/WARN messages per second
        double windowSizeSeconds = windowSizeMillis / 1000;
        double actualRate = ((double)totalWarnError) / windowSizeSeconds;

        // always collect ERROR/WARN messages
        StringBuilder builder = new StringBuilder();
        builder.append(ERROR).append("\n");
        builder.append(WARN).append("\n");

        // only collect INFO and DEBUG if ERROR/WARN is less than minimum rate
        if (actualRate < minRatePerSecond) {
            builder.append(INFO).append("\n");
            builder.append(DEBUG).append("\n");
        }

        // pass the rate, total, and window size as attributes
        Map<String,String> attrs = new HashMap<>();
        attrs.put(ERROR_WARN_RATE_ATTR, String.valueOf(actualRate));
        attrs.put(ERROR_WARN_TOTAL_ATTR, String.valueOf(totalWarnError));
        attrs.put(WINDOW_MILLIS_ATTR, String.valueOf(windowSizeMillis));

        byte[] content = builder.toString().getBytes(StandardCharsets.UTF_8);
        return new StandardNiFiDataPacket(content, attrs);
    }

}
