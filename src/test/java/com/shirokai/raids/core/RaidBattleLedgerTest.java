package com.shirokai.raids.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.shirokai.raids.core.RaidBattleLedger.Outcome.*;

class RaidBattleLedgerTest {
    private final UUID raid = UUID.randomUUID();
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    private final UUID sa = UUID.randomUUID(), sb = UUID.randomUUID(), sc = UUID.randomUUID();
    private RaidBattleLedger threePlayers(long hp) {
        var ledger = new RaidBattleLedger(raid, hp, 3);
        assertTrue(ledger.openSession(sa, a, UUID.randomUUID()));
        assertTrue(ledger.openSession(sb, b, UUID.randomUUID()));
        assertTrue(ledger.openSession(sc, c, UUID.randomUUID()));
        return ledger;
    }
    @Test void threeIndependentSessionsContributeToOneHealth() {
        var ledger = threePlayers(10_000_000);
        ledger.damage(raid, sa, 1, 100_000);
        ledger.damage(raid, sb, 1, 150_000);
        ledger.damage(raid, sc, 1, 200_000);
        var snapshot = ledger.snapshot();
        assertEquals(9_550_000, snapshot.remaining());
        assertEquals(450_000, snapshot.totalDamage());
        assertEquals(100.0 / 4.5, snapshot.contributionPercent(a), 1e-8);
        assertEquals(3, snapshot.activeSessions());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.contributions().clear());
    }
    @Test void rejectsReplaysGapsUnknownSessionsAndOldRaids() {
        var ledger = threePlayers(100);
        assertEquals(WRONG_RAID, ledger.damage(UUID.randomUUID(), sa, 1, 99).outcome());
        assertEquals(UNKNOWN_SESSION, ledger.damage(raid, UUID.randomUUID(), 1, 99).outcome());
        assertEquals(OUT_OF_ORDER, ledger.damage(raid, sa, 2, 99).outcome());
        assertEquals(APPLIED, ledger.damage(raid, sa, 1, 10).outcome());
        assertEquals(OUT_OF_ORDER, ledger.damage(raid, sa, 1, 10).outcome());
        assertEquals(90, ledger.snapshot().remaining());
        assertThrows(IllegalArgumentException.class, () -> ledger.damage(raid, sa, 2, -1));
        assertEquals(ZERO_DAMAGE, ledger.damage(raid, sa, 2, 0).outcome());
        assertEquals(OUT_OF_ORDER, ledger.damage(raid, sa, 2, 99).outcome());
    }
    @Test void overkillCountsOnlyEffectiveDamageAndEndsExactlyOnce() {
        var ledger = threePlayers(100);
        ledger.damage(raid, sa, 1, 75);
        var last = ledger.damage(raid, sb, 1, Long.MAX_VALUE);
        assertEquals(25, last.applied()); assertTrue(last.defeatedNow());
        assertEquals(CLOSED, ledger.damage(raid, sc, 1, 100).outcome());
        assertFalse(ledger.damage(raid, sb, 1, 100).defeatedNow());
        assertFalse(ledger.openSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(75, ledger.snapshot().contributionPercent(a));
        assertEquals(25, ledger.snapshot().contributionPercent(b));
        assertEquals(100, ledger.snapshot().contributions().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void departurePreservesDamageAndFreesCapacityWithoutReusingBossCopies() {
        var ledger = new RaidBattleLedger(raid, 100, 1);
        var copy = UUID.randomUUID();
        assertTrue(ledger.openSession(sa, a, copy));
        assertFalse(ledger.openSession(sb, b, UUID.randomUUID()));
        ledger.damage(raid, sa, 1, 20);
        ledger.closeSession(sa); ledger.closeSession(sa);
        assertEquals(UNKNOWN_SESSION, ledger.damage(raid, sa, 2, 20).outcome());
        assertFalse(ledger.openSession(sa, a, UUID.randomUUID()));
        assertFalse(ledger.openSession(sb, b, copy));
        assertTrue(ledger.openSession(sb, b, UUID.randomUUID()));
        assertEquals(20, ledger.snapshot().contributions().get(a));
        ledger.cancel();
        assertEquals(CLOSED, ledger.damage(raid, sb, 1, 80).outcome());
        assertEquals(80, ledger.snapshot().remaining());
    }
    @Test void aPlayerCannotOpenTwoConcurrentBattlesOrShareABossCopy() {
        var ledger = new RaidBattleLedger(raid, 100, 100);
        var copy = UUID.randomUUID();
        assertTrue(ledger.openSession(sa, a, copy));
        assertFalse(ledger.openSession(sb, a, UUID.randomUUID()));
        assertFalse(ledger.openSession(sc, c, copy));
        assertThrows(IllegalArgumentException.class, () -> new RaidBattleLedger(raid, 1, 101));
    }
    @Test void duplicateConcurrentCallbacksCannotApplyTwice() throws Exception {
        var ledger = threePlayers(100);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<RaidBattleLedger.DamageResult>>();
            for (int i = 0; i < 100; i++) results.add(pool.submit(() -> ledger.damage(raid, sa, 1, 100)));
            long applied = 0, victories = 0;
            for (var result : results) {
                var value = result.get(); applied += value.applied(); victories += value.defeatedNow() ? 1 : 0;
            }
            assertEquals(100, applied); assertEquals(1, victories);
        }
    }
    // Accounting only: these are not Showdown sessions or Minecraft load tests.
    @ParameterizedTest @ValueSource(ints = {1, 3, 10, 25, 50, 100})
    void accountingAtPlannedParticipantCounts(int count) {
        var ledger = new RaidBattleLedger(raid, count * 100L, count);
        for (int i = 0; i < count; i++) {
            var session = UUID.randomUUID();
            assertTrue(ledger.openSession(session, UUID.randomUUID(), UUID.randomUUID()));
            assertEquals(100, ledger.damage(raid, session, 1, 100).applied());
        }
        var snapshot = ledger.snapshot();
        assertEquals(0, snapshot.remaining()); assertEquals(count, snapshot.contributions().size());
        assertTrue(snapshot.closed());
    }
}
