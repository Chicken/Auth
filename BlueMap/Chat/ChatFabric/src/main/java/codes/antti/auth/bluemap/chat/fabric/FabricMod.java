package codes.antti.auth.bluemap.chat.fabric;

import codes.antti.auth.bluemap.chat.ChatCore;
import codes.antti.auth.bluemap.chat.ChatPlatformAdapter;
import codes.antti.auth.common.fabric.FabricConfig;
import codes.antti.auth.common.fabric.LogWrapper;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

public class FabricMod implements DedicatedServerModInitializer, ChatPlatformAdapter {
    private static final Logger LOGGER = new LogWrapper(LoggerFactory.getLogger("BlueMap-Chat"), "[BlueMap-Chat]");
    private final ChatCore core = new ChatCore();
    private FabricConfig config;
    private MinecraftServer server;

    @Override
    public void onInitializeServer() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("bluemap-chat/config.yml");
        try {
            this.config = new FabricConfig(configFile, "assets/bluemap-chat/config.yml");
        } catch (IOException e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
            return;
        }

        BlueMapAPI.onEnable(api -> {
            core.onBlueMapEnabled(this, api);
        });
        BlueMapAPI.onDisable(api -> core.onBlueMapDisabled());

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            this.server = srv;
            core.onStart();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> core.onStop());

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
            core.onChat(sender.getUUID(), sender.getName().getString(), message.decoratedContent().getString())
        );

        ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) -> {
            if (!params.chatType().is(ChatType.SAY_COMMAND)) return;
            String text = message.decoratedContent().getString();
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                core.onPlayerSayCommand(player.getUUID(), player.getName().getString(), text);
            } else {
                core.onSayCommand(text);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                String deathMsg = player.getCombatTracker().getDeathMessage().getString();
                core.onDeath(player.getUUID(), player.getName().getString(), deathMsg);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) ->
            core.onJoin(handler.player.getUUID(), handler.player.getName().getString())
        );

        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) ->
            core.onQuit(handler.player.getUUID(), handler.player.getName().getString())
        );

        getLogger().info("BlueMap Chat enabled");
    }

    // ChatPlatformAdapter
    @Override public String getIp() { return config.getString("ip", "127.0.0.1"); }
    @Override public int getPort() { return config.getInt("port", 8800); }
    @Override public boolean isReadAuth() { return config.getBoolean("read-auth", false); }
    @Override public boolean isReadOnly() { return config.getBoolean("read-only", false); }
    @Override public int getMaxMessageCount() { return config.getInt("max-message-count", 100); }
    @Override public String getWebChatPrefix() { return config.getString("web-chat-prefix", "[web]"); }
    @Override public String getAuthPath() { return config.getString("auth-path", "/authentication-outpost/"); }
    @Override public boolean isRegisterWebApp() { return config.getBoolean("register-web-app", true); }
    @Override public Logger getLogger() { return LOGGER; }
    @Override public void broadcastToServer(String formattedMessage) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(formattedMessage), false);
        }
    }
}
