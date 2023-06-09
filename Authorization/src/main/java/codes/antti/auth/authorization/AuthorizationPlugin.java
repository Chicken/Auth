package codes.antti.auth.authorization;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class AuthorizationPlugin extends JavaPlugin {
    AuthorizationWebServer server;
    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        File webRoot = getDataFolder().toPath().resolve("web").toFile();
        if (!webRoot.exists()) {
            boolean _ignored = webRoot.mkdirs();
        }
        if (!webRoot.toPath().resolve("unauthorized.html").toFile().exists()) this.saveResource("web/unauthorized.html", true);

        try {
            this.server = new AuthorizationWebServer(this);
        } catch (IOException e) {
            getLogger().severe("Failed to create HTTP server");
            e.printStackTrace();
        }

        if (this.server == null) return;

        getLogger().info("Ready to authorize");
    }

    @Override
    public void onDisable() {
        if (this.server != null) this.server.close();
        getLogger().info("Going down");
    }
}
