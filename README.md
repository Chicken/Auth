# Auth

> **Warning**
> In development, not for production use

A pair of plugins for **authentication** and **authorization** of Minecraft players in web applications.

Usage and documentation will come later.

For anyone brave enough to try without documentation, here's my nginx config:

<details>
<summary>Config</summary>

```nginx
server {
  listen 443 ssl;
  listen [::]:443 ssl;
  server_name auth-dev.antti.codes;

  ssl_certificate /etc/nginx/certs/cert.pem;
  ssl_certificate_key /etc/nginx/certs/key.pem;

  location / {
    proxy_pass http://127.0.0.1:7925;

    auth_request /authentication-outpost/auth;
    error_page 401 = @minecraft_login;

    auth_request_set $minecraft_uuid $upstream_http_x_minecraft_uuid;
    proxy_set_header x-minecraft-uuid $minecraft_uuid;
    proxy_set_header Host $host;
  }

  location /authentication-outpost/ {
    proxy_pass http://127.0.0.1:31514/;
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
  }

  location @minecraft_login {
    internal;
    return 302 /authentication-outpost/login;
  }
}

server {
  listen 127.0.0.1:7925;

  location / {
    proxy_pass http://127.0.0.1:31516;

    auth_request /authorization-outpost/auth;
    error_page 403 = @minecraft_unauthorized;
  }

  location /authorization-outpost/ {
    proxy_pass http://127.0.0.1:31515/;
    proxy_set_header Host $host;
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
  }

  location @minecraft_unauthorized {
      internal;
      return 302 https://auth-dev.antti.codes/authorization-outpost/unauthorized;
    }
}
```
</details>
