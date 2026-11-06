package com.shirokai.raids.core;

import java.util.*;

/**
 * Authoritative accounting for a future server-side Showdown adapter.
 * This class does not start battles or calculate move damage. Only trusted, ordered
 * engine events may call damage; never expose it as a client packet or command.
 */
public final class RaidBattleLedger {
    public enum Outcome { APPLIED, ZERO_DAMAGE, WRONG_RAID, UNKNOWN_SESSION, OUT_OF_ORDER, CLOSED }
    public record DamageResult(Outcome outcome, long applied, long remaining, boolean defeatedNow) {}
    public record Snapshot(UUID raidId, long maximum, long remaining, boolean closed,
                           int activeSessions, Map<UUID, Long> contributions) {
        public long totalDamage() { return maximum - remaining; }
        public double contributionPercent(UUID player) {
            return totalDamage() == 0 ? 0 : 100.0 * contributions.getOrDefault(player, 0L) / totalDamage();
        }
    }
    private static final class Session {
        final UUID player;
        long sequence;
        Session(UUID player) { this.player = player; }
    }
    private final UUID raidId;
    private final long maximum;
    private final int capacity;
    private long remaining;
    private boolean closed;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> usedSessions = new HashSet<>();
    private final Set<UUID> usedBossCopies = new HashSet<>();
    private final Map<UUID, Long> contributions = new HashMap<>();

    public RaidBattleLedger(UUID raidId, long maximum, int capacity) {
        this.raidId = Objects.requireNonNull(raidId);
        if (maximum <= 0 || capacity < 1 || capacity > 100) throw new IllegalArgumentException("Invalid raid limits");
        this.maximum = this.remaining = maximum;
        this.capacity = capacity;
    }

    /** Each engine battle and boss copy needs a new identity; never reuse the display boss. */
    public synchronized boolean openSession(UUID sessionId, UUID player, UUID bossCopyId) {
        Objects.requireNonNull(sessionId); Objects.requireNonNull(player); Objects.requireNonNull(bossCopyId);
        if (closed || sessions.size() >= capacity || players.contains(player)
                || usedSessions.contains(sessionId) || usedBossCopies.contains(bossCopyId)) return false;
        sessions.put(sessionId, new Session(player));
        players.add(player); usedSessions.add(sessionId); usedBossCopies.add(bossCopyId);
        return true;
    }

    /** Disconnect/faint ends only that battle; contribution survives and its slot is released. */
    public synchronized void closeSession(UUID sessionId) {
        var removed = sessions.remove(sessionId);
        if (removed != null) players.remove(removed.player);
    }

    /**
     * Sequences begin at one, and are assigned once per authoritative engine event,
     * including zero damage. Public/private protocol copies must share that identity.
     * Gaps are rejected rather than silently losing accounting; the adapter must fail
     * the affected session safely if an ordered stream cannot be recovered.
     */
    public synchronized DamageResult damage(UUID eventRaid, UUID sessionId, long sequence, long requested) {
        if (!raidId.equals(eventRaid)) return result(Outcome.WRONG_RAID, 0, false);
        if (closed) return result(Outcome.CLOSED, 0, false);
        var session = sessions.get(sessionId);
        if (session == null) return result(Outcome.UNKNOWN_SESSION, 0, false);
        if (sequence <= 0 || session.sequence == Long.MAX_VALUE || sequence != session.sequence + 1)
            return result(Outcome.OUT_OF_ORDER, 0, false);
        if (requested < 0) throw new IllegalArgumentException("Damage must not heal the global boss");
        session.sequence = sequence;
        if (requested == 0) return result(Outcome.ZERO_DAMAGE, 0, false);
        long applied = Math.min(requested, remaining);
        remaining -= applied;
        contributions.merge(session.player, applied, Long::sum);
        boolean defeated = remaining == 0;
        if (defeated) closed = true;
        return result(Outcome.APPLIED, applied, defeated);
    }

    /** Cancellation is distinct from victory: it must never trigger a reward roll. */
    public synchronized void cancel() { closed = true; }
    public synchronized Set<UUID> sessionIds() { return Set.copyOf(sessions.keySet()); }
    public synchronized Snapshot snapshot() {
        return new Snapshot(raidId, maximum, remaining, closed, sessions.size(), Map.copyOf(contributions));
    }
    private DamageResult result(Outcome outcome, long applied, boolean defeated) {
        return new DamageResult(outcome, applied, remaining, defeated);
    }
}
