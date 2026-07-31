package world.bentobox.advancements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import world.bentobox.advancements.AdvancementsManager.Result;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;

/**
 * Tests for {@link AdvancementsManager}.
 */
class AdvancementsManagerTest extends CommonTestSetup {

    private static final String ADVANCEMENTS_YML =
            """
                    settings:
                      default-root-increase: 0
                      unknown-advancement-increase: 1
                      unknown-recipe-increase: 0
                      unknown-advancement-reward:
                        commands:
                        - test [player]
                    advancements:
                      'adventure/bullseye': 5
                      'end/kill_dragon':
                        protection-range: 2
                        rewards:
                          experience: 100
                          money: 10.5
                      'adventure/nothing':
                        protection-range: 0
                        rewards: {}
                    """;

    @Mock
    private Advancements addon;

    private AdvancementsManager manager;
    private MockedStatic<DatabaseSetup> mockDb;
    private final File dataFolder = new File("addons/AdvancementsTest");

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        // Database mock
        AbstractDatabaseHandler<Object> h = mock(AbstractDatabaseHandler.class);
        mockDb = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockDb.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(h.objectExists(anyString())).thenReturn(false);

        // Addon
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getIslands()).thenReturn(im);
        when(addon.isRegisteredGameModeWorld(any())).thenReturn(true);
        // The advancements.yml "resource" is pre-written to the data folder; saveResource is a no-op mock
        dataFolder.mkdirs();
        Files.write(new File(dataFolder, "advancements.yml").toPath(), ADVANCEMENTS_YML.getBytes(StandardCharsets.UTF_8));
        when(addon.getDataFolder()).thenReturn(dataFolder);

        // Island
        when(island.getUniqueId()).thenReturn("uniqueId");
        when(island.getProtectionRange()).thenReturn(10);
        when(island.getRange()).thenReturn(400);
        when(island.getRank(any(UUID.class))).thenReturn(RanksManager.MEMBER_RANK);
        when(island.getCenter()).thenReturn(location);
        when(island.getWorld()).thenReturn(world);
        when(im.getIsland(any(World.class), any(UUID.class))).thenReturn(island);

        // Player
        when(mockPlayer.isOnline()).thenReturn(true);

        // No economy by default
        when(plugin.getVault()).thenReturn(Optional.empty());

        // Stored advancement keys resolve to no server advancement by default
        mockedBukkit.when(() -> Bukkit.getAdvancement(any())).thenReturn(null);
        // Do not really run commands
        mockedUtil.when(() -> Util.runCommands(any(), anyList(), anyString())).then(invocation -> null);

        manager = new AdvancementsManager(addon);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (mockDb != null) {
            mockDb.closeOnDemand();
        }
        super.tearDown();
        deleteAll(new File("addons"));
    }

    private Advancement advancement(String key) {
        Advancement a = mock(Advancement.class);
        when(a.getKey()).thenReturn(NamespacedKey.minecraft(key));
        return a;
    }

    @Test
    void testGetScoreScalarEntry() {
        assertEquals(5, manager.getScore(advancement("adventure/bullseye")));
    }

    @Test
    void testGetScoreSectionEntry() {
        assertEquals(2, manager.getScore(advancement("end/kill_dragon")));
    }

    @Test
    void testGetScoreUnlistedUsesUnknownDefault() {
        assertEquals(1, manager.getScore(advancement("story/mine_stone")));
    }

    @Test
    void testGetScoreRoot() {
        assertEquals(0, manager.getScore(advancement("story/root")));
        assertEquals(0, manager.getScore(advancement("root")));
    }

    @Test
    void testGetScoreRecipe() {
        assertEquals(0, manager.getScore(advancement("recipes/misc/charcoal")));
    }

    @Test
    void testGetScoreStringUnknownKey() {
        // Bukkit.getAdvancement returns null, so the key scores nothing
        assertEquals(0, manager.getScore("minecraft:adventure/bullseye"));
        assertEquals(0, manager.getScore("not a valid key ##"));
    }

    @Test
    void testGetScoreStringKnownKey() {
        Advancement a = advancement("adventure/bullseye");
        mockedBukkit.when(() -> Bukkit.getAdvancement(any())).thenReturn(a);
        assertEquals(5, manager.getScore("minecraft:adventure/bullseye"));
    }

    @Test
    void testGetRewardExplicit() {
        Reward r = manager.getReward(advancement("end/kill_dragon"));
        assertEquals(100, r.experience());
        assertEquals(10.5D, r.money());
        assertTrue(r.items().isEmpty());
        assertTrue(r.commands().isEmpty());
    }

    @Test
    void testGetRewardUnlistedUsesUnknownDefault() {
        Reward r = manager.getReward(advancement("story/mine_stone"));
        assertEquals(1, r.commands().size());
    }

    @Test
    void testGetRewardRootAndRecipeAreEmptyByDefault() {
        assertSame(Reward.EMPTY, manager.getReward(advancement("story/root")));
        assertSame(Reward.EMPTY, manager.getReward(advancement("recipes/misc/charcoal")));
    }

    @Test
    void testAddAndRemoveAdvancementString() {
        assertTrue(manager.addAdvancement(island, "minecraft:adventure/bullseye"));
        assertTrue(manager.hasAdvancement(island, "minecraft:adventure/bullseye"));
        // Duplicate
        assertFalse(manager.addAdvancement(island, "minecraft:adventure/bullseye"));
        manager.removeAdvancement(island, "minecraft:adventure/bullseye");
        assertFalse(manager.hasAdvancement(island, "minecraft:adventure/bullseye"));
    }

    @Test
    void testAddAdvancementPlayerHappyPath() {
        Result result = manager.addAdvancement(mockPlayer, advancement("adventure/bullseye"));
        assertEquals(5, result.score());
        // Range grew from 10 to 15
        verify(island).setProtectionRange(15);
        assertTrue(manager.hasAdvancement(island, "minecraft:adventure/bullseye"));
    }

    @Test
    void testAddAdvancementPlayerCapsAtIslandRange() {
        when(island.getRange()).thenReturn(12);
        manager.addAdvancement(mockPlayer, advancement("adventure/bullseye"));
        verify(island).setProtectionRange(12);
    }

    @Test
    void testAddAdvancementPlayerWrongWorld() {
        when(addon.isRegisteredGameModeWorld(any())).thenReturn(false);
        assertSame(Result.NONE, manager.addAdvancement(mockPlayer, advancement("adventure/bullseye")));
        verify(island, never()).setProtectionRange(Mockito.anyInt());
    }

    @Test
    void testAddAdvancementPlayerVisitorRank() {
        when(island.getRank(any(UUID.class))).thenReturn(RanksManager.VISITOR_RANK);
        assertSame(Result.NONE, manager.addAdvancement(mockPlayer, advancement("adventure/bullseye")));
        verify(island, never()).setProtectionRange(Mockito.anyInt());
    }

    @Test
    void testAddAdvancementPlayerDuplicate() {
        manager.addAdvancement(mockPlayer, advancement("adventure/bullseye"));
        assertSame(Result.NONE, manager.addAdvancement(mockPlayer, advancement("adventure/bullseye")));
    }

    @Test
    void testAddAdvancementPlayerNothingToGain() {
        assertSame(Result.NONE, manager.addAdvancement(mockPlayer, advancement("adventure/nothing")));
        assertFalse(manager.hasAdvancement(island, "minecraft:adventure/nothing"));
    }

    @Test
    void testAddAdvancementPlayerNoIsland() {
        when(im.getIsland(any(World.class), any(UUID.class))).thenReturn(null);
        assertSame(Result.NONE, manager.addAdvancement(mockPlayer, advancement("adventure/bullseye")));
    }

    @Test
    void testAddAdvancementPlayerGivesReward() {
        manager.addAdvancement(mockPlayer, advancement("end/kill_dragon"));
        verify(mockPlayer).giveExp(100);
        // No economy plugin hooked - warn that the money reward was skipped
        verify(addon).logWarning(anyString());
    }

    @Test
    void testGrantRewardCommands() {
        User user = User.getInstance(mockPlayer);
        manager.grantReward(user, new Reward(java.util.Collections.emptyList(), 0, 0, java.util.List.of("test [player]")));
        mockedUtil.verify(() -> Util.runCommands(any(), anyList(), anyString()));
    }

    @Test
    void testGrantRewardEmptyDoesNothing() {
        manager.grantReward(User.getInstance(mockPlayer), Reward.EMPTY);
        verify(mockPlayer, never()).giveExp(Mockito.anyInt());
    }

    @Test
    void testCheckIslandSizeCorrectsRange() {
        when(iwm.getIslandProtectionRange(any())).thenReturn(100);
        when(island.getProtectionRange()).thenReturn(50);
        // No stored advancements, so the island should be at the game mode default of 100
        assertEquals(50, manager.checkIslandSize(island));
        verify(island).setProtectionRange(100);
    }

    @Test
    void testCheckIslandSizeNoChange() {
        when(iwm.getIslandProtectionRange(any())).thenReturn(100);
        when(island.getProtectionRange()).thenReturn(100);
        assertEquals(0, manager.checkIslandSize(island));
        verify(island, never()).setProtectionRange(Mockito.anyInt());
    }

    @Test
    void testCheckIslandSizeWithAdvancements() {
        when(iwm.getIslandProtectionRange(any())).thenReturn(100);
        when(island.getProtectionRange()).thenReturn(100);
        Advancement a = advancement("adventure/bullseye");
        mockedBukkit.when(() -> Bukkit.getAdvancement(any())).thenReturn(a);
        manager.addAdvancement(island, "minecraft:adventure/bullseye");
        // 100 + 5 = 105
        assertEquals(5, manager.checkIslandSize(island));
        verify(island).setProtectionRange(105);
    }
}
