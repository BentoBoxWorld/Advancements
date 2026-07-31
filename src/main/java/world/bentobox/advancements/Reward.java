package world.bentobox.advancements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.util.ItemParser;

/**
 * The reward given when an island completes an advancement. Rewards follow the same
 * model as the Challenges addon: items, experience, money (via Vault) and commands.
 * <p>
 * Parsed from a YAML section of the form:
 * <pre>
 * rewards:
 *   items:
 *   - GOLD_INGOT:1
 *   experience: 5
 *   money: 10.5
 *   commands:
 *   - give [player] diamond 1
 * </pre>
 *
 * @param items items given to the player; overflow is dropped at the player's feet
 * @param experience experience points given to the player
 * @param money money deposited via the economy plugin, if one is hooked
 * @param commands commands run for the player. [player] is replaced with the player's
 *                 name and the [SUDO] prefix makes the command run as the player.
 * @author tastybento
 */
public record Reward(List<ItemStack> items, int experience, double money, List<String> commands) {

    /**
     * A reward that gives nothing.
     */
    public static final Reward EMPTY = new Reward(Collections.emptyList(), 0, 0D, Collections.emptyList());

    /**
     * Parse a reward from a configuration section.
     * @param section rewards section, may be null
     * @param errorLogger sink for item-parsing error messages
     * @return parsed reward, or {@link #EMPTY} if the section is null or gives nothing
     */
    public static Reward fromSection(@Nullable ConfigurationSection section, Consumer<String> errorLogger) {
        if (section == null) {
            return EMPTY;
        }
        List<ItemStack> items = new ArrayList<>();
        for (String itemString : section.getStringList("items")) {
            ItemStack item = ItemParser.parse(itemString);
            if (item == null) {
                errorLogger.accept("Could not parse reward item '" + itemString + "' - skipping it.");
            } else {
                items.add(item);
            }
        }
        Reward r = new Reward(Collections.unmodifiableList(items),
                section.getInt("experience", 0),
                section.getDouble("money", 0D),
                List.copyOf(section.getStringList("commands")));
        return r.isEmpty() ? EMPTY : r;
    }

    /**
     * @return true if this reward gives nothing
     */
    public boolean isEmpty() {
        return items.isEmpty() && experience <= 0 && money <= 0D && commands.isEmpty();
    }
}
