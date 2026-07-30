package world.bentobox.advancements;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.advancements.objects.IsleAdvancements;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;

/**
 * Manages Island advancements. When an island member completes an advancement, the island's
 * protection range grows by the advancement's score (like the Boxed game mode) and the player
 * receives the advancement's reward (like the Challenges addon).
 * @author tastybento
 *
 */
public class AdvancementsManager {

    /**
     * The outcome of {@link #addAdvancement(Player, Advancement)}.
     * @param score how much the island protection range grew. 0 if it did not change.
     * @param reward the reward given to the player. {@link Reward#EMPTY} if none.
     */
    public record Result(int score, Reward reward) {
        /** Nothing happened - the advancement was not registered to the island. */
        public static final Result NONE = new Result(0, Reward.EMPTY);
    }

    private final Advancements addon;
    // Database handler for advancement data
    private final Database<IsleAdvancements> handler;
    // A cache of island advancements.
    private final Map<String, IsleAdvancements> cache = new HashMap<>();
    private final YamlConfiguration advConfig = new YamlConfiguration();
    private int defaultRootIncrease;
    private int unknownAdvIncrease;
    private int unknownRecipeIncrease;
    private Reward rootReward = Reward.EMPTY;
    private Reward unknownAdvReward = Reward.EMPTY;
    private Reward recipeReward = Reward.EMPTY;

