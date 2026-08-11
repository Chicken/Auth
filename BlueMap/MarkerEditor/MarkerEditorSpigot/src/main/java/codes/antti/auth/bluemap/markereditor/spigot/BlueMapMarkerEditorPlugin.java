package codes.antti.auth.bluemap.markereditor.spigot;

import codes.antti.auth.bluemap.markereditor.MarkerEditorCore;
import codes.antti.auth.bluemap.markereditor.MarkerEditorPlatformAdapter;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class BlueMapMarkerEditorPlugin extends JavaPlugin implements MarkerEditorPlatformAdapter {
    private final MarkerEditorCore core = new MarkerEditorCore();
    private final Consumer<BlueMapAPI> blueMapEnableListener = core::onBlueMapEnabled;
    private final Consumer<BlueMapAPI> blueMapDisableListener = api -> core.onBlueMapDisabled();

    @Override
    public void onLoad() {
        saveDefaultConfig();
        core.onStart(this);
        BlueMapAPI.onEnable(blueMapEnableListener);
        BlueMapAPI.onDisable(blueMapDisableListener);
    }

    @Override
    public void onEnable() {
        getLogger().info("BlueMap MarkerEditor enabled");
    }

    @Override
    public void onDisable() {
        BlueMapAPI.unregisterListener(blueMapEnableListener);
        BlueMapAPI.unregisterListener(blueMapDisableListener);
        core.onBlueMapDisabled();
        core.onStop();
        getLogger().info("BlueMap MarkerEditor disabled");
    }

    @Override public Path getDataPath() { return getDataFolder().toPath(); }
    @Override public String getIp() { return getConfig().getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return getConfig().getInt("port", 8700); }
    @Override public String getDatabaseUrl() { return getConfig().getString("database_url", "jdbc:sqlite:{data}/markers.db"); }
    @Override public String getDriverJar() { return getConfig().getString("driver_jar", null); }
    @Override public String getDriverClass() { return getConfig().getString("driver_class", null); }
    @Override public String getAuthPath() { return getConfig().getString("auth-path", "/authentication-outpost/"); }
    @Override public boolean isRegisterWebApp() { return getConfig().getBoolean("register-web-app", true); }
    @Override public Logger getLogger() { return super.getLogger(); }
    @Override public LuckPerms getLuckPerms() { return LuckPermsProvider.get(); }
}
