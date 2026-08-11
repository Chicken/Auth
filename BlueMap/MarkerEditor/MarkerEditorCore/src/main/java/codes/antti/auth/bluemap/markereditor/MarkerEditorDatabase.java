package codes.antti.auth.bluemap.markereditor;

import codes.antti.auth.common.database.Database;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static codes.antti.auth.bluemap.markereditor.MarkerModels.*;

final class MarkerEditorDatabase {
    private static final Gson GSON = new Gson();
    private static final int SCHEMA_VERSION = 1;

    private final Database database;

    MarkerEditorDatabase(Database database) throws SQLException {
        this.database = database;
        initialize();
    }

    private void initialize() throws SQLException {
        database.update("""
            CREATE TABLE IF NOT EXISTS marker_editor_meta (
                meta_key VARCHAR(255) PRIMARY KEY NOT NULL,
                meta_value VARCHAR(255) NOT NULL
            )
            """);

        Integer version = readSchemaVersion();
        validateSchemaVersion(version);

        database.update("""
            CREATE TABLE IF NOT EXISTS marker_editor_sets (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                map_id VARCHAR(255) NOT NULL,
                scope VARCHAR(16) NOT NULL,
                owner_uuid VARCHAR(36) NOT NULL,
                marker_key VARCHAR(64) NOT NULL,
                label VARCHAR(256) NOT NULL,
                toggleable INTEGER NOT NULL,
                default_hidden INTEGER NOT NULL,
                sorting INTEGER NOT NULL,
                created_by VARCHAR(36) NOT NULL,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                UNIQUE (map_id, scope, owner_uuid, marker_key)
            )
            """);
        database.update("""
            CREATE TABLE IF NOT EXISTS marker_editor_markers (
                id VARCHAR(36) PRIMARY KEY NOT NULL,
                set_id VARCHAR(36) NOT NULL,
                marker_key VARCHAR(64) NOT NULL,
                marker_type VARCHAR(16) NOT NULL,
                position_mode VARCHAR(16) NOT NULL,
                payload TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                UNIQUE (set_id, marker_key),
                FOREIGN KEY (set_id) REFERENCES marker_editor_sets(id) ON DELETE CASCADE
            )
            """);

        if (version == null) {
            try {
                database.update(
                    "INSERT INTO marker_editor_meta (meta_key, meta_value) VALUES (?, ?)",
                    "schema", Integer.toString(SCHEMA_VERSION)
                );
            } catch (SQLException insertFailure) {
                Integer concurrentVersion = readSchemaVersion();
                validateSchemaVersion(concurrentVersion);
                if (concurrentVersion == null) throw insertFailure;
            }
        }
    }

    private @Nullable Integer readSchemaVersion() throws SQLException {
        return database.query(
            "SELECT meta_value FROM marker_editor_meta WHERE meta_key = ?",
            result -> {
                if (!result.next()) return null;
                try {
                    return Integer.parseInt(result.getString(1));
                } catch (NumberFormatException exception) {
                    throw new SQLException("Invalid MarkerEditor database schema version", exception);
                }
            },
            "schema"
        );
    }

    private static void validateSchemaVersion(@Nullable Integer version) throws SQLException {
        if (version != null && (version < 1 || version > SCHEMA_VERSION))
            throw new SQLException("Unsupported MarkerEditor database schema version: " + version);
    }

    synchronized List<StoredSet> listForState(String mapId, UUID owner) throws SQLException {
        return loadSets(
            "SELECT * FROM marker_editor_sets WHERE map_id = ? AND (scope = 'global' OR (scope = 'private' AND owner_uuid = ?)) ORDER BY sorting, marker_key",
            mapId, owner.toString()
        );
    }

    synchronized List<StoredSet> listGlobal(String mapId) throws SQLException {
        return loadSets(
            "SELECT * FROM marker_editor_sets WHERE map_id = ? AND scope = 'global' ORDER BY sorting, marker_key",
            mapId
        );
    }

    synchronized List<StoredSet> listPrivate(String mapId, UUID owner) throws SQLException {
        return loadSets(
            "SELECT * FROM marker_editor_sets WHERE map_id = ? AND scope = 'private' AND owner_uuid = ? ORDER BY sorting, marker_key",
            mapId, owner.toString()
        );
    }

