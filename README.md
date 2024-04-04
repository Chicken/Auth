# Auth

<!--
> **Warning** <br>
> In development, be wary when using in production.  
> The security of this solution hasn't been verified by third parties.
-->

A pair of plugins for **authentication** and **authorization** of Minecraft players in web applications.

You can also find a bunch of plugins using this authentication system
for BlueMap addons in [the BlueMap -folder](./BlueMap). Should these be in a seperate repository?
Absolutely! Are they? No. Is Authorization the child that's not liked as other solutions are better? Yes.

An authenticated BlueMap demo can be found at https://auth-dev.antti.codes/.
The Minecraft server is also available at ip `auth-dev.antti.codes`.
In the server you can use demo commands `/demo list` and `/demo toggle <node>` to change your permissions.
This server doubles as my development server so it might sometimes be down or have features not yet on GitHub.

## Prerequisites

These plugins require you to have your own server such as a cloud vps or a dedicated bare metal server.
This is because you will need multiple ports, Nginx and the ability to firewall ports from the outside world.
Running on a Minecraft hosting service is probably a bad idea.
The Authorization plugin also depends on LuckPerms for offline player permissions.

Auth is pretty advanced stuff so this guide assumes you have pretty good basic understanding of Linux, Nginx and networking.
If you can't create a simple reverse proxy on Nginx or set the dns A record on a domain, this isn't for you.

The setup of the plugins itself is very easy, the difficulty mostly comes in the form of Nginx configuration.
And if you want to use something else instead of Nginx you will have to figure that out yourself.

### Security considerations

