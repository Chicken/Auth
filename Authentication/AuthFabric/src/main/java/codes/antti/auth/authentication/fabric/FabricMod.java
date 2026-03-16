package codes.antti.auth.authentication.fabric;

import codes.antti.auth.authentication.AuthPlatformAdapter;
import codes.antti.auth.authentication.AuthenticationCore;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.logging.Logger;

public class FabricMod implements DedicatedServerModInitializer, AuthPlatformAdapter {
    private static final Logger LOGGER = Logger.getLogger("Authentication");
    private final AuthenticationCore core = new AuthenticationCore();
    private FabricConfig config;

    @Override
    public void onInitializeServer() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("authentication/config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/authentication/config.yml");
        } catch (IOException e) {
            LOGGER.severe("Failed to load config: " + e.getMessage());
            return;
        }

        core.init(this);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("auth")
                .then(CommandManager.argument("token", StringArgumentType.string())
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ServerPlayerEntity player;
                        try {
                            player = source.getPlayerOrThrow();
                        } catch (Exception e) {
                            source.sendFeedback(() -> Text.literal("This command can only be used by players"), false);
                            return 0;
                        }
                        String token = StringArgumentType.getString(ctx, "token");
                        try {
                            String msg = core.handleAuthCommand(player.getUuid(), player.getName().getString(), token);
                            if (msg != null) {
                                source.sendFeedback(() -> Text.literal(msg), false);
                                return 1;
                            } else {
                                source.sendFeedback(() -> Text.literal("Verification failed, check that you typed the authentication token correctly"), false);
                                return 0;
                            }
                        } catch (SQLException e) {
                            LOGGER.severe("Failed to verify player session: " + e.getMessage());
                            source.sendFeedback(() -> Text.literal("Something went wrong"), false);
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
    @Override public boolean isIpLock() { return config.getBoolean("ip_lock", true); }
    @Override public boolean isOptionalAuthentication() { return config.getBoolean("optional_authentication", false); }
    @Override public int getSessionLengthDays() { return config.getInt("session_length_days", 31); }
    @Override public int getAuthTokenLength() { return config.getInt("auth_token_length", 7); }
    @Override public int getUserMaxSessions() { return config.getInt("user_max_sessions", 3); }
    @Override public Logger getLogger() { return LOGGER; }
}
