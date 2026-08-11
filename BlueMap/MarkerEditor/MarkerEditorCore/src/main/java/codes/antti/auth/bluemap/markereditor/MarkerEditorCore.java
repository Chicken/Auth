package codes.antti.auth.bluemap.markereditor;

import codes.antti.auth.bluemap.markereditor.MarkerEditorDatabase.StoredSet;
import codes.antti.auth.common.database.Database;
import codes.antti.auth.common.database.DatabaseFactory;
import codes.antti.auth.common.http.HTTPError;
import codes.antti.auth.common.http.Request;
import codes.antti.auth.common.http.WebServer;
import com.google.gson.JsonObject;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import net.luckperms.api.model.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

import static codes.antti.auth.bluemap.markereditor.MarkerModels.*;

public final class MarkerEditorCore {
    public static final String GLOBAL_PERMISSION = "bluemap.markereditor.global";

    private static final String ROUTE_PREFIX = "/addons/marker-editor/";
    private static final String OWNED_SET_PREFIX = "marker-editor:global:";
    private static final int MAX_REQUEST_BYTES = 256 * 1024;

    private final Object blueMapLock = new Object();
    private @Nullable MarkerEditorPlatformAdapter adapter;
    private @Nullable MarkerEditorDatabase database;
    private @Nullable WebServer http;
    private volatile @Nullable BlueMapAPI blueMap;

    public void onStart(@NotNull MarkerEditorPlatformAdapter adapter) {
        if (this.adapter != null) throw new IllegalStateException("MarkerEditorCore is already started");
        this.adapter = adapter;

        Database rawDatabase = null;
        try {
            rawDatabase = DatabaseFactory.open(
                adapter.getDatabaseUrl(), adapter.getDataPath(), adapter.getDriverJar(),
                adapter.getDriverClass(), adapter.getLogger()
            );
            this.database = new MarkerEditorDatabase(rawDatabase);
        } catch (Exception exception) {
            if (rawDatabase != null) rawDatabase.close();
            adapter.getLogger().log(Level.SEVERE, "Failed to initialize MarkerEditor database", exception);
            return;
        }

        try {
            this.http = new WebServer(adapter.getIp(), adapter.getPort());
            this.http.route(ROUTE_PREFIX + "*", this::handleRequest, "GET", "POST", "PUT", "DELETE");
            this.http.start();
            adapter.getLogger().info("MarkerEditor webserver bound to " + this.http.getAddress());
        } catch (IOException exception) {
            adapter.getLogger().log(Level.SEVERE, "Failed to create MarkerEditor HTTP server", exception);
            this.database.close();
            this.database = null;
        }
    }

    public void onBlueMapEnabled(@NotNull BlueMapAPI api) {
        MarkerEditorPlatformAdapter currentAdapter = requireStartedAdapter();
        this.blueMap = api;

        if (currentAdapter.isRegisterWebApp()) {
            api.getWebApp().registerScript("assets/bluemap-marker-editor/marker-editor.js");
            api.getWebApp().registerStyle("assets/bluemap-marker-editor/marker-editor.css");
            try {
                copyWebResource(api, "marker-editor.js");
                copyWebResource(api, "marker-editor.css");
            } catch (IOException exception) {
                currentAdapter.getLogger().log(Level.SEVERE, "Failed to copy MarkerEditor web assets", exception);
            }
        }

        synchronized (blueMapLock) {
            for (BlueMapMap map : api.getMaps()) {
                try {
                    reconcileGlobalMap(map.getId());
                } catch (Exception exception) {
                    currentAdapter.getLogger().log(Level.SEVERE, "Failed to load global markers for map " + map.getId(), exception);
                }
            }
        }
    }

    public void onBlueMapDisabled() {
        BlueMapAPI api = this.blueMap;
        if (api == null) return;
        synchronized (blueMapLock) {
            for (BlueMapMap map : api.getMaps()) removeOwnedSets(map);
            this.blueMap = null;
        }
    }

    public void onStop() {
        onBlueMapDisabled();
        if (this.http != null) this.http.close();
        if (this.database != null) this.database.close();
        this.http = null;
        this.database = null;
        this.adapter = null;
    }

    private void handleRequest(Request request) throws IOException {
        try {
            dispatch(request);
        } catch (ApiException exception) {
            respondError(request, exception.status, exception.code, exception.getMessage(), exception.field);
        } catch (Exception exception) {
            requireStartedAdapter().getLogger().log(Level.SEVERE, "MarkerEditor request failed", exception);
            respondError(request, 500, "internal_error", "Internal server error", null);
        }
    }

