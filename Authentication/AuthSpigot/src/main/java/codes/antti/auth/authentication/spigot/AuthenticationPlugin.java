package codes.antti.auth.authentication.spigot;

import codes.antti.auth.authentication.AuthPlatformAdapter;
import codes.antti.auth.authentication.AuthenticationCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;

public final class AuthenticationPlugin extends JavaPlugin implements CommandExecutor, AuthPlatformAdapter {
    private final AuthenticationCore core = new AuthenticationCore();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        core.init(this);
        Objects.requireNonNull(getCommand("auth")).setExecutor(this);
    }

    @Override
    public void onDisable() {
        core.shutdown();
        getLogger().info("Going down");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }

        Player player = (Player) sender;
        try {
            String msg = core.handleAuthCommand(player.getUniqueId(), player.getName(), args[0]);
            if (msg != null) {
                sender.sendMessage(msg);
            } else {
                sender.sendMessage("Verification failed, check that you typed the authentication token correctly");
            }
        } catch (SQLException e) {
            sender.sendMessage("Something went wrong");
            getLogger().severe("Failed to verify player session");
            e.printStackTrace();
        }

        return true;
    }

    // AuthPlatformAdapter
    @Override public Path getDataPath() { return getDataFolder().toPath(); }
    @Override public String getIp() { return getConfig().getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return getConfig().getInt("port", 8200); }
    @Override public boolean isWebServerEnabled() { return getConfig().getBoolean("webserver_enabled", true); }
    @Override public boolean isIpLock() { return getConfig().getBoolean("ip_lock", true); }
    @Override public boolean isOptionalAuthentication() { return getConfig().getBoolean("optional_authentication", false); }
    @Override public int getSessionLengthDays() { return getConfig().getInt("session_length_days", 31); }
    @Override public int getAuthTokenLength() { return getConfig().getInt("auth_token_length", 7); }
    @Override public int getUserMaxSessions() { return getConfig().getInt("user_max_sessions", 3); }
    @Override public String getDatabaseUrl() { return getConfig().getString("database_url", "jdbc:sqlite:{data}/db.sqlite"); }
    @Override public String getDriverJar() { return getConfig().getString("driver_jar", null); }
    @Override public String getDriverClass() { return getConfig().getString("driver_class", null); }
    @Override public Logger getLogger() { return super.getLogger(); }
}
