package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

/**
 * Per-channel peak and combined RMS meter for a stereo block.
 *
 * <p>It applies neither visual hold nor temporal smoothing: every call replaces values
 * with those from the received block. The UI controls display cadence.</p>
 */
public final class LevelMeter {
    /** Absolute left peak of the latest block in dBFS. */
    private double peakLeftDb = DecibelUtils.MINIMUM_DB;
    /** Absolute right peak of the latest block in dBFS. */
    private double peakRightDb = DecibelUtils.MINIMUM_DB;
    /** Root mean square of all L/R samples in the block, in dBFS. */
    private double rmsDb = DecibelUtils.MINIMUM_DB;

    /** Creates a meter whose initial state represents digital silence. */
    public LevelMeter() {
        // Fields already contain the measurement floor before the first block.
    }

    /**
     * Analyzes an initial region of interleaved stereo samples.
     *
     * @param samples normalized L/R buffer
     * @param frames number of valid pairs to include
     */
    public void measure(float[] samples, int frames) {
        double leftPeak = 0.0, rightPeak = 0.0, sumSquares = 0.0;
        for (int frame = 0, sample = 0; frame < frames; frame++, sample += 2) {
            double left = samples[sample], right = samples[sample + 1];
            leftPeak = Math.max(leftPeak, Math.abs(left));
            rightPeak = Math.max(rightPeak, Math.abs(right));
            sumSquares += left * left + right * right;
        }
        peakLeftDb = DecibelUtils.amplitudeToDb(leftPeak);
        peakRightDb = DecibelUtils.amplitudeToDb(rightPeak);
        rmsDb = DecibelUtils.amplitudeToDb(Math.sqrt(sumSquares / Math.max(1, frames * 2)));
    }

    /**
     * Returns the left-channel maximum.
     *
     * @return left peak of the latest block, in dBFS
     */
    public double peakLeftDb() { return peakLeftDb; }

    /**
     * Returns the right-channel maximum.
     *
     * @return right peak of the latest block, in dBFS
     */
    public double peakRightDb() { return peakRightDb; }

    /**
     * Summarizes the worst stereo peak.
     *
     * @return greater peak of the two channels, in dBFS
     */
    public double peakDb() { return Math.max(peakLeftDb, peakRightDb); }

    /**
     * Returns the combined average energy.
     *
     * @return combined RMS level of the latest block, in dBFS
     */
    public double rmsDb() { return rmsDb; }
}