*These plugins are made available for free without warranty of any kind. The authors can't be held responsible for any damages.
Refer to the [LICENSE](https://github.com/Chicken/Auth/blob/master/LICENSE) file for full conditions.*

The final web application behind the proxy layers shouldn't be accessible via any other means than the proxy as
that would render the authentication and authorization useless.
This means you should restrict traffic to it via firewalls, ip binding or other measures.
If you are using Docker make sure to publish ports only on localhost as otherwise it might bypass your firewall such as UFW.
This especially applies to users of Pterodactyl who might have their port allocations on their public ip or all interfaces (`0.0.0.0`)

At the time of writing (`2023-05-19`) no third-party has reviewed the security.
You are by all means welcome to do your own security audit before usage and I would appreciate knowing the results of that.
Refer to the [SECURITY](https://github.com/Chicken/Auth/blob/master/.github/SECURITY.md) file for our security policy.

## Authentication

The Authentication plugin is the backbone of this project. It is what links a web session to a Minecraft player.
Players authenticate by running a command given by the login page and
then the web client carries a cookie which is linked to the player's uuid.
This uuid is then forwarded to the proxied web application.

This plugin can be used standalone without the Authorization plugin.
Then nobody will be restricted from using the final web application and
the web application can do what it wants with the player's uuid. 

### Endpoints

*This section is only relevant for advanced usage.*

The Authentication plugin serves four HTTP endpoints `/auth`, `/login`, `/logout` and `/logout/all`.

- `/auth` is the auth request endpoint used by Nginx. When a request has a valid session cookie and `x-forwarded-for`
header with the correct ip for that session it returns a status `200` and `x-minecraft-uuid` header with 
the authenticated player's uuid and `x-minecraft-username` header with the username. 
Otherwise, it will return a status `401` unless optional authentication is enabled when `200` is always returned.
When optional authentication is enabled a header called `x-minecraft-loggedin` is also returned with a boolean.
If the header is returned with false, the uuid and username headers do not exist.

- `/login` serves the static HTML file. It also sends a `Set-Cookie` header
to set a session cookie if the user doesn't already have one.

- `/logout` removes the currently authenticated session and clears the user's session cookie.

- `/logout/all` removes all sessions of the currently authenticated user and clears the user's session cookie.

### Configuration

The `ip` setting is by default `127.0.0.1` so only available on localhost.
Localhost should be fine when everything is running on the same machine.
When running inside Docker containers you need to bind to `0.0.0.0`
but remember to publish the port only on localhost or properly firewall it.
This setting might also be useful if you are running more exotic hardware configurations or on different servers.

The `port` setting is by default `8200` and should be self-explanatory for someone settings this up.

The `optional_authentication` setting defines if users are forced to log in or not. When set to `true` the users
can view the application without logging in and the application can choose to show its own guest mode and login button.

`session_length_days`, `auth_token_length` and `user_max_sessions` are optional customizations you can do for the authentication.
`session_length_days` is the lifetime of a session in days.
The default `31` means sessions expire after a month and then players need to reauthenticate.
`auth_token_length` is the length of the random string given to players for the command to authenticate.
`user_max_sessions` is the maximum amount of sessions a user can have at the same time. When the limit is reached,
the oldest session will be removed.

#### Customizing

The default login page is very bland.
You can customize it to your branding by editing the `login.html` in `./plugins/Authentication/web/login.html`.
You can create additional assets in the web folder. They will be available under the `/login/*` path of the webserver.
The placeholder `{{auth_token}}` in the `login.html` file is replaced by the authentication token.

#### Permissions

The Authentication plugin comes with a single permission node `authentication.authenticate`.
The permission node is for using the `/auth` command and is enabled by default.

## Authorization

The Authorization plugin is an example application that can use the benefits of Authentication to lock down websites.
It simply checks a given uuid and host pair for a configured permission node.
Authorization doesn't support optional authentication.
Permissions checks use a cache so permissions changes require about a minute to take effect.

### Endpoints

*This section is only relevant for advanced usage.*

The Authorization plugin serves two HTTP endpoints `/auth` and `/unauthorized`.

- `/auth` checks `Host` and `x-minecraft-uuid` headers and returns status `200` if the user of `x-minecraft-uuid`
has the configured permission node for `Host`. Otherwise, it returns status `403`.

- `/unauthorized` endpoints just serves the static HTML file.

### Configuration

The `ip` and `port` are the same as with Authentication. The default port is `8300`.

The main configuration happens in `sites` which is a list of key-value pairs of hostnames and permission nodes.
To access a page on a hostname the player needs the configured permission node.
For example `map.example.com: auth.map` means that to access the site at `map.example.com`
the player needs to have the `auth.map` permission node.

#### Customizing

The default unauthorized page is very bland.
You can customize it to your branding by editing the `unauthorized.html` in `./plugins/Authorization/unauthorized.html`.
You should probably consider adding instructions on how to get access or explain why the user can't access the page.
You can create additional assets in the web folder. They will be available under the `/unauthorized/*` path of the webserver.
There are couple placeholders available for the `unauthorized.html` file:

| Variable         | Value                              |
|------------------|------------------------------------|
| `{{uuid}}`       | Logged in player's uuid            |
| `{{name}}`       | Logged in player's name            |
| `{{host}}`       | The hostname of the target website |
| `{{permission}}` | The required permission node       |


## Example Setup

Example Nginx config that authenticates and authorizes players for an imaginary map at `map.example.com`.
It assumes default plugin configs and everything running on the same linux server.
Per the default configs players would need the `auth.map` permission node to access the website.
In the config `127.0.0.1:8100` is the map itself.

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

# Authorization layer, all requests are authenticated, proxy valid requests to the final web application
server {
  # Arbitary unused port on localhost
  listen 127.0.0.1:8000; # REPLACE ME
  
  # The authentication layer already logs, we don't need internal logging
  access_log off;

  location / {
    # Proxy to your final web application, for example a map
    proxy_pass http://127.0.0.1:8100; # REPLACE ME
    proxy_buffering off;
    
    # Checking the authorization
    auth_request /authorization-outpost/auth;
    # When not authorized, rewrite to unauthorized
    error_page 403 = @minecraft_unauthorized;
  }

  # Arbitary unused path
  location /authorization-outpost/ {
    # Proxy to your authorization plugin instance
    proxy_pass http://127.0.0.1:8300/; # REPLACE ME
    proxy_set_header Host $host;
    # Nginx requirements
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
  }

  # Internal location for redirecting to unauthorized page
  location @minecraft_unauthorized {
      internal;
      return 302 https://$host/authorization-outpost/unauthorized;
    }
}
```
