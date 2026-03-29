package codes.antti.auth.authentication.fabric;

import codes.antti.auth.authentication.AuthPlatformAdapter;
import codes.antti.auth.authentication.AuthenticationCore;
import codes.antti.auth.common.fabric.FabricConfig;
import codes.antti.auth.common.fabric.LogWrapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Logger;

public class FabricMod implements DedicatedServerModInitializer, AuthPlatformAdapter {
    private static final Logger LOGGER = new LogWrapper(LoggerFactory.getLogger("Authentication"), "[Authentication]");
    private final AuthenticationCore core = new AuthenticationCore();
    private FabricConfig config;

    @Override
    public void onInitializeServer() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("authentication/config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/authentication/config.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
            return;
        }

        core.init(this);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("auth")
                .then(Commands.argument("token", StringArgumentType.string())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player;
                        try {
                            player = source.getPlayerOrException();
                        } catch (Exception e) {
                            source.sendSuccess(() -> Component.literal("This command can only be used by players"), false);
                            return 0;
                        }
                        String token = StringArgumentType.getString(ctx, "token");
                        try {
                            String msg = core.handleAuthCommand(player.getUUID(), player.getName().getString(), token);
                            if (msg != null) {
                                source.sendSuccess(() -> Component.literal(msg), false);
                                return 1;
                            } else {
                                source.sendSuccess(() -> Component.literal("Verification failed, check that you typed the authentication token correctly"), false);
                                return 0;
                            }
                        } catch (SQLException e) {
                            getLogger().severe("Failed to verify player session: " + e.getMessage());
                            source.sendSuccess(() -> Component.literal("Something went wrong"), false);
                            return 0;
                        }
                    })
                )
            )
        );

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> core.shutdown());
    }

    // AuthPlatformAdapter
    @Override public Path getDataPath() { return FabricLoader.getInstance().getConfigDir().resolve("authentication"); }
    @Override public String getIp() { return config.getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return config.getInt("port", 8200); }
    @Override public boolean isWebServerEnabled() { return config.getBoolean("webserver_enabled", true); }
    @Override public boolean isIpLock() { return config.getBoolean("ip_lock", true); }
    @Override public boolean isOptionalAuthentication() { return config.getBoolean("optional_authentication", false); }
    @Override public int getSessionLengthDays() { return config.getInt("session_length_days", 31); }
    @Override public int getAuthTokenLength() { return config.getInt("auth_token_length", 7); }
    @Override public int getUserMaxSessions() { return config.getInt("user_max_sessions", 3); }
    @Override public String getDatabaseUrl() { return config.getString("database_url", "jdbc:sqlite:{data}/db.sqlite"); }
    @Override public String getDriverJar() { return config.getString("driver_jar", null); }
    @Override public String getDriverClass() { return config.getString("driver_class", null); }
    @Override public java.util.logging.Logger getLogger() { return LOGGER; }
}
