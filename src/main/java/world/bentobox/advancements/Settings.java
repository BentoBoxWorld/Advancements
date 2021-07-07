package world.bentobox.advancements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import world.bentobox.bentobox.api.configuration.ConfigComment;
import world.bentobox.bentobox.api.configuration.ConfigEntry;
import world.bentobox.bentobox.api.configuration.StoreAt;

/**
 * All the settings are here
 * @author Tastybento
 */
@StoreAt(filename="config.yml", path="addons/Advancements") // Explicitly call out what name this should have.
@ConfigComment("Advancements Configuration [version]")
public class Settings {
    @ConfigComment("")
    @ConfigComment("Disabled Game Mode Addons")
    @ConfigComment("Level will NOT hook into these game mode addons.")
    @ConfigEntry(path = "disabled-game-modes")
    private List<String> gameModes = Collections.singletonList("Boxed");

    @ConfigComment("")
    @ConfigComment("Announce advancements. We recommend you set the game rule `/gamerule announceAdvancements false`")
    @ConfigComment("but that blocks all new advancement announcements. This setting tells Boxed to broadcast new advancements.")
    @ConfigEntry(path = "broadcast-advancements")
    private boolean broadcastAdvancements;

    @ConfigComment("")
    @ConfigComment("Reset advancements.")
    @ConfigEntry(path = "reset.on-join.reset-advancements")
    private boolean onJoinResetAdvancements = true;
    @ConfigComment("Grant these advancements")
    @ConfigEntry(path = "reset.on-join.grant-advancements")
    private List<String> onJoinGrantAdvancements = new ArrayList<>();
    @ConfigComment("Reset advancements.")
    @ConfigEntry(path = "reset.on-leave.reset-advancements")
    private boolean onLeaveResetAdvancements = false;
    @ConfigComment("Grant these advancements")
    @ConfigEntry(path = "reset.on-leave.grant-advancements")
    private List<String> onLeaveGrantAdvancements = new ArrayList<>();
    /**
     * @return the gameModes
     */
    public List<String> getGameModes() {
        return gameModes;
    }
    /**
     * @param gameModes the gameModes to set
     */
    public void setGameModes(List<String> gameModes) {
        this.gameModes = gameModes;
    }
    /**
     * @return the broadcastAdvancements
     */
    public boolean isBroadcastAdvancements() {
        return broadcastAdvancements;
    }
    /**
     * @param broadcastAdvancements the broadcastAdvancements to set
     */
    public void setBroadcastAdvancements(boolean broadcastAdvancements) {
        this.broadcastAdvancements = broadcastAdvancements;
    }
    /**
     * @return the onJoinResetAdvancements
     */
    public boolean isOnJoinResetAdvancements() {
        return onJoinResetAdvancements;
    }
    /**
     * @param onJoinResetAdvancements the onJoinResetAdvancements to set
     */
    public void setOnJoinResetAdvancements(boolean onJoinResetAdvancements) {
        this.onJoinResetAdvancements = onJoinResetAdvancements;
    }
    /**
     * @return the onJoinGrantAdvancements
     */
    public List<String> getOnJoinGrantAdvancements() {
        return onJoinGrantAdvancements;
    }
    /**
     * @param onJoinGrantAdvancements the onJoinGrantAdvancements to set
     */
    public void setOnJoinGrantAdvancements(List<String> onJoinGrantAdvancements) {
        this.onJoinGrantAdvancements = onJoinGrantAdvancements;
    }
    /**
     * @return the onLeaveResetAdvancements
     */
    public boolean isOnLeaveResetAdvancements() {
        return onLeaveResetAdvancements;
    }
    /**
     * @param onLeaveResetAdvancements the onLeaveResetAdvancements to set
     */
    public void setOnLeaveResetAdvancements(boolean onLeaveResetAdvancements) {
        this.onLeaveResetAdvancements = onLeaveResetAdvancements;
    }
    /**
     * @return the onLeaveGrantAdvancements
     */
    public List<String> getOnLeaveGrantAdvancements() {
        return onLeaveGrantAdvancements;
    }
    /**
     * @param onLeaveGrantAdvancements the onLeaveGrantAdvancements to set
     */
    public void setOnLeaveGrantAdvancements(List<String> onLeaveGrantAdvancements) {
        this.onLeaveGrantAdvancements = onLeaveGrantAdvancements;
    }


}
