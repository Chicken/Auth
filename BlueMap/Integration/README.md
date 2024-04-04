# Auth/BlueMap/Integration

Authentication integration for BlueMap.
Adds logout buttons to the menu and profile view with username and a player head to see the logged-in user.
Serves as a requirement for other BlueMap addons by providing logged-in user's information.
Optional authentication is recommended, but not required.

## Configuration

The `ip` and `port` are the same as with the main projects in this repository. The default port is `8400`.

`auth-path` is an optional configuration value which should point to where the Authentication portal is hosted at.

## Endpoints

*This section is only relevant for advanced usage.*

`/whoami` is the only endpoint provided by the addon. It returns an empty JSON object for unauthenticated users and
an object with uuid and username for authenticated users.

## Addon API

*This section is only relevant for addon developers.*

The addon adds an object to the global `window` called `window.bluemapAuth` which contains a `loggedIn` boolean and
uuid and username strings. You may acquire this information by creating and calling function such as the following:

```js
function getAuthStatus() {
    return new Promise((res, err) => {
        let timeout;

        const acquireInterval = setInterval(() => {
            if (window.bluemapAuth) {
                clearInterval(acquireInterval);
                if (timeout) clearTimeout(timeout);
                res(window.bluemapAuth);
            }
        }, 10);

        timeout = setTimeout(() => {
            clearInterval(acquireInterval);
            err()
        }, 2000);
    });
}
```

## Example Nginx

This an example Nginx config to use when using Authentication and BlueMap-Auth with optional authentication.
This Nginx config will be expanded by other addons in this repository.

```nginx
# Basic http to https redirect
server {
  listen 80;
  listen [::]:80;
  # Your domain name
  server_name map.example.com; # REPLACE ME
  return 301 https://$host$request_uri;
}

# Https server, authentication layer
server {
  listen 443 ssl;
  listen [::]:443 ssl;

  # Your domain name
  server_name map.example.com; # REPLACE ME
  
  # Your ssl certificates
  ssl_certificate /etc/nginx/certs/cert.pem; # REPLACE ME
  ssl_certificate_key /etc/nginx/certs/key.pem; # REPLACE ME

  location / {
    # Arbitary port which has to be same as the one in the server block below
    proxy_pass http://127.0.0.1:8000; # REPLACE ME
    proxy_buffering off;

    # Checking the authentication
    auth_request /authentication-outpost/auth;
    error_page 401 = @minecraft_login;

    # Set the request to backend to contain user's information
    auth_request_set $minecraft_loggedin $upstream_http_x_minecraft_loggedin;
    auth_request_set $minecraft_uuid $upstream_http_x_minecraft_uuid;
    auth_request_set $minecraft_username $upstream_http_x_minecraft_username;

    proxy_set_header x-minecraft-loggedin $minecraft_loggedin;
    proxy_set_header x-minecraft-uuid $minecraft_uuid;
    proxy_set_header x-minecraft-username $minecraft_username;

    proxy_set_header Host $host;
  }

  # Arbitary unused path
  location /authentication-outpost/ {
    # Proxy to your authentication plugin instance
    proxy_pass http://127.0.0.1:8200/; # REPLACE ME
    # Send users ip to authentication for security reasons
    proxy_set_header X-Forwarded-For $remote_addr;
    # Nginx requirements
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
  }

  # Internal location for redirecting to login page
  location @minecraft_login {
    internal;
    return 302 /authentication-outpost/login;
  }
}

# Application layer, all requests are authenticated, proxy requests to BlueMap or an addon
server {
  # Arbitary unused port on localhost
  listen 127.0.0.1:8000; # REPLACE ME

  # The authentication layer already logs, we don't need internal logging
  access_log off;

  # Everything by default to BlueMap
  location / {
    proxy_pass http://127.0.0.1:8100;
    proxy_buffering off;
  }

　# Addon integration requests to it
  location /addons/integration/ {
     proxy_pass http://127.0.0.1:8400/;
     proxy_buffering off;
  }
  
  # Other addons go here
}

```