    synchronized @Nullable StoredSet findSet(String mapId, UUID id) throws SQLException {
        List<StoredSet> result = loadSets("SELECT * FROM marker_editor_sets WHERE map_id = ? AND id = ?", mapId, id.toString());
        return result.isEmpty() ? null : result.getFirst();
    }

    synchronized StoredSet createSet(String mapId, Scope scope, @Nullable UUID owner, UUID actor, SetInput input)
        throws SQLException, ApiException {
        String ownerValue = owner == null ? "" : owner.toString();
        if (setKeyExists(mapId, scope, ownerValue, input.key, null))
            throw ApiException.field("key", "A marker set with this key already exists");

        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        try {
            database.update("""
                INSERT INTO marker_editor_sets
                    (id, map_id, scope, owner_uuid, marker_key, label, toggleable, default_hidden, sorting, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id.toString(), mapId, scope.jsonName(), ownerValue, input.key, input.label,
                input.toggleable ? 1 : 0, input.defaultHidden ? 1 : 0, input.sorting,
                actor.toString(), now, now
            );
        } catch (SQLException insertFailure) {
            if (setKeyExists(mapId, scope, ownerValue, input.key, null))
                throw ApiException.field("key", "A marker set with this key already exists");
            throw insertFailure;
        }
        return new StoredSet(id, mapId, scope, owner, setModel(id, scope, input, new ArrayList<>()));
    }

    synchronized StoredSet updateSet(StoredSet stored, SetInput input) throws SQLException, ApiException {
        String ownerValue = stored.owner == null ? "" : stored.owner.toString();
        if (setKeyExists(stored.mapId, stored.scope, ownerValue, input.key, stored.id))
            throw ApiException.field("key", "A marker set with this key already exists");

        try {
            database.update("""
                UPDATE marker_editor_sets
                SET marker_key = ?, label = ?, toggleable = ?, default_hidden = ?, sorting = ?, updated_at = ?
                WHERE id = ?
                """,
                input.key, input.label, input.toggleable ? 1 : 0, input.defaultHidden ? 1 : 0,
                input.sorting, System.currentTimeMillis(), stored.id.toString()
            );
        } catch (SQLException updateFailure) {
            if (setKeyExists(stored.mapId, stored.scope, ownerValue, input.key, stored.id))
                throw ApiException.field("key", "A marker set with this key already exists");
            throw updateFailure;
        }
        return new StoredSet(
            stored.id, stored.mapId, stored.scope, stored.owner,
            setModel(stored.id, stored.scope, input, stored.model.markers)
        );
    }

    synchronized void deleteSet(StoredSet stored) throws SQLException {
        database.update("DELETE FROM marker_editor_sets WHERE id = ?", stored.id.toString());
    }

    synchronized MarkerModel createMarker(StoredSet set, MarkerData marker) throws SQLException, ApiException {
        if (markerKeyExists(set.id, marker.key, null))
            throw ApiException.field("key", "A marker with this key already exists in this set");

        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        try {
            database.update("""
                INSERT INTO marker_editor_markers
                    (id, set_id, marker_key, marker_type, position_mode, payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id.toString(), set.id.toString(), marker.key, marker.type, marker.positionMode,
                GSON.toJson(marker), now, now
            );
        } catch (SQLException insertFailure) {
            if (markerKeyExists(set.id, marker.key, null))
                throw ApiException.field("key", "A marker with this key already exists in this set");
            throw insertFailure;
        }
        return markerModel(id, marker);
    }

    synchronized MarkerModel updateMarker(StoredSet set, UUID markerId, MarkerData marker)
        throws SQLException, ApiException {
        if (findMarker(set, markerId) == null) throw new ApiException(404, "not_found", "Marker not found");
        if (markerKeyExists(set.id, marker.key, markerId))
            throw ApiException.field("key", "A marker with this key already exists in this set");

        try {
            database.update("""
                UPDATE marker_editor_markers
                SET marker_key = ?, marker_type = ?, position_mode = ?, payload = ?, updated_at = ?
                WHERE id = ? AND set_id = ?
                """,
                marker.key, marker.type, marker.positionMode, GSON.toJson(marker), System.currentTimeMillis(),
                markerId.toString(), set.id.toString()
            );
        } catch (SQLException updateFailure) {
            if (markerKeyExists(set.id, marker.key, markerId))
                throw ApiException.field("key", "A marker with this key already exists in this set");
            throw updateFailure;
        }
        return markerModel(markerId, marker);
    }

    synchronized @Nullable MarkerModel findMarker(StoredSet set, UUID markerId) throws SQLException {
        return database.query(
            "SELECT id, payload FROM marker_editor_markers WHERE id = ? AND set_id = ?",
            result -> result.next() ? readMarker(result) : null,
            markerId.toString(), set.id.toString()
        );
    }

    synchronized void deleteMarker(StoredSet set, UUID markerId) throws SQLException, ApiException {
        if (findMarker(set, markerId) == null) throw new ApiException(404, "not_found", "Marker not found");
        database.update("DELETE FROM marker_editor_markers WHERE id = ? AND set_id = ?", markerId.toString(), set.id.toString());
    }

    void close() {
        database.close();
    }

    private boolean setKeyExists(String mapId, Scope scope, String owner, String key, @Nullable UUID except) throws SQLException {
        return database.query(
            "SELECT id FROM marker_editor_sets WHERE map_id = ? AND scope = ? AND owner_uuid = ? AND marker_key = ?",
            result -> result.next() && (except == null || !except.toString().equals(result.getString(1))),
            mapId, scope.jsonName(), owner, key
        );
    }

    private boolean markerKeyExists(UUID setId, String key, @Nullable UUID except) throws SQLException {
        return database.query(
            "SELECT id FROM marker_editor_markers WHERE set_id = ? AND marker_key = ?",
            result -> result.next() && (except == null || !except.toString().equals(result.getString(1))),
            setId.toString(), key
        );
    }

    private List<StoredSet> loadSets(String sql, Object... parameters) throws SQLException {
        List<StoredSet> sets = database.query(sql, result -> {
            List<StoredSet> loaded = new ArrayList<>();
            while (result.next()) loaded.add(readSet(result));
            return loaded;
        }, parameters);
        for (StoredSet set : sets) set.model.markers.addAll(loadMarkers(set.id));
        return sets;
    }

    private List<MarkerModel> loadMarkers(UUID setId) throws SQLException {
        return database.query(
            "SELECT id, payload FROM marker_editor_markers WHERE set_id = ? ORDER BY marker_key",
            result -> {
                List<MarkerModel> markers = new ArrayList<>();
                while (result.next()) markers.add(readMarker(result));
                return markers;
            },
            setId.toString()
        );
    }

    private StoredSet readSet(ResultSet result) throws SQLException {
        UUID id = UUID.fromString(result.getString("id"));
        Scope scope = switch (result.getString("scope")) {
            case "global" -> Scope.GLOBAL;
            case "private" -> Scope.PRIVATE;
            default -> throw new SQLException("Invalid marker set scope");
        };
        String ownerValue = result.getString("owner_uuid");
        UUID owner = ownerValue == null || ownerValue.isEmpty() ? null : UUID.fromString(ownerValue);
        SetInput input = new SetInput();
        input.key = result.getString("marker_key");
        input.scope = scope.jsonName();
        input.label = result.getString("label");
        input.toggleable = result.getInt("toggleable") != 0;
        input.defaultHidden = result.getInt("default_hidden") != 0;
        input.sorting = result.getInt("sorting");
        return new StoredSet(id, result.getString("map_id"), scope, owner, setModel(id, scope, input, new ArrayList<>()));
    }

    private MarkerModel readMarker(ResultSet result) throws SQLException {
        try {
            UUID id = UUID.fromString(result.getString("id"));
            MarkerData stored = GSON.fromJson(result.getString("payload"), MarkerData.class);
            return markerModel(id, MarkerCodec.normalizeMarker(stored));
        } catch (JsonParseException | IllegalArgumentException | ApiException exception) {
            throw new SQLException("Invalid marker data in database", exception);
        }
    }

    private static SetModel setModel(UUID id, Scope scope, SetInput input, List<MarkerModel> markers) {
        SetModel model = new SetModel();
        model.id = id.toString();
        model.key = input.key;
        model.scope = scope.jsonName();
        model.label = input.label;
        model.toggleable = input.toggleable;
        model.defaultHidden = input.defaultHidden;
        model.sorting = input.sorting;
        model.markers = new ArrayList<>(markers);
        return model;
    }

    private static MarkerModel markerModel(UUID id, MarkerData marker) {
        MarkerModel model = GSON.fromJson(GSON.toJson(marker), MarkerModel.class);
        model.id = id.toString();
        return model;
    }

    record StoredSet(UUID id, String mapId, Scope scope, @Nullable UUID owner, SetModel model) {}
}
