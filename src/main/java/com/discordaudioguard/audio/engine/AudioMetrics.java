package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.processing.LevelMeter;

/** Volatile metrics publication keeps UI snapshots from blocking the audio path. */
public final class AudioMetrics {
    private volatile double inputPeakLeftDb = -120, inputPeakRightDb = -120, inputRmsDb = -120;
    private volatile double outputPeakLeftDb = -120, outputPeakRightDb = -120, outputRmsDb = -120;
    private volatile double compressorReductionDb, limiterReductionDb;
    private volatile long blocksProcessed, writeErrors;
    private volatile double averageProcessingMicros, maximumProcessingMicros;
    private volatile double estimatedLatencyMillis, processingBudgetPercent;
    private volatile double adaptiveBufferMillis, playbackRateCorrectionPpm;

    public void publishLevels(LevelMeter input, LevelMeter output, double compressorReduction, double limiterReduction) {
        inputPeakLeftDb = input.peakLeftDb(); inputPeakRightDb = input.peakRightDb(); inputRmsDb = input.rmsDb();
        outputPeakLeftDb = output.peakLeftDb(); outputPeakRightDb = output.peakRightDb(); outputRmsDb = output.rmsDb();
        compressorReductionDb = compressorReduction; limiterReductionDb = limiterReduction;
    }

    public void blockCompleted(long processingNanos, double blockMillis) {
        long count = ++blocksProcessed;
        double micros = processingNanos / 1_000.0;
        averageProcessingMicros += (micros - averageProcessingMicros) / count;
        maximumProcessingMicros = Math.max(maximumProcessingMicros, micros);
        processingBudgetPercent = micros / (blockMillis * 1_000.0) * 100.0;
    }

    public void incrementWriteErrors() { writeErrors++; }
    public void setEstimatedLatencyMillis(double value) { estimatedLatencyMillis = value; }
    public void setSynchronization(double bufferMillis, double rateCorrectionPpm) {
        adaptiveBufferMillis = bufferMillis;
        playbackRateCorrectionPpm = rateCorrectionPpm;
    }

    public Snapshot snapshot() {
        return new Snapshot(inputPeakLeftDb, inputPeakRightDb, inputRmsDb, outputPeakLeftDb, outputPeakRightDb,
                outputRmsDb, compressorReductionDb, limiterReductionDb, compressorReductionDb + limiterReductionDb,
                blocksProcessed, writeErrors, averageProcessingMicros, maximumProcessingMicros,
                estimatedLatencyMillis, processingBudgetPercent, adaptiveBufferMillis, playbackRateCorrectionPpm);
    }

    public record Snapshot(double inputPeakLeftDb, double inputPeakRightDb, double inputRmsDb,
                           double outputPeakLeftDb, double outputPeakRightDb, double outputRmsDb,
                           double compressorReductionDb, double limiterReductionDb, double totalReductionDb,
                           long blocksProcessed, long writeErrors, double averageProcessingMicros,
                           double maximumProcessingMicros, double estimatedLatencyMillis,
                           double processingBudgetPercent, double adaptiveBufferMillis,
                           double playbackRateCorrectionPpm) {}
}
