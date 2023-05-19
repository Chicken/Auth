# Auth

> **Warning** <br>
> In development, be wary when using in production

A pair of plugins for **authentication** and **authorization** of Minecraft players in web applications.

Usage and documentation will come later.

For anyone brave enough to try without proper documentation, here is a commented example nginx config:

<details>
<summary>Config</summary>

```nginx
# Basic http to https redirect
server {
    listen 80;
    listen [::]:80;
    # Your domain name
    server_name map.example.com;
    return 301 https://$host$request_uri;
}

server {
  # Https server
  listen 443 ssl;
  listen [::]:443 ssl;
 
  # Your domain name
  server_name map.example.com;

  # Your ssl certificates
  ssl_certificate /etc/nginx/certs/cert.pem;
  ssl_certificate_key /etc/nginx/certs/key.pem;

  location / {
    # Arbitary port which has to be same as the one in the server block below
    proxy_pass http://127.0.0.1:8400;

    # Checking the authentication
    auth_request /authentication-outpost/auth;
    # When not authenticated, rewrite to login
    error_page 401 = @minecraft_login;

    # Set a proxy header which contains the authenticated user's uuid
    auth_request_set $minecraft_uuid $upstream_http_x_minecraft_uuid;
    proxy_set_header x-minecraft-uuid $minecraft_uuid;
    proxy_set_header Host $host;
  }

  # Arbitary unused path
  location /authentication-outpost/ {
    # Proxy to your authentication plugin instance
    proxy_pass http://127.0.0.1:8200/;
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

server {
  # Arbitary unused port on localhost
  listen 127.0.0.1:8400;

  location / {
    # Proxy to your final web application, for example a map
    proxy_pass http://127.0.0.1:8100;
    
    # Checking the authorization
    auth_request /authorization-outpost/auth;
    # When not authorized, rewrite to unauthorized
    error_page 403 = @minecraft_unauthorized;
  }

  # Arbitary unused path
  location /authorization-outpost/ {
    # Proxy to your authorization plugin instance
    proxy_pass http://127.0.0.1:8300/;
    proxy_set_header Host $host;
    # Nginx requirements
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
  }

  # Internal location for redirecting to unauthorized page
  location @minecraft_unauthorized {
      internal;
      # Your domain name here
      return 302 https://map.example.com/authorization-outpost/unauthorized;
    }
}
```
</details>
