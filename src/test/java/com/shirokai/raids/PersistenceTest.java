package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path dir;
    @Test void restartKeepsPendingOfflineRewardsAndCompletedRaidLedger() throws Exception {
        var state=new RaidState(); state.nextRaid=12345;
        state.completed.add("raid-1");
        state.pending.put("offline",new ArrayList<>(List.of(new RaidState.Pending("raid-1","minecraft:diamond",3))));
        state.uncertain.put("interrupted",new RaidState.Pending("raid-1","minecraft:diamond",1));
        var path=dir.resolve("data/shirokai-raids/state.json");
        JsonFiles.write(path,state);
        var restored=JsonFiles.read(path,RaidState.class); restored.validate();
        assertEquals(12345,restored.nextRaid);
        assertEquals(3,restored.pending.get("offline").getFirst().count);
        assertTrue(restored.completed.contains("raid-1"));
        assertTrue(restored.uncertain.containsKey("interrupted"));
    }
    @Test void customConfigsAreNotOverwrittenAndCorruptStateIsNotSilentlyReset() throws Exception {
        Path p=dir.resolve("config.json");
        var config=new RaidConfig();config.intervalSeconds=10000;
        JsonFiles.defaultFile(p,config);JsonFiles.defaultFile(p,new RaidConfig());
        assertEquals(10000,JsonFiles.read(p,RaidConfig.class).intervalSeconds);
        Files.writeString(p,"{bad json");
        assertThrows(java.io.IOException.class,()->JsonFiles.read(p,RaidState.class));
        assertEquals("{bad json",Files.readString(p));
    }
    @Test void rejectsDirectoryAsStateAndWorksWithUnicodePaths() throws Exception {
        assertThrows(java.io.IOException.class,()->JsonFiles.read(dir,RaidState.class));
        Path p=dir.resolve("dados com espaço/ação.json");
        JsonFiles.write(p,new RaidState());
        assertNotNull(JsonFiles.read(p,RaidState.class));
    }
    @Test void invalidConfigurationCannotEscapeSafetyLimits() {
        var config=new RaidConfig();config.validate();config.attackCooldownTicks=0;
        assertThrows(IllegalArgumentException.class,config::validate);
        var boss=new RaidConfig.Boss();boss.id="../escape";
        assertThrows(IllegalArgumentException.class,boss::validate);
        var rewards=new RaidConfig.Rewards();rewards.items.getFirst().maximum=0;
        assertThrows(IllegalArgumentException.class,rewards::validate);
    }
    @Test void concurrentPlayerLimitDefaultsTo100AndPreservesLegacyChoices() throws Exception {
        var defaults = new RaidConfig(); defaults.validate();
        assertEquals(100, defaults.maximumParticipants);
        var path = dir.resolve("config.json");
        Files.writeString(path, "{\"maximumParticipants\":25}");
        assertEquals(25, JsonFiles.read(path, RaidConfig.class).maximumParticipants);
        Files.writeString(path, "{\"maxConcurrentPlayers\":50}");
        var custom = JsonFiles.read(path, RaidConfig.class);
        assertEquals(50, custom.maximumParticipants);
        JsonFiles.write(path, custom);
        assertTrue(Files.readString(path).contains("\"maxConcurrentPlayers\""));
        defaults.maximumParticipants = 101;
        assertThrows(IllegalArgumentException.class, defaults::validate);
    }
}
