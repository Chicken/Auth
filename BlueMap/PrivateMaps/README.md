# Auth/BlueMap/PrivateMaps

Add permissions nodes for maps.
Prevents users without a maps permission node from viewing them.
Supports optional authentication.

## Configuration

The `ip` and `port` are the same as with the main projects in this repository. The default port is `8600`.

`bluemap-url` should be a http url where the backend can reach BlueMap to retrieve configuration. By default `"http://localhost:8100"`.

# Permissions

When user is not logged in, permissions are checked against the default group.
Permissions checks use a cache so permissions changes require about a minute to take effect.

- `auth.maps.$map` permissions to view `$map`

## Endpoints

- `/settings.json` returns modified BlueMap configuration with the `maps` field containing a list of maps the user has permission to view.
- `/auth` requires headers `x-minecraft-uuid` and `x-original-uri` to be set. `x-minecraft-loggedin` must be set
when using optional authentication. `x-original-uri` should contain the path user is trying to access.
Map id is parsed from the path and permissions are compared against the player uuid. Returns 200 if user has permission, 403 otherwise.

## Nginx configuration

See [the BlueMap Integration example Nginx configuration](https://github.com/Chicken/Auth/tree/master/BlueMap/Integration#example-nginx).

Add the following in your application layer next to the Integration configuration:
```nginx
  # Get modified settings from PrivateMaps
  location = /settings.json {
    proxy_pass http://127.0.0.1:8600;
  }
```

Change
```nginx
  # Everything by default to BlueMap
  location / {
    proxy_pass http://127.0.0.1:8100;
    proxy_buffering off;
  }
```
to
```nginx
  # Go through PrivateMaps
  location / {
    # Arbitary port which has to be same as the one in the server block below
    proxy_pass http://127.0.0.1:7999; # REPLACE ME
    proxy_buffering off;
  }
```

And add
```nginx
# PrivateMaps layer
server {
  # Arbitary unused port on localhost
  listen 127.0.0.1:7999; # REPLACE ME

  # The authentication layer already logs, we don't need internal logging
  access_log off;

  # Everything by default to BlueMap
  location / {
    proxy_pass http://127.0.0.1:8100;

    # Check permissions
    auth_request /privatemaps-outpost/auth;
    error_page 403 = @minecraft_unauthorized;
  }

  # Arbitary unused path
  location /privatemaps-outpost/ {
    # Proxy to your PrivateMaps instance
    proxy_pass http://127.0.0.1:8600/; # REPLACE ME
    # PrivateMaps headers
    proxy_set_header X-Original-URI $request_uri;
    # Nginx requirements
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
  }

  # Internal location to just respond with 403 Forbidden
  location @minecraft_unauthorized {
    internal;
    return 403;
  }
}
```
