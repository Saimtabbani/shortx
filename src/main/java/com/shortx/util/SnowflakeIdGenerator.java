package com.shortx.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Generates 64-bit, roughly time-sortable, globally-unique IDs with zero
 * coordination between application instances — used as the primary key
 * for the two highest-write-volume tables (short_urls, click_events)
 * instead of a Postgres sequence. See the rationale block in
 * V5__create_short_urls_table.sql for why a DB sequence or random UUID
 * was rejected for those tables.
 *
 * <p>Bit layout (Twitter Snowflake, adapted):
 * <pre>
 *   1 bit  - unused (kept 0, so IDs are always positive as a signed long)
 *  41 bits - milliseconds since {@link #EPOCH}          (~69 years of range)
 *  10 bits - worker ID                                   (1024 concurrent nodes)
 *  12 bits - per-millisecond sequence                    (4096 IDs/ms/node)
 * </pre>
 *
 * <p><b>Concurrency:</b> {@link #nextId()} is {@code synchronized}. At this
 * table's realistic throughput (redirects/clicks, not a HFT order book)
 * that's the right trade-off: a synchronized block over a few nanoseconds
 * of bit arithmetic is simpler to reason about and test than a lock-free
 * CAS loop, and won't be the bottleneck before the database itself is. If
 * profiling ever shows this method under real contention, the standard
 * next step is an AtomicLong-based CAS loop — noted here rather than
 * pre-optimized away.
 *
 * <p><b>Worker ID:</b> resolved once at startup, either from the
 * {@code shortx.snowflake.worker-id} property (set explicitly per
 * instance in an orchestrator, e.g. from a StatefulSet pod ordinal) or,
 * if unset, derived deterministically from the instance hostname. Two
 * instances sharing a worker ID would risk colliding IDs within the same
 * millisecond, so explicit assignment is preferred in production; the
 * hostname fallback exists so local/dev/single-instance runs work with
 * zero configuration.
 */
@Component
public class SnowflakeIdGenerator {

    /** Custom epoch: 2025-01-01T00:00:00Z, in milliseconds since Unix epoch. */
    private static final long EPOCH = 1735689600000L;

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;   // 1023
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;     // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(@Value("${shortx.snowflake.worker-id:-1}") long configuredWorkerId) {
        this.workerId = resolveWorkerId(configuredWorkerId);
    }

    /**
     * Returns the next unique ID. Safe to call concurrently from multiple
     * threads within this JVM instance.
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        if (timestamp < lastTimestamp) {
            // System clock moved backwards (NTP adjustment, VM pause/resume).
            // Rather than silently risking a duplicate/out-of-order ID, fail
            // loudly — this should be vanishingly rare and callers (the
            // service layer) should treat it as a retryable infrastructure
            // error, not swallow it.
            throw new IllegalStateException(
                "Clock moved backwards. Refusing to generate ID for " + (lastTimestamp - timestamp) + "ms.");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Exhausted this millisecond's 4096 IDs on this worker;
                // spin until the clock ticks forward rather than reuse it.
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long resolveWorkerId(long configuredWorkerId) {
        if (configuredWorkerId >= 0) {
            if (configuredWorkerId > MAX_WORKER_ID) {
                throw new IllegalArgumentException(
                    "shortx.snowflake.worker-id must be between 0 and " + MAX_WORKER_ID);
            }
            return configuredWorkerId;
        }

        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            // & MAX_WORKER_ID folds the hash into the valid 10-bit range.
            // Math.abs guards against Integer.MIN_VALUE's asymmetric hashCode.
            return Math.abs((long) hostname.hashCode()) & MAX_WORKER_ID;
        } catch (UnknownHostException e) {
            // Last-resort fallback for environments with no resolvable
            // hostname at all. Fine for single-instance/local use; multi-
            // instance production deployments should set the property
            // explicitly rather than rely on this branch.
            return 0L;
        }
    }

    private long waitForNextMillis(long currentLastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= currentLastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
