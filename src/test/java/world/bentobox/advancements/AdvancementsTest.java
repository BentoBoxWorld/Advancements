package world.bentobox.advancements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.managers.AddonsManager;
import world.bentobox.bentobox.managers.CommandsManager;

/**
 * Tests for {@link Advancements}.
 */
class AdvancementsTest extends CommonTestSetup {

    private static final String CONFIG_YML =
            """
                    disabled-game-modes:
                    - Boxed
                    broadcast-advancements: false
                    deny-visitor-advancements: true
                    reset:
                      on-join:
                        reset-advancements: true
                        grant-advancements: []
                      on-leave:
                        reset-advancements: false
                        grant-advancements: []
                    """;

    private static final String ADVANCEMENTS_YML =
            """
                    settings:
                      default-root-increase: 0
                      unknown-advancement-increase: 1
                      unknown-recipe-increase: 0
                    advancements:
                      'adventure/bullseye': 5
                    """;

    @Mock
    private AddonsManager am;

    private Advancements addon;
    private MockedStatic<DatabaseSetup> mockDb;

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

        // CommandsManager
        CommandsManager cm = mock(CommandsManager.class);
        when(plugin.getCommandsManager()).thenReturn(cm);

        // AddonsManager
        when(plugin.getAddonsManager()).thenReturn(am);
        when(am.getGameModeAddons()).thenReturn(Collections.emptyList());

        // FlagsManager
        when(plugin.getFlagsManager()).thenReturn(fm);
        when(fm.getFlags()).thenReturn(Collections.emptyList());

        // Create addon with a JAR containing the resources it saves on load/enable
        addon = new Advancements();
        File jFile = new File("addon.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jFile))) {
            addJarEntry(jos, "config.yml", CONFIG_YML);
            addJarEntry(jos, "advancements.yml", ADVANCEMENTS_YML);
        }
        File dataFolder = new File("addons/Advancements");
        addon.setDataFolder(dataFolder);
        addon.setFile(jFile);
        AddonDescription desc = new AddonDescription.Builder("bentobox", "Advancements", "1.0.0")
                .description("test").authors("tastybento").build();
        addon.setDescription(desc);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (mockDb != null) {
            mockDb.closeOnDemand();
        }
        super.tearDown();
        new File("addon.jar").delete();
        deleteAll(new File("addons"));
    }

    private static void addJarEntry(JarOutputStream jos, String name, String content) throws Exception {
        JarEntry entry = new JarEntry(name);
        jos.putNextEntry(entry);
        jos.write(content.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }

    @Test
    void testGetSettingsNullBeforeLoad() {
        assertNull(addon.getSettings());
    }

    @Test
    void testOnLoad() {
        addon.onLoad();
        assertNotNull(addon.getSettings());
    }

    @Test
    void testOnLoadSettingsDefaults() {
        addon.onLoad();
        Settings s = addon.getSettings();
        assertNotNull(s);
        assertTrue(s.getGameModes().contains("Boxed"));
        assertFalse(s.isBroadcastAdvancements());
        assertTrue(s.isDenyVisitorAdvancements());
        assertTrue(s.isOnJoinResetAdvancements());
        assertFalse(s.isOnLeaveResetAdvancements());
        assertTrue(s.getOnJoinGrantAdvancements().isEmpty());
        assertTrue(s.getOnLeaveGrantAdvancements().isEmpty());
    }

    @Test
    void testOnEnable() {
        addon.onLoad();
        addon.onEnable();
        assertNotNull(addon.getAdvManager());
        assertTrue(addon.getRegisteredGameModes().isEmpty());
    }

    @Test
    void testOnEnableWithoutSettingsDoesNothing() {
        // onLoad not called - settings are null so onEnable must not create the manager
        addon.onEnable();
        assertNull(addon.getAdvManager());
    }

    @Test
    void testOnDisableBeforeEnable() {
        // Must not throw when the manager was never created
        addon.onDisable();
        assertNull(addon.getAdvManager());
    }

    @Test
    void testOnDisableAfterEnable() {
        addon.onLoad();
        addon.onEnable();
        addon.onDisable();
        assertNotNull(addon.getAdvManager());
    }

    @Test
    void testOnReload() {
        addon.onLoad();
        addon.onReload();
        assertNotNull(addon.getSettings());
    }

    @Test
    void testIsRegisteredGameModeWorldEmpty() {
        addon.onLoad();
        addon.onEnable();
        assertFalse(addon.isRegisteredGameModeWorld(world));
        assertFalse(addon.isRegisteredGameModeWorld(null));
    }

    @Test
    void testGetRegisteredGameModesEmptyList() {
        addon.onLoad();
        addon.onEnable();
        List<?> modes = addon.getRegisteredGameModes();
        assertNotNull(modes);
        assertTrue(modes.isEmpty());
    }
}
