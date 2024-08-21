# Auth/BlueMap/PrivateLocation

Only shows the currently logged in player on the map.
Prevents seeing other players.

## Configuration

The only configuration is `ip` and `port`, which are the same as with the main projects in this repository.
The default port is `8500`.

## Permissions

Permissions checks use a cache so permissions changes require about a minute to take effect.

- `privatelocation.seeall` exists to bypass the private location system.

## Endpoints

*This section is only relevant for advanced usage.*

- `/players/$map` returns BlueMap compatible players JSON for the map with only the current player
(or all with the bypass permission).

## Nginx configuration

See [the BlueMap Integration example Nginx configuration](https://github.com/Chicken/Auth/tree/master/BlueMap/Integration#example-nginx).
And add the following in your application layer next to the Integration configuration:

```nginx
  location ~ ^/maps/([^/]*)/live/players.json {
    proxy_pass http://127.0.0.1:8500/players/$1;
  }
```
