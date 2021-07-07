package world.bentobox.advancements;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;

import world.bentobox.advancements.listeners.AdvancementListener;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.util.Util;

public class Advancements extends Addon {

    // Settings
    private Settings settings;
    private Config<Settings> configObject = new Config<>(this, Settings.class);
    private final List<GameModeAddon> registeredGameModes = new ArrayList<>();

    private AdvancementsManager advManager;

    @Override
    public void onLoad() {
        // Save the default config from config.yml
        saveDefaultConfig();
        if (loadSettings()) {
            // Disable
            logError("Advancement settings could not load! Addon disabled.");
            setState(State.DISABLED);
        } else {
            configObject.saveConfigObject(settings);
        }
    }

    private boolean loadSettings() {
        settings = configObject.loadConfigObject();
        return settings == null;
    }

    @Override
    public void onDisable() {
        // Save the advancements cache
        getAdvManager().save();

    }

    @Override
    public void onEnable() {
        // Advancements manager
        advManager = new AdvancementsManager(this);
        // Register listeners
        this.registerListener(new AdvancementListener(this));
        // Register commands for GameModes
        registeredGameModes.clear();
        getPlugin().getAddonsManager().getGameModeAddons().stream()
        .filter(gm -> !settings.getGameModes().contains(gm.getDescription().getName()))
        .forEach(gm -> {
            log("Advancements hooking into " + gm.getDescription().getName());
            //registerCommands(gm);
            //registerPlaceholders(gm);
            registeredGameModes.add(gm);
        });
    }

    /**
     * @return the advManager
     */
    public AdvancementsManager getAdvManager() {
        return advManager;
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the registeredGameModes
     */
    public List<GameModeAddon> getRegisteredGameModes() {
        return registeredGameModes;
    }

    /**
     * Check if addon is active in game mode
     * @param gm Game Mode Addon
     * @return true if active, false if not
     */
    public boolean isRegisteredGameMode(GameModeAddon gm) {
        return registeredGameModes.contains(gm);
    }

    /**
     * Checks if addon is active in world
     * @param world world
     * @return true if active, false if not
     */
    public boolean isRegisteredGameModeWorld(World world) {
        return registeredGameModes.stream().map(GameModeAddon::getOverWorld).anyMatch(w -> Util.sameWorld(world, w));
    }

}
