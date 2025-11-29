# Configuration

Pairgoth general configuration is done using the `pairgoth.properties` file in the installation folder.

Properties are loaded in this order (later overrides earlier):

1. Default properties embedded in WAR/JAR
2. User properties file (`./pairgoth.properties`) in current working directory
3. System properties prefixed with `pairgoth.` (command-line: `-Dpairgoth.key=value`)

## Environment

Controls the running environment.

```
env = prod
```

Values:
- `dev` - Development mode: enables CORS headers and additional logging
- `prod` - Production: for distributed instances

## Mode

Running mode for the application.

```
mode = standalone
```

Values:
- `standalone` - Both web and API in a single process (default for jar execution)
- `server` - API only
- `client` - Web UI only (connects to remote API)

## Authentication

Authentication method for the application.

```
auth = none
```

Values:
- `none` - No authentication required
- `sesame` - Shared unique password
- `oauth` - Email and/or OAuth accounts

### Shared secret

When running in client or server mode with authentication enabled:

```
auth.shared_secret = <16 ascii characters string>
```

This secret is shared between API and View webapps. Auto-generated in standalone mode.

### Sesame password

When using sesame authentication:

```
auth.sesame = <password>
```

## OAuth configuration

When using OAuth authentication:

```
oauth.providers = ffg,google,facebook
```

Comma-separated list of enabled providers: `ffg`, `facebook`, `google`, `instagram`, `twitter`

For each enabled provider, configure credentials:

```
oauth.<provider>.client_id = <client_id>
oauth.<provider>.secret = <client_secret>
```

Example:
```
oauth.ffg.client_id = your-ffg-client-id
oauth.ffg.secret = your-ffg-client-secret
oauth.google.client_id = your-google-client-id
oauth.google.secret = your-google-client-secret
```

## Webapp connector

Pairgoth webapp (UI) connector configuration.

```
webapp.protocol = http
webapp.host = localhost
webapp.port = 8080
webapp.context = /
webapp.external.url = http://localhost:8080
```

- `webapp.host` (or `webapp.interface`) - Hostname/interface to bind to
- `webapp.external.url` - External URL for OAuth redirects and client configuration

## API connector

Pairgoth API connector configuration.

```
api.protocol = http
api.host = localhost
api.port = 8085
api.context = /api
api.external.url = http://localhost:8085/api
```

Note: In standalone mode, API port defaults to 8080 and context to `/api/tour`.

## SSL/TLS configuration

For HTTPS connections:

```
webapp.ssl.key = path/to/localhost.key
webapp.ssl.cert = path/to/localhost.crt
webapp.ssl.pass = <key passphrase>
```

Supports `jar:` URLs for embedded resources.

## Store

Persistent storage for tournaments.

```
store = file
store.file.path = tournamentfiles
```

Values for `store`:
- `file` - Persistent XML files (default)
- `memory` - RAM-based (mainly for tests)

The `store.file.path` is relative to the current working directory.

## Ratings

### Ratings directory

```
ratings.path = ratings
```

Directory for caching downloaded ratings files.

### Rating sources

For each rating source (`aga`, `egf`, `ffg`):

```
ratings.<source> = <url or file path>
```

If not set, ratings are auto-downloaded from the default URL. Set to a local file path to freeze ratings at a specific date.

Example to freeze EGF ratings:
```
ratings.egf = ratings/EGF-20240115.json
```

### Enable/disable ratings

```
ratings.<source>.enable = true | false
```

Whether to display the rating source button in the Add Player popup.

```
ratings.<source>.show = true | false
```

Whether to show player IDs from this rating source on the registration page.

Defaults:
- For tournaments in France: FFG enabled and shown by default
- Otherwise: all disabled by default

## SMTP

SMTP configuration for email notifications. Not yet functional.

```
smtp.sender = sender@example.com
smtp.host = smtp.example.com
smtp.port = 587
smtp.user = username
smtp.password = password
```

## Logging

Logging configuration.

```
logger.level = info
logger.format = [%level] %ip [%logger] %message
```

Log levels: `trace`, `debug`, `info`, `warn`, `error`

Format placeholders: `%level`, `%ip`, `%logger`, `%message`

## Example configurations

### Standalone development

```properties
env = dev
mode = standalone
auth = none
store = file
store.file.path = tournamentfiles
logger.level = trace
```

### Client-server deployment

**Server (API):**
```properties
env = prod
mode = server
auth = oauth
auth.shared_secret = 1234567890abcdef
api.port = 8085
store = file
store.file.path = /var/tournaments
logger.level = info
```

**Client (Web UI):**
```properties
env = prod
mode = client
auth = oauth
auth.shared_secret = 1234567890abcdef
oauth.providers = ffg,google
oauth.ffg.client_id = your-ffg-id
oauth.ffg.secret = your-ffg-secret
oauth.google.client_id = your-google-id
oauth.google.secret = your-google-secret
webapp.port = 8080
api.external.url = http://api-server:8085/api
```
