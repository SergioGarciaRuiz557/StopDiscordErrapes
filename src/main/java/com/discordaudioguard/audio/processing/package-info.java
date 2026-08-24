/**
 * Digital dynamics processing: level detection, compression, lookahead limiting,
 * metering, and immutable parameter models.
 *
 * <p>Classes in this layer operate on normalized interleaved stereo PCM and avoid
 * allocations, locks, and I/O on the real-time path.</p>
 */
package com.discordaudioguard.audio.processing;
