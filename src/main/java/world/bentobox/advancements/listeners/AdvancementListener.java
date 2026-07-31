package world.bentobox.advancements.listeners;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import world.bentobox.advancements.Advancements;
import world.bentobox.advancements.AdvancementsManager;
import world.bentobox.bentobox.api.events.island.IslandNewIslandEvent;
import world.bentobox.bentobox.api.events.team.TeamJoinedEvent;
import world.bentobox.bentobox.api.events.team.TeamLeaveEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

/**
 * Handles advancement events for registered game mode worlds.
 * @author tastybento
 *
 */
public class AdvancementListener implements Listener {

    private static final Material[] MATS = Material.values();

    private final Advancements addon;
    private final Advancement netherAdvancement;
    private final Advancement endAdvancement;
    private final Advancement netherRoot;
    private final Advancement endRoot;

    /**
     * @param addon addon
     */
    public AdvancementListener(Advancements addon) {
        this.addon = addon;
        this.netherAdvancement = getAdvancement("minecraft:story/enter_the_nether");
        this.endAdvancement = getAdvancement("minecraft:story/enter_the_end");
        this.netherRoot = getAdvancement("minecraft:nether/root");
        this.endRoot = getAdvancement("minecraft:end/root");
    }

    /**
     * Get Advancement given the namespaced key for it
     * @param key namespaced key name for Advancement
     * @return Advancement or null if none found
     */
    public static Advancement getAdvancement(String key) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(Bukkit.advancementIterator(), Spliterator.ORDERED), false)
                .filter(a -> a.getKey().toString().equals(key))
                .findFirst().orElse(null);
    }

    /**
     * Grows the island and rewards the player when an advancement is done.
     * Removes advancements from visitors if they are denied them.
     * @param e PlayerAdvancementDoneEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        // Ignore players not in survival
        if (!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)
                || !addon.isRegisteredGameModeWorld(e.getPlayer().getWorld())) {
            return;
        }
        // Only allow members or higher to get advancements on an island
        if (addon.getSettings().isDenyVisitorAdvancements()
                && !addon.getIslands().getIslandAt(e.getPlayer().getLocation())
                .map(i -> i.getMemberSet().contains(e.getPlayer().getUniqueId())).orElse(false)) {
            // Remove advancement from player
            e.getAdvancement().getCriteria().forEach(c ->
            e.getPlayer().getAdvancementProgress(e.getAdvancement()).revokeCriteria(c));
            User u = User.getInstance(e.getPlayer());
            if (addon.getAdvManager().getScore(e.getAdvancement()) > 0) {
                u.notify("advancements.adv-disallowed", TextVariables.NAME, e.getPlayer().getName(),
                        TextVariables.DESCRIPTION, this.keyToString(u, e.getAdvancement().getKey()));
            }
            return;
        }
        // Add the advancement to the island
        AdvancementsManager.Result result = addon.getAdvManager().addAdvancement(e.getPlayer(), e.getAdvancement());
        if (!AdvancementsManager.Result.NONE.equals(result)) {
            User user = User.getInstance(e.getPlayer());
            // Tell the team one tick after it occurs
            Bukkit.getScheduler().runTask(addon.getPlugin(),
                    () -> tellTeam(user, e.getAdvancement().getKey(), result.score()));
        }
    }

    private void tellTeam(User user, NamespacedKey key, int score) {
        World world = Util.getWorld(user.getWorld());
        Island island = addon.getIslands().getIsland(world, user);
        if (island == null) {
            // Something went wrong here
            return;
        }
        island.getMemberSet().stream().map(User::getInstance).filter(User::isOnline)
        .forEach(u -> {
            informPlayer(u, key, score);
            // Sync
            grantAdv(u, addon.getAdvManager().getIsland(island).getAdvancements());
        });
        // Broadcast
        if (addon.getSettings().isBroadcastAdvancements()) {
            Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission(Server.BROADCAST_CHANNEL_USERS))
            .map(User::getInstance)
            .forEach(u -> u.sendMessage("advancements.user-completed", TextVariables.NAME, user.getName(),
                    TextVariables.DESCRIPTION, this.keyToString(u, key)));
        }
    }

    /**
     * Synchronize the player's advancements to that of the island.
     * Player's advancements should be cleared before calling this otherwise they will get add the island ones as well.
     * @param user - user
     */
    public void syncAdvancements(User user) {
        World world = Util.getWorld(user.getWorld());
        if (world == null || !addon.isRegisteredGameModeWorld(world)) {
            return;
        }
        Island island = addon.getIslands().getIsland(world, user);
        if (island != null) {
            grantAdv(user, addon.getAdvManager().getIsland(island).getAdvancements());
            int diff = addon.getAdvManager().checkIslandSize(island);
            if (diff > 0) {
                user.sendMessage("advancements.size-changed", TextVariables.NUMBER, String.valueOf(diff));
                user.getPlayer().playSound(Objects.requireNonNull(user.getLocation()), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
            } else if (diff < 0) {
                user.sendMessage("advancements.size-decreased", TextVariables.NUMBER, String.valueOf(Math.abs(diff)));
                user.getPlayer().playSound(Objects.requireNonNull(user.getLocation()), Sound.ENTITY_VILLAGER_DEATH, 1F, 2F);
            }
        }
    }

    private void informPlayer(User user, NamespacedKey key, int score) {
        user.getPlayer().playSound(Objects.requireNonNull(user.getLocation()), Sound.ENTITY_PLAYER_LEVELUP, 1F, 2F);
        user.sendMessage("advancements.completed", TextVariables.NAME, keyToString(user, key));
        if (score != 0) {
            user.sendMessage(score > 0 ? "advancements.size-changed" : "advancements.size-decreased",
                    TextVariables.NUMBER, String.valueOf(Math.abs(score)));
        }
    }

    private String keyToString(User user, NamespacedKey key) {
        String adv = user.getTranslationOrNothing("advancements.names." + key.toString());
        if (adv.isEmpty()) {
            adv = Util.prettifyText(key.getKey().substring(key.getKey().lastIndexOf("/") + 1));
        }
        return adv;
    }

    /**
     * Special case Advancement awarding
     * Awards the nether and end advancements when they use a portal for the first time,
     * because game mode worlds do not always trigger these naturally.
     * @param e PlayerPortalEvent
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)
                || !addon.isRegisteredGameModeWorld(e.getPlayer().getWorld())) {
            return;
        }
        if (e.getCause().equals(TeleportCause.NETHER_PORTAL)) {
            giveAdv(e.getPlayer(), netherAdvancement);
            giveAdv(e.getPlayer(), netherRoot);

        } else if (e.getCause().equals(TeleportCause.END_PORTAL)) {
            giveAdv(e.getPlayer(), endAdvancement);
            giveAdv(e.getPlayer(), endRoot);
        }
    }

    /**
     * Give player an advancement
     * @param player - player
     * @param adv - Advancement
     */
    public static void giveAdv(Player player, Advancement adv) {
        if (adv != null && !player.getAdvancementProgress(adv).isDone()) {
            adv.getCriteria().forEach(player.getAdvancementProgress(adv)::awardCriteria);
        }
    }

    /**
     * Sync advancements when player joins server if they are in a registered game mode world
     * @param e PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        syncAdvancements(User.getInstance(e.getPlayer()));
    }

    /**
     * Sync advancements when player enters a registered game mode world
     * @param e PlayerChangedWorldEvent
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerEnterWorld(PlayerChangedWorldEvent e) {
        syncAdvancements(User.getInstance(e.getPlayer()));
    }

    /**
     * Clear and sync advancements for a player when they join a team if the settings require it
     * @param e TeamJoinedEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamJoinTime(TeamJoinedEvent e) {
        User user = User.getInstance(e.getPlayerUUID());
        if (user != null && addon.getSettings().isOnJoinResetAdvancements() && user.isOnline()
                && addon.isRegisteredGameModeWorld(Util.getWorld(user.getWorld()))) {
            // Clear and set advancements
            clearAndSetAdv(user, addon.getSettings().isOnJoinResetAdvancements(), addon.getSettings().getOnJoinGrantAdvancements());
            // Set advancements to same as island
            syncAdvancements(user);
        }
    }

    /**
     * Clear player's advancements when they leave a team if the setting requires it
     * @param e TeamLeaveEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeamLeaveTime(TeamLeaveEvent e) {
        User user = User.getInstance(e.getPlayerUUID());
        if (user != null && addon.getSettings().isOnLeaveResetAdvancements() && user.isOnline()
                && addon.isRegisteredGameModeWorld(Util.getWorld(user.getWorld()))) {
            // Clear and set advancements
            clearAndSetAdv(user, addon.getSettings().isOnLeaveResetAdvancements(), addon.getSettings().getOnLeaveGrantAdvancements());
        }
    }

    /**
     * Clear player's advancements when they start an island for the first time.
     * @param e IslandNewIslandEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFirstTime(IslandNewIslandEvent e) {
        if (!addon.isRegisteredGameModeWorld(e.getIsland().getWorld())) {
            return;
        }
        User user = User.getInstance(e.getPlayerUUID());
        clearAndSetAdv(user, addon.getSettings().isOnJoinResetAdvancements(), addon.getSettings().getOnJoinGrantAdvancements());
    }

    /**
     * Clear and set advancements for user. Will not do anything if the user is offline
     * @param user - user
     * @param clear - whether to clear advancements for this user or not
     * @param list - list of advancements (namespaced keys) to grant to user
     */
    private void clearAndSetAdv(User user, boolean clear, List<String> list) {
        if (!user.isOnline()) {
            return;
        }
        if (clear) {
            clearAdv(user);
        }
        grantAdv(user, list);
    }

    /**
     * Grant advancement to user
     * @param user - user
     * @param list - list of advancements to grant
     */
    private void grantAdv(User user, List<String> list) {
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement a = it.next();
            AdvancementProgress progress = user.getPlayer().getAdvancementProgress(a);
            if (list.contains(a.getKey().toString()) && !progress.isDone()) {
                // Award
                a.getCriteria().forEach(progress::awardCriteria);
            }
        }
    }

    private void clearAdv(User user) {
        // Clear Statistics
        Bukkit.getScheduler().runTaskAsynchronously(addon.getPlugin(),
                () -> Arrays.stream(Statistic.values()).forEach(s -> resetStats(user, s)));
        // Clear advancements
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement a = it.next();
            AdvancementProgress p = user.getPlayer().getAdvancementProgress(a);
            p.getAwardedCriteria().forEach(p::revokeCriteria);
        }
    }

    private void resetStats(User user, Statistic s) {
        switch(s.getType()) {
        case BLOCK ->
            Arrays.stream(MATS).filter(Material::isBlock).forEach(m -> user.getPlayer().setStatistic(s, m, 0));
        case ITEM -> Arrays.stream(MATS).filter(Material::isItem).forEach(m -> user.getPlayer().setStatistic(s, m, 0));
        case ENTITY -> Arrays.stream(EntityType.values()).filter(EntityType::isAlive).forEach(m -> user.getPlayer().setStatistic(s, m, 0));
        case UNTYPED -> user.getPlayer().setStatistic(s, 0);
        }
    }

}