    /**
     * @param addon addon
     */
    public AdvancementsManager(Advancements addon) {
        this.addon = addon;
        // Set up the database handler to store and retrieve data
        // Note that these are saved by the BentoBox database
        handler = new Database<>(addon, IsleAdvancements.class);
        // Advancement score and reward sheet
        addon.saveResource("advancements.yml", false);
        File advFile = new File(addon.getDataFolder(), "advancements.yml");
        if (!advFile.exists()) {
            addon.logError("advancements.yml cannot be found!");
        } else {
            try {
                advConfig.load(advFile);
                defaultRootIncrease = advConfig.getInt("settings.default-root-increase", 0);
                unknownAdvIncrease = advConfig.getInt("settings.unknown-advancement-increase", 0);
                unknownRecipeIncrease = advConfig.getInt("settings.unknown-recipe-increase", 0);
                rootReward = Reward.fromSection(advConfig.getConfigurationSection("settings.default-root-reward"), addon::logError);
                unknownAdvReward = Reward.fromSection(advConfig.getConfigurationSection("settings.unknown-advancement-reward"), addon::logError);
                recipeReward = Reward.fromSection(advConfig.getConfigurationSection("settings.default-recipe-reward"), addon::logError);
            } catch (IOException | InvalidConfigurationException e) {
                addon.logError("advancements.yml cannot be loaded! " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Get advancements for the island, loading from database if required
     * @param island - island
     * @return the island's advancement list object
     */
    @NonNull
    public IsleAdvancements getIsland(Island island) {
        return cache.computeIfAbsent(island.getUniqueId(), this::getFromDb);
    }

    @NonNull
    private IsleAdvancements getFromDb(String k) {
        if (!handler.objectExists(k)) {
            return new IsleAdvancements(k);
        }
        @Nullable
        IsleAdvancements ia = handler.loadObject(k);
        return ia == null ? new IsleAdvancements(k) : ia;
    }

    /**
     * Save the island
     * @param island - island
     * @return CompletableFuture true if saved successfully
     */
    protected CompletableFuture<Boolean> saveIsland(Island island) {
        return cache.containsKey(island.getUniqueId()) ? handler.saveObjectAsync(cache.get(island.getUniqueId())): CompletableFuture.completedFuture(true);
    }

    /**
     * Save all values in the cache
     */
    protected void save() {
        cache.values().forEach(handler::saveObjectAsync);
    }

    /**
     * Remove island from cache
     * @param island - island
     */
    protected void removeFromCache(Island island) {
        cache.remove(island.getUniqueId());
    }

    /**
     * Add advancement to island
     * @param island - island
     * @param advancement - advancement string
     * @return true if added, false if already added
     */
    public boolean addAdvancement(Island island, String advancement) {
        if (hasAdvancement(island, advancement)) {
            return false;
        }
        getIsland(island).getAdvancements().add(advancement);
        this.saveIsland(island);
        return true;
    }

    /**
     * Remove advancement from island
     * @param island - island
     * @param advancement - advancement string
     */
    public void removeAdvancement(Island island, String advancement) {
        getIsland(island).getAdvancements().remove(advancement);
        this.saveIsland(island);
    }

    /**
     * Check if island has advancement
     * @param island - island
     * @param advancement - advancement
     * @return true if island has advancement, false if not
     */
    public boolean hasAdvancement(Island island, String advancement) {
        return getIsland(island).getAdvancements().contains(advancement);
    }

    /**
     * Check and correct the island's protection size based on accumulated advancements.
     * The baseline is the game mode's default island protection range.
     * @param island - island to check
     * @return value of island size change. Negative values mean the island range shrank.
     */
    public int checkIslandSize(Island island) {
        int baseSize = addon.getPlugin().getIWM().getIslandProtectionRange(island.getWorld());
        int shouldSize = baseSize + getIsland(island).getAdvancements().stream().mapToInt(this::getScore).sum();
        // Protection cannot be smaller than 1 or larger than the island's full range
        shouldSize = Math.max(1, Math.min(shouldSize, island.getRange()));
        int diff = shouldSize - island.getProtectionRange();
        if (diff != 0) {
            this.setProtectionSize(island, shouldSize, null);
        }
        return diff;
    }

    /**
     * Add an advancement to the player's island: grows the island protection range by the
     * advancement's score and gives the player the advancement's reward.
     * @param p - player who just advanced
     * @param advancement - advancement
     * @return result of the operation. {@link Result#NONE} if the advancement was not added,
     *         e.g., wrong world, no island, player is not a member, already completed, or the
     *         advancement has no score and no reward.
     */
    public Result addAdvancement(Player p, Advancement advancement) {
        World world = Util.getWorld(p.getWorld());
        if (world == null || !addon.isRegisteredGameModeWorld(world)) {
            // Wrong world
            return Result.NONE;
        }
        int score = getScore(advancement);
        Reward reward = getReward(advancement);
        if (score == 0 && reward.isEmpty()) {
            // Nothing to gain - do not track this advancement
            return Result.NONE;
        }
        // Get island
        Island island = addon.getIslands().getIsland(world, p.getUniqueId());
        if (island == null
                || island.getRank(p.getUniqueId()) < RanksManager.MEMBER_RANK // Only island members gain from advancements
                || !addAdvancement(island, advancement.getKey().toString())) {
            return Result.NONE;
        }
        if (score != 0) {
            int oldSize = island.getProtectionRange();
            // Protection cannot be smaller than 1 or larger than the island's full range
            int newSize = Math.max(1, Math.min(oldSize + score, island.getRange()));
            if (newSize != oldSize) {
                setProtectionSize(island, newSize, p.getUniqueId());
            }
        }
        grantReward(User.getInstance(p), reward);
        return new Result(score, reward);
    }

    /**
     * Give a reward to a player: items (overflow drops at their feet), experience, money and
     * commands.
     * @param user - recipient. Must be online.
     * @param reward - reward to give
     */
    public void grantReward(User user, Reward reward) {
        if (reward == null || reward.isEmpty() || !user.isOnline()) {
            return;
        }
        Player p = user.getPlayer();
        reward.items().forEach(item -> p.getInventory().addItem(item.clone())
                .forEach((k, v) -> p.getWorld().dropItem(p.getLocation(), v)));
        if (reward.experience() > 0) {
            p.giveExp(reward.experience());
        }
        if (reward.money() > 0) {
            addon.getPlugin().getVault().ifPresentOrElse(vault -> vault.deposit(user, reward.money(), p.getWorld()),
                    () -> addon.logWarning("Advancement has a money reward, but there is no economy plugin - skipping it."));
        }
        if (!reward.commands().isEmpty()) {
            Util.runCommands(user, reward.commands(), "Advancement reward");
        }
    }

    /**
     * Sets the island protection size and fires an event for it
     * @param island - island
     * @param newSize - new size of protected area
     * @param uuid - UUID of player making the change. null if the change is system-driven.
     */
    private void setProtectionSize(@NonNull Island island, int newSize, @Nullable UUID uuid) {
        int oldSize = island.getProtectionRange();
        island.setProtectionRange(newSize);
        // Call Protection Range Change event. Does not support canceling.
        IslandEvent.builder()
        .island(island)
        .location(island.getCenter())
        .reason(IslandEvent.Reason.RANGE_CHANGE)
        .involvedPlayer(uuid)
        .admin(true)
        .protectionRange(newSize, oldSize)
        .build();
    }

    /**
     * Get the score for this advancement namespaced key
     * @param key advancement namespaced key, e.g. "minecraft:story/mine_stone"
     * @return score or 0 if this key is unknown to the server.
     */
    public int getScore(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key);
        if (nk == null) {
            return 0;
        }
        Advancement a = Bukkit.getAdvancement(nk);
        return a == null ? 0 : getScore(a);
    }

    /**
     * Get the protection range increase for this advancement. Looks up the advancement in
     * advancements.yml: entries may be a plain number (score only) or a section with a
     * protection-range value. Roots, recipes and unlisted advancements use the defaults
     * from the settings section.
     * @param a - advancement
     * @return protection range increase
     */
    public int getScore(Advancement a) {
        String path = a.getKey().getKey();
        String node = "advancements." + path;
        if (advConfig.isConfigurationSection(node)) {
            return advConfig.getInt(node + ".protection-range", 0);
        }
        if (advConfig.contains(node)) {
            return advConfig.getInt(node, 0);
        }
        // Not listed - use category defaults
        if (isRoot(path)) {
            return defaultRootIncrease;
        }
        if (isRecipe(path)) {
            return unknownRecipeIncrease;
        }
        return unknownAdvIncrease;
    }

    /**
     * Get the reward for this advancement. An explicit rewards section on the advancement's
     * entry in advancements.yml wins; otherwise the category default from the settings
     * section applies (root, recipe, or unknown advancement).
     * @param a - advancement
     * @return reward, {@link Reward#EMPTY} if none
     */
    public Reward getReward(Advancement a) {
        String path = a.getKey().getKey();
        String node = "advancements." + path + ".rewards";
        if (advConfig.isConfigurationSection(node)) {
            return Reward.fromSection(advConfig.getConfigurationSection(node), addon::logError);
        }
        if (isRoot(path)) {
            return rootReward;
        }
        if (isRecipe(path)) {
            return recipeReward;
        }
        return unknownAdvReward;
    }

    private static boolean isRoot(String path) {
        return path.equals("root") || path.endsWith("/root");
    }

    private static boolean isRecipe(String path) {
        return path.startsWith("recipes/") || path.contains("/recipes/");
    }

}
