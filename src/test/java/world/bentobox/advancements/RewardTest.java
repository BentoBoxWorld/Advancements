package world.bentobox.advancements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Reward} parsing.
 */
class RewardTest extends CommonTestSetup {

    private final List<String> errors = new ArrayList<>();

    private Reward parse(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return Reward.fromSection(config.getConfigurationSection("rewards"), errors::add);
    }

    @Test
    void testNullSectionIsEmpty() {
        assertSame(Reward.EMPTY, Reward.fromSection(null, errors::add));
        assertTrue(errors.isEmpty());
    }

    @Test
    void testEmptySectionIsEmpty() throws Exception {
        Reward r = parse("rewards: {}\n");
        assertSame(Reward.EMPTY, r);
    }

    @Test
    void testParseFull() throws Exception {
        Reward r = parse("""
                rewards:
                  experience: 5
                  money: 10.5
                  commands:
                  - give [player] diamond 1
                """);
        assertFalse(r.isEmpty());
        assertEquals(5, r.experience());
        assertEquals(10.5D, r.money());
        assertEquals(List.of("give [player] diamond 1"), r.commands());
        assertTrue(r.items().isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void testBadItemLogsAndSkips() throws Exception {
        Reward r = parse("""
                rewards:
                  items:
                  - NOT_A_REAL_ITEM:1
                  experience: 1
                """);
        assertEquals(1, errors.size());
        assertTrue(r.items().isEmpty());
        assertEquals(1, r.experience());
    }

    @Test
    void testCommandsOnlyIsNotEmpty() throws Exception {
        Reward r = parse("""
                rewards:
                  commands:
                  - some command
                """);
        assertFalse(r.isEmpty());
    }

    @Test
    void testZeroValuesAreEmpty() throws Exception {
        Reward r = parse("""
                rewards:
                  experience: 0
                  money: 0
                """);
        assertTrue(r.isEmpty());
        assertSame(Reward.EMPTY, r);
    }
}
