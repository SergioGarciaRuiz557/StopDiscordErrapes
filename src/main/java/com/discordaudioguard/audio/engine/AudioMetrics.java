package com.discordaudioguard.audio.engine;

import com.discordaudioguard.audio.processing.LevelMeter;

/**
 * Non-blocking telemetry channel between audio threads and the UI.
 *
 * <p>Every value is {@code volatile}: the real-time path publishes scalars without
 * acquiring locks, and the UI periodically creates a {@link Snapshot}. A snapshot may
 * combine values from two adjacent blocks, which is acceptable for display and avoids
 * introducing expensive synchronization into audio.</p>
 */
public final class AudioMetrics {
    /** Published input levels in dBFS. */
    private volatile double inputPeakLeftDb = -120, inputPeakRightDb = -120, inputRmsDb = -120;
    /** Published output levels in dBFS. */
    private volatile double outputPeakLeftDb = -120, outputPeakRightDb = -120, outputRmsDb = -120;
    /** Instantaneous attenuation applied by both protective stages. */
    private volatile double compressorReductionDb, limiterReductionDb;
    /** Counters accumulated since engine creation. */
    private volatile long blocksProcessed, writeErrors;
    /** Incremental average and maximum block-processing cost, in microseconds. */
    private volatile double averageProcessingMicros, maximumProcessingMicros;
    /** Estimated latency and fraction of block time consumed by DSP. */
    private volatile double estimatedLatencyMillis, processingBudgetPercent;
    /** Adaptive-buffer occupancy and correction applied to the playback clock. */
    private volatile double adaptiveBufferMillis, playbackRateCorrectionPpm;

    /** Creates a collector with zero counters and initial levels equivalent to silence. */
    public AudioMetrics() {
        // Field initializers establish the initial snapshot.
    }

    /**
     * Copies levels and reductions calculated by the latest DSP execution.
     *
     * @param input meter for the block before processing
     * @param output meter for the block after processing
     * @param compressorReduction compressor reduction in positive dB
     * @param limiterReduction limiter reduction in positive dB
     */
    public void publishLevels(LevelMeter input, LevelMeter output, double compressorReduction, double limiterReduction) {
        inputPeakLeftDb = input.peakLeftDb(); inputPeakRightDb = input.peakRightDb(); inputRmsDb = input.rmsDb();
        outputPeakLeftDb = output.peakLeftDb(); outputPeakRightDb = output.peakRightDb(); outputRmsDb = output.rmsDb();
        compressorReductionDb = compressorReduction; limiterReductionDb = limiterReduction;
    }

    /**
     * Adds one block's cost to accumulated statistics and computes its time budget.
     *
     * @param processingNanos duration measured around the pipeline, in nanoseconds
     * @param blockMillis audio duration represented by the block
     */
    public void blockCompleted(long processingNanos, double blockMillis) {
        long count = ++blocksProcessed;
        double micros = processingNanos / 1_000.0;
        averageProcessingMicros += (micros - averageProcessingMicros) / count;
        maximumProcessingMicros = Math.max(maximumProcessingMicros, micros);
        processingBudgetPercent = micros / (blockMillis * 1_000.0) * 100.0;
    }

    /** Records an incomplete playback write. */
    public void incrementWriteErrors() { writeErrors++; }

    /**
     * Updates the visible total-latency estimate.
     *
     * @param value estimated total latency in milliseconds
     */
    public void setEstimatedLatencyMillis(double value) { estimatedLatencyMillis = value; }

    /**
     * Publishes the decoupling state between capture and playback clocks.
     *
     * @param bufferMillis currently accumulated audio in milliseconds
     * @param rateCorrectionPpm speed deviation from 1.0 in parts per million
     */
    public void setSynchronization(double bufferMillis, double rateCorrectionPpm) {
        adaptiveBufferMillis = bufferMillis;
        playbackRateCorrectionPpm = rateCorrectionPpm;
    }

    /**
     * Copies volatile telemetry into an object suitable for the UI.
     *
     * @return immutable snapshot of every published value
     */
    public Snapshot snapshot() {
        return new Snapshot(inputPeakLeftDb, inputPeakRightDb, inputRmsDb, outputPeakLeftDb, outputPeakRightDb,
                outputRmsDb, compressorReductionDb, limiterReductionDb, compressorReductionDb + limiterReductionDb,
                blocksProcessed, writeErrors, averageProcessingMicros, maximumProcessingMicros,
                estimatedLatencyMillis, processingBudgetPercent, adaptiveBufferMillis, playbackRateCorrectionPpm);
    }

    /**
     * Diagnostic snapshot ready for consumption outside the audio thread.
     * Levels and reductions use dB/dBFS; times use microseconds or milliseconds as
     * indicated by each name, budget uses percent, and synchronization uses ppm.
     *
     * @param inputPeakLeftDb left input peak in dBFS
     * @param inputPeakRightDb right input peak in dBFS
     * @param inputRmsDb combined input RMS level in dBFS
     * @param outputPeakLeftDb left output peak in dBFS
     * @param outputPeakRightDb right output peak in dBFS
     * @param outputRmsDb combined output RMS level in dBFS
     * @param compressorReductionDb compressor reduction in positive dB
     * @param limiterReductionDb limiter reduction in positive dB
     * @param totalReductionDb informational sum of both reductions in dB
     * @param blocksProcessed blocks completed since collector creation
     * @param writeErrors incomplete writes counted
     * @param averageProcessingMicros average processing cost in microseconds
     * @param maximumProcessingMicros greatest observed processing cost in microseconds
     * @param estimatedLatencyMillis estimated buffer and DSP latency in milliseconds
     * @param processingBudgetPercent percentage of the latest block consumed by processing
     * @param adaptiveBufferMillis adaptive-buffer occupancy in milliseconds
     * @param playbackRateCorrectionPpm speed correction in parts per million
     */
    public record Snapshot(double inputPeakLeftDb, double inputPeakRightDb, double inputRmsDb,
                           double outputPeakLeftDb, double outputPeakRightDb, double outputRmsDb,
                           double compressorReductionDb, double limiterReductionDb, double totalReductionDb,
                           long blocksProcessed, long writeErrors, double averageProcessingMicros,
                           double maximumProcessingMicros, double estimatedLatencyMillis,
                           double processingBudgetPercent, double adaptiveBufferMillis,
                           double playbackRateCorrectionPpm) {}
}
