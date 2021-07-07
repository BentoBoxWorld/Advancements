package world.bentobox.advancements;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.advancements.objects.IsleAdvancements;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.Database;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.RanksManager;
import world.bentobox.bentobox.util.Util;

/**
 * Manages Island advancements
 * @author tastybento
 *
 */
public class AdvancementsManager {

    private final Advancements addon;
    // Database handler for level data
    private final Database<IsleAdvancements> handler;
    // A cache of island levels.
    private final Map<String, IsleAdvancements> cache;
    private final YamlConfiguration advConfig;
    private List<String> unknownAdvChange;

    /**
     * @param addon
     */
    public AdvancementsManager(Advancements addon) {
        this.addon = addon;
        // Get the BentoBox database
        // Set up the database handler to store and retrieve data
        // Note that these are saved by the BentoBox database
        handler = new Database<>(addon, IsleAdvancements.class);
        // Initialize the cache
        cache = new HashMap<>();
        // Advancement score sheet
        addon.saveResource("advancements.yml", false);
        advConfig = new YamlConfiguration();
        File advFile = new File(addon.getDataFolder(), "advancements.yml");
        if (!advFile.exists()) {
            addon.logError("advancements.yml cannot be found!");
        } else {
            try {
                advConfig.load(advFile);
                unknownAdvChange = advConfig.getStringList("settings.unknown-advancement-reward");
            } catch (IOException | InvalidConfigurationException e) {
                addon.logError("advancements.yml cannot be found! " + e.getLocalizedMessage());
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
     * Add advancement to island and give the reward to the player
     * @param p - player who just advanced
     * @param advancement - advancement
     * @return list of commands to run when advancement occurs. Empty if none.
     */
    public List<String> addAdvancement(Player p, Advancement advancement) {
        World world = Util.getWorld(p.getWorld());
        if (!addon.isRegisteredGameModeWorld(world)) {
            // Wrong world
            return Collections.emptyList();
        }
        List<String> rewards = getRewards(advancement.getKey().toString());
        if (rewards.isEmpty()) {
            return Collections.emptyList();
        }
        // Get island
        Island island = addon.getIslands().getIsland(world, p.getUniqueId());
        if (island != null
                && island.getRank(p.getUniqueId()) >= RanksManager.MEMBER_RANK // Only island members receive rewards
                && addAdvancement(island, advancement.getKey().toString())) {
            Util.runCommands(User.getInstance(p), rewards, "Advancement command");
            return rewards;
        }
        return Collections.emptyList();

    }

    private List<String> getRewards(String advancement) {
        String adv = "advancements." + advancement;
        // Check rewards for advancement
        if (advConfig.contains(adv)) {
            return advConfig.getStringList(adv);
        }
        // Advancement is not explicitly called out, so use defaults
        if (adv.endsWith("/root")) {
            return advConfig.getStringList("settings.default-root-reward");
        }
        if (adv.contains("recipes")) {
            return advConfig.getStringList("settings.default-recipe-reward");
        }
        return this.unknownAdvChange;
    }

}