    private void dispatch(Request request) throws Exception {
        String path = request.getPath();
        if (!path.startsWith(ROUTE_PREFIX)) throw new ApiException(404, "not_found", "Route not found");
        String remainder = path.substring(ROUTE_PREFIX.length());
        String[] parts = remainder.split("/", -1);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty())
            throw new ApiException(404, "not_found", "Route not found");

        String mapId = parts[0];
        requireMap(mapId);
        switch (request.getMethod()) {
            case "GET" -> handleGet(request, mapId, parts);
            case "POST" -> handlePost(request, mapId, parts);
            case "PUT" -> handlePut(request, mapId, parts);
            case "DELETE" -> handleDelete(request, mapId, parts);
            default -> throw new ApiException(405, "method_not_allowed", "Method not allowed");
        }
    }

    private void handleGet(Request request, String mapId, String[] parts) throws Exception {
        if (parts.length != 2) throw new ApiException(404, "not_found", "Route not found");
        AuthIdentity identity = readIdentity(request);
        switch (parts[1]) {
            case "state" -> getState(request, mapId, identity);
            case "private-markers" -> getPrivateMarkers(request, mapId, requireLogin(identity));
            default -> throw new ApiException(404, "not_found", "Route not found");
        }
    }

    private void handlePost(Request request, String mapId, String[] parts) throws Exception {
        AuthIdentity identity = requireLogin(readIdentity(request));
        if (parts.length == 2 && "marker-sets".equals(parts[1])) {
            createSet(request, mapId, identity);
            return;
        }
        if (parts.length == 4 && "marker-sets".equals(parts[1]) && "markers".equals(parts[3])) {
            createMarker(request, mapId, parseUuid(parts[2]), identity);
            return;
        }
        throw new ApiException(404, "not_found", "Route not found");
    }

    private void handlePut(Request request, String mapId, String[] parts) throws Exception {
        AuthIdentity identity = requireLogin(readIdentity(request));
        if (parts.length == 3 && "marker-sets".equals(parts[1])) {
            updateSet(request, mapId, parseUuid(parts[2]), identity);
            return;
        }
        if (parts.length == 5 && "marker-sets".equals(parts[1]) && "markers".equals(parts[3])) {
            updateMarker(request, mapId, parseUuid(parts[2]), parseUuid(parts[4]), identity);
            return;
        }
        throw new ApiException(404, "not_found", "Route not found");
    }

    private void handleDelete(Request request, String mapId, String[] parts) throws Exception {
        AuthIdentity identity = requireLogin(readIdentity(request));
        if (parts.length == 3 && "marker-sets".equals(parts[1])) {
            deleteSet(request, mapId, parseUuid(parts[2]), identity);
            return;
        }
        if (parts.length == 5 && "marker-sets".equals(parts[1]) && "markers".equals(parts[3])) {
            deleteMarker(request, mapId, parseUuid(parts[2]), parseUuid(parts[4]), identity);
            return;
        }
        throw new ApiException(404, "not_found", "Route not found");
    }

    private void getState(Request request, String mapId, AuthIdentity identity) throws Exception {
        MarkerEditorPlatformAdapter currentAdapter = requireStartedAdapter();
        if (!identity.loggedIn) {
            request.setHeader("Cache-Control", "private, no-store");
            request.json(new StateResponse(false, currentAdapter.getAuthPath(), null, null, false, List.of()));
            request.respond(200);
            return;
        }

        boolean canEditGlobal = hasGlobalPermission(identity.uuid);
        List<SetModel> sets = requireDatabase().listForState(mapId, identity.uuid).stream().map(StoredSet::model).toList();
        request.setHeader("Cache-Control", "private, no-store");
        request.json(new StateResponse(
            true, currentAdapter.getAuthPath(), identity.uuid.toString(), identity.username, canEditGlobal, sets
        ));
        request.respond(200);
    }

    private void getPrivateMarkers(Request request, String mapId, AuthIdentity identity) throws Exception {
        Map<String, MarkerSet> markerSets = new LinkedHashMap<>();
        for (StoredSet stored : requireDatabase().listPrivate(mapId, identity.uuid)) {
            markerSets.put(privateSetRenderId(identity.uuid, stored.id()), buildMarkerSet(stored.model()));
        }
        request.setHeader("Cache-Control", "private, no-store");
        request.setBody(serializeMarkerSets(markerSets), "application/json; charset=utf-8");
        request.respond(200);
    }

    private void createSet(Request request, String mapId, AuthIdentity identity) throws Exception {
        SetInput input = body(request, SetInput.class);
        Scope scope = MarkerCodec.normalizeSet(input, null);
        if (scope == Scope.GLOBAL) requireGlobalPermission(identity.uuid);
        StoredSet created = requireDatabase().createSet(
            mapId, scope, scope == Scope.PRIVATE ? identity.uuid : null, identity.uuid, input
        );
        if (scope == Scope.GLOBAL) reconcileGlobalMap(mapId);
        request.json(created.model());
        request.respond(201);
    }

    private void updateSet(Request request, String mapId, UUID setId, AuthIdentity identity) throws Exception {
        StoredSet stored = requireAccessibleSet(mapId, setId, identity);
        SetInput input = body(request, SetInput.class);
        MarkerCodec.normalizeSet(input, stored.scope());
        StoredSet updated = requireDatabase().updateSet(stored, input);
        if (stored.scope() == Scope.GLOBAL) reconcileGlobalMap(mapId);
        request.json(updated.model());
        request.respond(200);
    }

    private void deleteSet(Request request, String mapId, UUID setId, AuthIdentity identity) throws Exception {
        StoredSet stored = requireAccessibleSet(mapId, setId, identity);
        requireDatabase().deleteSet(stored);
        if (stored.scope() == Scope.GLOBAL) reconcileGlobalMap(mapId);
        request.respond(204);
    }

    private void createMarker(Request request, String mapId, UUID setId, AuthIdentity identity) throws Exception {
        StoredSet stored = requireAccessibleSet(mapId, setId, identity);
        MarkerData marker = MarkerCodec.normalizeMarker(body(request, MarkerData.class));
        MarkerModel created = requireDatabase().createMarker(stored, marker);
        if (stored.scope() == Scope.GLOBAL) reconcileGlobalMap(mapId);
        request.json(created);
        request.respond(201);
    }

    private void updateMarker(Request request, String mapId, UUID setId, UUID markerId, AuthIdentity identity) throws Exception {
        StoredSet stored = requireAccessibleSet(mapId, setId, identity);
        MarkerData marker = MarkerCodec.normalizeMarker(body(request, MarkerData.class));
        MarkerModel updated = requireDatabase().updateMarker(stored, markerId, marker);
        if (stored.scope() == Scope.GLOBAL) reconcileGlobalMap(mapId);
        request.json(updated);
        request.respond(200);
    }

    private void deleteMarker(Request request, String mapId, UUID setId, UUID markerId, AuthIdentity identity) throws Exception {
        StoredSet stored = requireAccessibleSet(mapId, setId, identity);
        requireDatabase().deleteMarker(stored, markerId);
        if (stored.scope() == Scope.GLOBAL) reconcileGlobalMap(mapId);
        request.respond(204);
    }

    private StoredSet requireAccessibleSet(String mapId, UUID setId, AuthIdentity identity) throws Exception {
        StoredSet stored = requireDatabase().findSet(mapId, setId);
        if (stored == null) throw new ApiException(404, "not_found", "Marker set not found");
        if (stored.scope() == Scope.PRIVATE) {
            if (!identity.uuid.equals(stored.owner())) throw new ApiException(404, "not_found", "Marker set not found");
        } else {
            requireGlobalPermission(identity.uuid);
        }
        return stored;
    }

    private void reconcileGlobalMap(String mapId) throws SQLException, ApiException {
        synchronized (blueMapLock) {
            BlueMapMap map = requireMap(mapId);
            removeOwnedSets(map);
            for (StoredSet stored : requireDatabase().listGlobal(mapId)) {
                map.getMarkerSets().put(globalSetRenderId(stored.id()), buildMarkerSet(stored.model()));
            }
        }
    }

    private static MarkerSet buildMarkerSet(SetModel model) {
        MarkerSet set = MarkerSet.builder()
            .label(model.label)
            .toggleable(model.toggleable)
            .defaultHidden(model.defaultHidden)
            .sorting(model.sorting)
            .build();
        for (MarkerModel marker : model.markers) {
            set.put(markerRenderId(UUID.fromString(marker.id)), MarkerCodec.buildMarker(marker));
        }
        return set;
    }

    private static void removeOwnedSets(BlueMapMap map) {
        map.getMarkerSets().keySet().removeIf(key -> key.startsWith(OWNED_SET_PREFIX));
    }

    private static String globalSetRenderId(UUID setId) {
        return OWNED_SET_PREFIX + setId;
    }

    private static String privateSetRenderId(UUID owner, UUID setId) {
        return "marker-editor:private:" + owner + ":" + setId;
    }

    private static String markerRenderId(UUID markerId) {
        return "marker-editor:marker:" + markerId;
    }

    private static String serializeMarkerSets(Map<String, MarkerSet> markerSets) throws ReflectiveOperationException {
        // MarkerGson belongs to BlueMap's classloader. Reflection keeps Shadow from rewriting its Gson field type.
        Object markerGson = MarkerGson.class.getField("INSTANCE").get(null);
        return (String) markerGson.getClass().getMethod("toJson", Object.class).invoke(markerGson, markerSets);
    }

    private boolean hasGlobalPermission(UUID uuid) throws ApiException {
        try {
            User user = requireStartedAdapter().getLuckPerms().getUserManager().getUser(uuid);
            if (user == null) user = requireStartedAdapter().getLuckPerms().getUserManager().loadUser(uuid).join();
            return user != null && user.getCachedData().getPermissionData().checkPermission(GLOBAL_PERMISSION).asBoolean();
        } catch (CompletionException exception) {
            throw new ApiException(500, "permission_error", "Failed to load player permissions");
        }
    }

    private void requireGlobalPermission(UUID uuid) throws ApiException {
        if (!hasGlobalPermission(uuid))
            throw new ApiException(403, "forbidden", "The " + GLOBAL_PERMISSION + " permission is required");
    }

    private AuthIdentity readIdentity(Request request) throws ApiException {
        String loggedIn = request.getHeader("x-minecraft-loggedin");
        if (loggedIn == null) throw new ApiException(400, "invalid_auth_headers", "Missing authentication headers");
        if ("false".equals(loggedIn)) return new AuthIdentity(false, null, null);
        if (!"true".equals(loggedIn))
            throw new ApiException(400, "invalid_auth_headers", "Invalid x-minecraft-loggedin header");

        String uuidValue = request.getHeader("x-minecraft-uuid");
        String username = request.getHeader("x-minecraft-username");
        if (uuidValue == null || username == null)
            throw new ApiException(400, "invalid_auth_headers", "Incomplete authentication headers");
        try {
            return new AuthIdentity(true, UUID.fromString(uuidValue), username);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, "invalid_auth_headers", "Invalid player UUID header");
        }
    }

    private static AuthIdentity requireLogin(AuthIdentity identity) throws ApiException {
        if (!identity.loggedIn) throw new ApiException(401, "not_authenticated", "Authentication is required");
        return identity;
    }

    private BlueMapMap requireMap(String mapId) throws ApiException {
        BlueMapAPI api = this.blueMap;
        if (api == null) throw new ApiException(503, "bluemap_unavailable", "BlueMap is not available");
        return api.getMap(mapId).orElseThrow(() -> new ApiException(404, "map_not_found", "Map not found"));
    }

    private static UUID parseUuid(String value) throws ApiException {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(404, "not_found", "Resource not found");
        }
    }

    private static <T> T body(Request request, Class<T> type) throws ApiException {
        try {
            return request.getBodyAs(type, MAX_REQUEST_BYTES);
        } catch (HTTPError exception) {
            String code = exception.getStatusCode() == 413 ? "body_too_large" : "invalid_json";
            throw new ApiException(exception.getStatusCode(), code, exception.getMessage());
        }
    }

    private static void respondError(Request request, int status, String code, String message, @Nullable String field)
        throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        if (field != null) error.addProperty("field", field);
        JsonObject response = new JsonObject();
        response.add("error", error);
        request.json(response);
        request.respond(status);
    }

    private void copyWebResource(BlueMapAPI api, String name) throws IOException {
        String resourcePath = "/assets/bluemap-marker-editor/" + name;
        Path target = api.getWebApp().getWebRoot().resolve("assets/bluemap-marker-editor").resolve(name);
        Files.createDirectories(target.getParent());
        InputStream resource = MarkerEditorCore.class.getResourceAsStream(resourcePath);
        if (resource == null) throw new IOException(resourcePath + " not found in classpath");
        try (InputStream input = resource;
             OutputStream output = Files.newOutputStream(
                 target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
             )) {
            input.transferTo(output);
        }
    }

    private MarkerEditorPlatformAdapter requireStartedAdapter() {
        return Objects.requireNonNull(this.adapter, "MarkerEditorCore is not started");
    }

    private MarkerEditorDatabase requireDatabase() {
        return Objects.requireNonNull(this.database, "MarkerEditor database is not available");
    }

    private record AuthIdentity(boolean loggedIn, @Nullable UUID uuid, @Nullable String username) {}
}
