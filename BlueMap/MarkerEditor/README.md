# Auth/BlueMap/MarkerEditor

> **Warning** <br>
> This one is vibecoded slop that hasn't been properly reviewed or tested.
> It was made as a Proof of Concept and will become obsolete when BlueMap's new webapp arrives.

An in-browser marker editor for BlueMap. It supports marker sets and all marker types exposed by the BlueMap Java API: POI, HTML, line, shape, and extrude markers.

Authenticated users can create private markers which are visible only to them. Players with `bluemap.markereditor.global` can also manage global markers shown to everyone. The addon owns only marker sets in its reserved `marker-editor:` namespace and does not modify markers registered by other plugins.

BlueMap, LuckPerms, and [BlueMap Auth Integration](../Integration) are required. Authentication must be optional if logged-out visitors should still be able to view the map.

## Security warning

BlueMap supports raw HTML in HTML markers and marker detail fields. MarkerEditor stores and serves that HTML unchanged. This gives every authenticated user who can create a marker the ability to store same-origin script content, including in their own private markers. Only deploy MarkerEditor when authenticated players are trusted with this capability.

## Configuration

`ip` and `port` configure the addon's HTTP server. The default port is `8700`. Keep this server bound to localhost or a private network and expose it only through the authenticated Nginx layer. Requests made directly to this port could forge the Minecraft identity headers.

`auth-path` points to the Authentication portal. `register-web-app` controls whether this instance installs the editor assets into BlueMap. Set it to `true` only on the instance which owns the served BlueMap web root.

Marker data is stored in a database. SQLite is the default:

```yaml
database_url: "jdbc:sqlite:{data}/markers.db"
driver_class: "org.sqlite.JDBC"
```

MySQL and MariaDB are also supported. A driver can be supplied with `driver_jar` and `driver_class` when the server does not already provide it:

```yaml
database_url: "jdbc:mariadb://localhost:3306/marker_editor?user=example&password=secret"
driver_jar: "{data}/mariadb-java-client-3.5.8.jar"
driver_class: "org.mariadb.jdbc.Driver"
```

`{data}` expands to `plugins/BlueMap-MarkerEditor/` on Spigot/Paper and `config/bluemap-marker-editor/` on Fabric.

## Permission

- `bluemap.markereditor.global` permits editing global marker sets and defaults to `false`.

All authenticated users can manage their own private marker sets. Users without the global permission can view global MarkerEditor entries in the editor but cannot change them.

## Endpoints

The browser uses the following authenticated routes. `{map}` is the BlueMap map ID.

- `GET /addons/marker-editor/{map}/state`
- `GET /addons/marker-editor/{map}/private-markers`
- `POST /addons/marker-editor/{map}/marker-sets`
- `PUT|DELETE /addons/marker-editor/{map}/marker-sets/{set-id}`
- `POST /addons/marker-editor/{map}/marker-sets/{set-id}/markers`
- `PUT|DELETE /addons/marker-editor/{map}/marker-sets/{set-id}/markers/{marker-id}`

Private marker JSON is loaded as normal top-level BlueMap marker sets. BlueMap's marker JSON and SSE endpoints are not intercepted; the client preserves its loaded private set IDs while BlueMap reconciles public marker updates. Private markers are reloaded after the current browser changes them, when changing maps, or when reloading the page. Concurrent edits use last-write-wins behavior.

## Nginx configuration

Add this location to the authenticated application layer from the [Integration example](../Integration#example-nginx):

```nginx
location /addons/marker-editor/ {
  # No URI suffix here: the complete /addons/marker-editor/... URI must be preserved.
  proxy_pass http://127.0.0.1:8700;
}
```

Do not expose port `8700` publicly. The outer Authentication layer must forward `x-minecraft-loggedin`, `x-minecraft-uuid`, and `x-minecraft-username` as shown in the Integration configuration.

## Multi-server setup

Run one MarkerEditor instance on each server which owns BlueMap maps and route requests by map ID. All instances must share one MySQL or MariaDB database so the management state is consistent. Only the instance serving the BlueMap web root should use `register-web-app: true`.

The following example uses map ID suffixes to route two secondary servers, then falls back to the primary server:

```nginx
location ~ ^/addons/marker-editor/[^/]+_fabric/ {
  proxy_pass http://127.0.0.1:8702;
}

location ~ ^/addons/marker-editor/[^/]+_server_b/ {
  proxy_pass http://127.0.0.1:8701;
}

location /addons/marker-editor/ {
  proxy_pass http://127.0.0.1:8700;
}
```

Each `proxy_pass` intentionally has no path component so Nginx preserves the map-bearing request URI. MarkerEditor does not proxy or intercept BlueMap's `markers.json` or SSE routes.

## Development and manual testing

Build the complete project and deploy the development JARs:

```bash
./gradlew build
dev/update-plugins.sh
docker compose -f dev/docker-compose.yml up -d mariadb paper1 nginx
```

The web project uses pnpm 11.3.0. For frontend-only work, run `pnpm install --frozen-lockfile`, `pnpm run typecheck`, and `pnpm run build` from `BlueMap/MarkerEditor/web`. Gradle runs the same pnpm install, typecheck, and build pipeline when packaging either platform.

An existing MariaDB development volume predating MarkerEditor also needs the `marker_editor` database and grant from `dev/mariadb/init/01-create-databases.sql` applied once. The init script handles fresh volumes automatically.

Test HTTP behavior separately from the browser. Direct requests to Paper's development port `8701` can supply the three `x-minecraft-*` headers explicitly; requests through Nginx on port `8000` should instead use an Authentication session cookie. Cover logged-out state, missing or malformed auth headers, private ownership isolation, global permission denial and success, validation errors, oversized bodies, CRUD for every marker type, canonical private-marker JSON, and global reinjection after `bluemap reload`.

For the visual pass, use Paper's map through Nginx and verify:

- the logged-out and missing-Integration disabled states;
- private and global tabs with permission-dependent controls;
- marker-set properties and all fields for POI, HTML, line, shape, and extrude markers;
- point picking and geometry drawing without BlueMap's coordinate popup, live marker previews, holes, undo, finish, cancel, and draft cleanup;
- private sets appearing directly in BlueMap's marker menu, visibility persistence, and survival across a normal public marker refresh;
- raw HTML warnings, validation feedback, dirty-form confirmation, map changes, BlueMap themes, keyboard focus, and mobile layout.

Fabric is covered by compilation, remapping, shaded-JAR inspection, and code review in the normal development loop. Run the interactive server/browser pass on Paper unless Fabric-specific behavior is being changed.
