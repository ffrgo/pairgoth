# Configuration

Pairgoth general configuration is done using the `pairgoth.properties` file in the installation folder.

## environment

Controls the running environment: `dev` for development, `prod` for distributed instances.

```
env = prod
```

## mode

Running mode: `standalone`, `client` or `server`.

```
mode = standalone
```

## authentication

Authentication: `none`, `sesame` for a shared unique password, `oauth` for email and/or oauth accounts.

```
auth = none
```

When running in client or server mode, if `auth` is not `none`, the following extra property is needed:

```
auth.shared_secret = <16 ascii characters string>
```

## webapp connector

Pairgoth webapp connector configuration.

```
webapp.protocol = http
webapp.host = localhost
webapp.port = 8080
webapp.context = /
webapp.external.url = http://localhost:8080
```

## api connector

Pairgoth API connector configuration.

```
api.protocol = http
api.host = localhost
api.port = 8085
api.context = /api
api.external.url = http://localhost:8085/api
```

## store

Persistent storage for tournaments, `memory` (mainly used for tests) or `file`.

```
store = file
store.file.path = tournamentfiles
```

## smtp

SMTP configuration. Not yet functional.

```
smtp.sender = 
smtp.host = 
smtp.port = 587
smtp.user = 
smtp.password = 
```

## logging

Logging configuration.

```
logger.level = info
logger.format = [%level] %ip [%logger] %message
```
