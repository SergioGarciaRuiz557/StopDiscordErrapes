package com.discordaudioguard.audio.processing;

import com.discordaudioguard.util.DecibelUtils;

public final class LevelMeter {
    private double peakLeftDb = DecibelUtils.MINIMUM_DB;
    private double peakRightDb = DecibelUtils.MINIMUM_DB;
    private double rmsDb = DecibelUtils.MINIMUM_DB;

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

    public double peakLeftDb() { return peakLeftDb; }
    public double peakRightDb() { return peakRightDb; }
    public double peakDb() { return Math.max(peakLeftDb, peakRightDb); }
    public double rmsDb() { return rmsDb; }
}
