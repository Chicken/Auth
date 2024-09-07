package codes.antti.auth.bluemap.integration;

import codes.antti.auth.common.http.WebServer;
import com.google.gson.JsonObject;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BlueMapAuthIntegrationPlugin extends JavaPlugin {
	private Configuration config;
	private WebServer http;
	@Override
	public void onEnable() {

		BlueMapAPI.onEnable(api -> {
			reloadConfig();
			saveDefaultConfig();
			this.config = getConfig();

			try {
				this.http = new WebServer(Objects.requireNonNull(config.getString("ip", "0.0.0.0")), config.getInt("port", 8400));
			} catch (IOException ex) {
				getLogger().severe("Failed to create HTTP server");
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
			getLogger().info("Webserver bound to " + this.http.getAddress());

			try {
				String integration = new String(Objects.requireNonNull(getResource("bluemap-integration.js")).readAllBytes(), StandardCharsets.UTF_8)
						.replaceAll("\\{\\{auth-path}}", Objects.requireNonNull(config.getString("auth-path", "/authentication-outpost/")));
				Path integrationPath = api.getWebApp().getWebRoot().resolve("assets/auth-integration.js");
				Files.createDirectories(integrationPath.getParent());
			    OutputStream out = Files.newOutputStream(integrationPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				out.write(integration.getBytes(StandardCharsets.UTF_8));
				out.close();
				api.getWebApp().registerScript("assets/auth-integration.js");
			} catch (IOException ex) {
				getLogger().severe("Couldn't move integration resources to BlueMap!");
				ex.printStackTrace();
			}
		});
		BlueMapAPI.onDisable(api -> {
			if (this.http != null) this.http.close();
		});
		getLogger().info("BlueMap Auth Integration enabled");
	}

	@Override
	public void onDisable() {
		getLogger().info("BlueMap Auth Integration disabled");
	}
}
