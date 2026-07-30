package world.bentobox.advancements.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.google.common.collect.ImmutableSet;

import world.bentobox.advancements.Advancements;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.advancements.AdvancementsManager;
import world.bentobox.advancements.CommonTestSetup;
import world.bentobox.advancements.Reward;
import world.bentobox.advancements.Settings;
import world.bentobox.advancements.objects.IsleAdvancements;

/**
 * Tests for {@link AdvancementListener}.
 */
class AdvancementListenerTest extends CommonTestSetup {

    @Mock
    private Advancements addon;
    @Mock
    private Settings settings;
    @Mock
    private AdvancementsManager advManager;
    @Mock
    private Advancement advancement;
    @Mock
    private AdvancementProgress progress;

    private AdvancementListener listener;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getAdvManager()).thenReturn(advManager);
        when(addon.getIslands()).thenReturn(im);
        when(addon.isRegisteredGameModeWorld(any())).thenReturn(true);

        // Settings defaults
        when(settings.isDenyVisitorAdvancements()).thenReturn(true);
        when(settings.isBroadcastAdvancements()).thenReturn(false);
        when(settings.isOnJoinResetAdvancements()).thenReturn(true);
        when(settings.getOnJoinGrantAdvancements()).thenReturn(Collections.emptyList());
        when(settings.isOnLeaveResetAdvancements()).thenReturn(false);
        when(settings.getOnLeaveGrantAdvancements()).thenReturn(Collections.emptyList());

        // Player is in survival and on their own island
        when(mockPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(mockPlayer.isOnline()).thenReturn(true);
        when(im.getIslandAt(any())).thenReturn(java.util.Optional.of(island));
        when(im.getIsland(any(), any(User.class))).thenReturn(island);
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid));

        // Advancement under test
        when(advancement.getKey()).thenReturn(NamespacedKey.minecraft("adventure/bullseye"));
        when(advancement.getCriteria()).thenReturn(List.of("criterion"));
        when(mockPlayer.getAdvancementProgress(any())).thenReturn(progress);

        // The server has no advancements registered by default
        mockedBukkit.when(Bukkit::advancementIterator).thenAnswer(invocation -> Collections.emptyIterator());

        // Island advancement store
        when(advManager.getIsland(any())).thenReturn(new IsleAdvancements("uniqueId"));
        when(advManager.checkIslandSize(any())).thenReturn(0);

        listener = new AdvancementListener(addon);
    }

    private PlayerAdvancementDoneEvent advancementEvent() {
        return new PlayerAdvancementDoneEvent(mockPlayer, advancement);
    }

    @Test
    void testOnAdvancementHappyPath() {
        when(advManager.addAdvancement(mockPlayer, advancement))
        .thenReturn(new AdvancementsManager.Result(5, Reward.EMPTY));
        listener.onAdvancement(advancementEvent());
        verify(advManager).addAdvancement(mockPlayer, advancement);
        // Team is told one tick later
        verify(sch).runTask(any(), any(Runnable.class));
        // Nothing was revoked
        verify(progress, never()).revokeCriteria(any());
    }

    @Test
    void testOnAdvancementNothingGained() {
        when(advManager.addAdvancement(mockPlayer, advancement)).thenReturn(AdvancementsManager.Result.NONE);
        listener.onAdvancement(advancementEvent());
        verify(sch, never()).runTask(any(), any(Runnable.class));
    }

    @Test
    void testOnAdvancementNotSurvival() {
        when(mockPlayer.getGameMode()).thenReturn(GameMode.CREATIVE);
        listener.onAdvancement(advancementEvent());
        verify(advManager, never()).addAdvancement(any(), any(Advancement.class));
    }

    @Test
    void testOnAdvancementWrongWorld() {
        when(addon.isRegisteredGameModeWorld(any())).thenReturn(false);
        listener.onAdvancement(advancementEvent());
        verify(advManager, never()).addAdvancement(any(), any(Advancement.class));
    }

    @Test
    void testOnAdvancementVisitorDenied() {
        // Player is not a member of the island they are standing on
        when(island.getMemberSet()).thenReturn(ImmutableSet.of());
        when(advManager.getScore(advancement)).thenReturn(5);
        listener.onAdvancement(advancementEvent());
        // Advancement is revoked and not registered
        verify(progress).revokeCriteria("criterion");
        verify(advManager, never()).addAdvancement(any(), any(Advancement.class));
        // Player is notified because the advancement had a score
        verify(notifier).notify(any(), any());
    }

    @Test
    void testOnAdvancementVisitorDeniedNoScoreNoNotify() {
        when(island.getMemberSet()).thenReturn(ImmutableSet.of());
        when(advManager.getScore(advancement)).thenReturn(0);
        listener.onAdvancement(advancementEvent());
        verify(progress).revokeCriteria("criterion");
        verify(notifier, never()).notify(any(), any());
    }

    @Test
    void testOnAdvancementVisitorsAllowed() {
        when(settings.isDenyVisitorAdvancements()).thenReturn(false);
        when(island.getMemberSet()).thenReturn(ImmutableSet.of());
        when(advManager.addAdvancement(mockPlayer, advancement)).thenReturn(AdvancementsManager.Result.NONE);
        listener.onAdvancement(advancementEvent());
        verify(progress, never()).revokeCriteria(any());
        verify(advManager).addAdvancement(mockPlayer, advancement);
    }

    @Test
    void testOnPortalNetherGrantsAdvancements() throws Exception {
        // Register the nether advancements on the server, then re-create the listener so it finds them
        Advancement nether = mock(Advancement.class);
        when(nether.getKey()).thenReturn(NamespacedKey.minecraft("story/enter_the_nether"));
        when(nether.getCriteria()).thenReturn(List.of("entered"));
        mockedBukkit.when(Bukkit::advancementIterator).thenAnswer(invocation -> List.of(nether).iterator());
        listener = new AdvancementListener(addon);

        when(progress.isDone()).thenReturn(false);
        PlayerPortalEvent e = new PlayerPortalEvent(mockPlayer, location, location, TeleportCause.NETHER_PORTAL);
        listener.onPortal(e);
        verify(progress).awardCriteria("entered");
    }

    @Test
    void testOnPortalWrongWorld() {
        when(addon.isRegisteredGameModeWorld(any())).thenReturn(false);
        PlayerPortalEvent e = new PlayerPortalEvent(mockPlayer, location, location, TeleportCause.NETHER_PORTAL);
        listener.onPortal(e);
        verify(mockPlayer, never()).getAdvancementProgress(any());
    }

    @Test
    void testOnPlayerJoinSyncs() {
        when(advManager.checkIslandSize(island)).thenReturn(5);
        listener.onPlayerJoin(new PlayerJoinEvent(mockPlayer, "join message"));
        verify(advManager).checkIslandSize(island);
        // Size increase message plays the level-up sound
        verify(mockPlayer).playSound(any(org.bukkit.Location.class), any(org.bukkit.Sound.class), any(Float.class), any(Float.class));
    }

    @Test
    void testOnPlayerJoinWrongWorldNoSync() {
        when(addon.isRegisteredGameModeWorld(any())).thenReturn(false);
        listener.onPlayerJoin(new PlayerJoinEvent(mockPlayer, "join message"));
        verify(advManager, never()).checkIslandSize(any());
    }

    @Test
    void testSyncAdvancementsNoIsland() {
        when(im.getIsland(any(), any(User.class))).thenReturn(null);
        listener.syncAdvancements(User.getInstance(mockPlayer));
        verify(advManager, never()).checkIslandSize(any());
    }
}
