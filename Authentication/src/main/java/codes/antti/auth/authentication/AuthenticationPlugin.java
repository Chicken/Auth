package codes.antti.auth.authentication;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;

public final class AuthenticationPlugin extends JavaPlugin implements CommandExecutor {
    AuthenticationWebServer server;
    AuthenticationDatabase db;
    private int userMaxSessions;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.userMaxSessions = this.getConfig().getInt("user_max_sessions", 3);
        File webRoot = getDataFolder().toPath().resolve("web").toFile();
        if (!webRoot.exists()) {
            boolean _ignored = webRoot.mkdirs();
        }
        if (!webRoot.toPath().resolve("login.html").toFile().exists()) this.saveResource("web/login.html", true);

        try {
            this.db = new AuthenticationDatabase(this);
        } catch (SQLException e) {
            getLogger().severe("Failed to open database");
            e.printStackTrace();
        }

        if (this.db == null) return;

        try {
            this.server = new AuthenticationWebServer(this);
        } catch (IOException e) {
            getLogger().severe("Failed to create HTTP server");
            e.printStackTrace();
        }

        if (this.server == null) return;

        Objects.requireNonNull(getCommand("auth")).setExecutor(this);

        getLogger().info("Ready to authenticate");
    }

    @Override
    public void onDisable() {
        if (this.server != null) this.server.close();
        if (this.db != null) this.db.close();
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
        String authToken = args[0];

        try {
            if (this.db.verifySession(authToken, player.getUniqueId().toString(), player.getName())) {
                int sessionsCount = this.db.getSessionsCount(player.getUniqueId().toString());
                if (sessionsCount > this.userMaxSessions) this.db.deleteOldestSessions(player.getUniqueId().toString(), sessionsCount - this.userMaxSessions);
                sender.sendMessage("Verification successful");
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
}
