package world.bentobox.advancements;

import org.bukkit.World;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

/**
 * Handles placeholders
 * @author tastybento
 *
 */
public class PlaceholdersManager {

    private final Advancements addon;

    public PlaceholdersManager(Advancements addon) {
        this.addon = addon;
    }

    /**
     * Get the advancement count of the user's island in the world they are in
     * @param user owner or team member
     * @return string of advancement count
     */
    public String getCount(User user) {
        if (user == null || user.getUniqueId() == null || user.getWorld() == null) {
            return "";
        }
        World world = Util.getWorld(user.getWorld());
        if (world == null || !addon.isRegisteredGameModeWorld(world)) {
            return "";
        }
        Island i = addon.getIslands().getIsland(world, user);
        return i == null ? "" : String.valueOf(addon.getAdvManager().getIsland(i).getAdvancements().size());
    }

    /**
     * Get the advancement count of the island at the user's location
     * @param user user
     * @return string of advancement count
     */
    public String getCountByLocation(User user) {
        if (user == null || user.getUniqueId() == null || user.getLocation() == null
                || !addon.isRegisteredGameModeWorld(Util.getWorld(user.getWorld()))) {
            return "";
        }
        return addon.getIslands().getIslandAt(user.getLocation())
                .map(i -> String.valueOf(addon.getAdvManager().getIsland(i).getAdvancements().size())).orElse("");
    }

}
