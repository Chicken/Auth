package codes.antti.auth.bluemap.integration;

import codes.antti.auth.common.http.WebServer;
import com.google.gson.JsonObject;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class IntegrationCore {
    private WebServer http;

    public void onBlueMapEnabled(@NotNull IntegrationPlatformAdapter adapter, @NotNull BlueMapAPI api) {
        try {
            this.http = new WebServer(adapter.getIp(), adapter.getPort());
        } catch (IOException ex) {
            adapter.getLogger().severe("Failed to create HTTP server");
            ex.printStackTrace();
            return;
        }

        this.http.get("/whoami", request -> {
            String loggedIn = request.getHeader("x-minecraft-loggedin");
            String uuid = request.getHeader("x-minecraft-uuid");
            String username = request.getHeader("x-minecraft-username");
            if (loggedIn == null || (loggedIn.equals("true") && (uuid == null || username == null))) {
                request.respond(400);
                return;
            }
            if (!loggedIn.equals("true")) {
                request.setBody("{}", "application/json");
                request.respond(200);
                return;
            }
            JsonObject response = new JsonObject();
            response.addProperty("uuid", uuid);
            response.addProperty("username", username);
            request.json(response);
            request.respond(200);
        });

        this.http.get("/playerhead/*", request -> {
            String loggedIn = request.getHeader("x-minecraft-loggedin");
            String uuid = request.getHeader("x-minecraft-uuid");
            if (loggedIn == null || (loggedIn.equals("true") && uuid == null)) {
                request.respond(400);
                return;
            }
            if (!loggedIn.equals("true")) {
                request.respond(400);
                return;
            }
            String[] parts = request.getPath().split("/");
            if (parts.length != 3) {
                request.respond(400);
                return;
            }
            String mapId = parts[2];
            Optional<BlueMapMap> map = api.getMap(mapId);
            if (map.isEmpty()) {
                request.respond(404);
                return;
            }
            request.redirect(301, "../../../" + BMSkin.getPlayerHeadIconAddress(api, UUID.fromString(uuid), map.get()));
        });

        this.http.start();
        adapter.getLogger().info("Webserver bound to " + this.http.getAddress());

        try {
            String integration;
            try (InputStream in = Objects.requireNonNull(
                IntegrationCore.class.getResourceAsStream("/assets/bluemap-auth/bluemap-integration.js"),
                "bluemap-integration.js not found in classpath"
            )) {
                integration = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            integration = integration.replaceAll("\\{\\{auth-path}}", adapter.getAuthPath());

            Path integrationPath = api.getWebApp().getWebRoot().resolve("assets/auth-integration.js");
            Files.createDirectories(integrationPath.getParent());
            try (OutputStream out = Files.newOutputStream(integrationPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                out.write(integration.getBytes(StandardCharsets.UTF_8));
            }
            api.getWebApp().registerScript("assets/auth-integration.js");
        } catch (IOException ex) {
            adapter.getLogger().severe("Couldn't move integration resources to BlueMap!");
            ex.printStackTrace();
        }
    }

    public void onBlueMapDisabled() {
        if (this.http != null) this.http.close();
    }
}
