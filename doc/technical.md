# Pairgoth Technical Documentation

[TOC]

## Deployment profiles

pairgoth integrates with an "event" site (a club page, a federation site, an EGC-scale platform) on two
independent planes: pairgoth **pushing** to the event (the *webhook*), and the event **driving** pairgoth
(the *API*). Which you set up depends on how pairgoth is reached and how complex the event is —
**most organizers only need the first row.**

| Profile | pairgoth runs… | Integration | Auth | Secrets |
|---|---|---|---|---|
| **local-publish** | on the organizer's laptop (not reachable from outside) | pairgoth *pushes* pairings/results/standings to the event site; the operator clicks Publish / Sync | `none` or `sesame` | `webhook.url` + `webhook.secret` |
| **hosted-sesame** | on a reachable server, one event | the event site *drives* pairgoth over its API (create, push the roster, read pairings/results/standings as JSON) | `sesame` (shared password) | `auth.shared_secret` |
| **hosted-SSO-ACL** *(advanced — EGC-scale)* | on a reachable server, many events and operators | the event site is an SSO front with per-operator tournament visibility and id allocation | `external` | `auth.shared_secret`, `auth.external.secret` |

- The two planes are **independent** — configure only what you need. A hosted event that drives the API
  needs no webhook; a laptop that only publishes needs no API auth beyond `sesame`. Configuring two
  directions (and two secrets) is only for setups that genuinely want both.
- A **small organizer** stops at the first one or two rows. The third — per-operator ACL, SSO tickets,
  an admin store — exists only for multi-tournament platforms (the EGC), and is documented apart under
  [External (SSO) authentication](#external-sso-authentication); ignore it otherwise.
- Details by plane: the push side is the [Webhook specification](#pairgoth-webhook-specification); the
  drive side is the [API specification](#pairgoth-api-specification) (incl. bulk roster import).

## Configuration

*How to tune the `pairgoth.properties` file.*

Pairgoth general configuration is done using the `pairgoth.properties` file in the installation folder.

Properties are loaded in this order (later overrides earlier):

1. Default properties embedded in WAR/JAR
2. User properties file (`./pairgoth.properties`) in current working directory
3. System properties prefixed with `pairgoth.` (command-line: `-Dpairgoth.key=value`)

### Environment

Controls the running environment.

```
env = prod
```

Values:
- `dev` - Development mode: enables CORS headers and additional logging
- `prod` - Production: for distributed instances

### Mode

Running mode for the application.

```
mode = standalone
```

Values:
- `standalone` - Both web and API in a single process (default for jar execution)
- `server` - API only
- `client` - Web UI only (connects to remote API)

### Authentication

Authentication method for the application.

```
auth = none
```

Values:
- `none` - No authentication required
- `sesame` - Shared unique password
- `oauth` - Email and/or OAuth accounts
- `external` - SSO delegated to a fronting website

#### Secrets overview

Each secret serves exactly one trust relationship:

| Property | Shared between | Purpose |
|----------|----------------|---------|
| `auth.shared_secret` | view ↔ api webapps | encrypts the API bearers — internal, never leaves pairgoth |
| `auth.sesame` | operators ↔ pairgoth | the shared password of the `sesame` mode |
| `auth.external.secret` | fronting site → pairgoth | encrypts the SSO tickets of the `external` mode |
| `oauth.<provider>.secret` | pairgoth ↔ OAuth provider | the provider's client secret |
| `webhook.secret` | pairgoth → tournament website | authenticates outbound webhook publishing |

#### Shared secret

When running with authentication enabled:

```
auth.shared_secret = <16 ascii characters string>
```

This secret is shared between API and View webapps. In standalone mode it is
auto-generated at startup when not set. In server and client modes, set it
explicitly — to the same value on both sides — for any real authentication mode
(with `auth = none` it is not used).

#### Sesame password

When using sesame authentication:

```
auth.sesame = <password>
```

#### External (SSO) authentication

In `external` mode, pairgoth delegates authentication to a fronting website (an event
site, typically) which owns the accounts and the access rights. The contract:

- The fronting site authenticates the operator, then hands them over to pairgoth on
  `/sso?ticket=<ticket>&goto=<path>`. The ticket is the payload `email:expiryMillis:nonce`,
  AES-encrypted (URL-safe base64) with a secret dedicated to this relationship:

```
auth.external.secret = <16+ ascii characters string>
```

  It must expire shortly (~60 seconds) and is accepted once. `goto` is the relative path
  to land on (default `/index`).
- Sessionless requests are redirected to the fronting site's login page:

```
auth.external.login_url = <url>
```

  with the originally requested path appended as the `goto` parameter, so deep links into
  pairgoth transparently round-trip through the fronting site.
- Tournament visibility is per operator: the store directory contains one directory per
  email, holding ACL symlinks `NNNNNN-name.tour` pointing to the root tournament files.
  The symlinks — and the tournament id allocation — are managed by the fronting site,
  never by pairgoth. A dangling symlink stands for a provisioned, not-yet-created
  tournament: the operator lands on a prefilled creation form.
- Admin accounts bypass the per-operator view and get the unscoped root store:

```
auth.external.admin = <comma-separated emails>
```

- Server-to-server: calling `/sso` with `Accept: application/json` returns
  `{"bearer": ...}` instead of redirecting — an opaque token for direct API calls
  (`Authorization: Bearer ...`). This is how the fronting site's backend, with an admin
  ticket, creates tournaments with explicit ids and reads everything; the internal
  `auth.shared_secret` never leaves pairgoth.

### OAuth configuration

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

### Webapp connector

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

### API connector

Pairgoth API connector configuration.

```
api.protocol = http
api.host = localhost
api.port = 8085
api.context = /api
api.external.url = http://localhost:8085/api
```

Note: In standalone mode, API port defaults to 8080 and context to `/api/tour`.

### SSL/TLS configuration

For HTTPS connections:

```
webapp.ssl.key = path/to/localhost.key
webapp.ssl.cert = path/to/localhost.crt
webapp.ssl.pass = <key passphrase>
```

Supports `jar:` URLs for embedded resources.

### Cleartext HTTP/2 (h2c)

Behind a TLS-terminating reverse proxy, HTTP/2 can be kept end-to-end by letting the proxy speak cleartext HTTP/2 to pairgoth (e.g. haproxy `server ... proto h2`):

```
webapp.h2c = false
```

When enabled, the plain connector accepts both HTTP/1.1 and h2c on the same port: regular clients keep using HTTP/1.1, the proxy connects with h2c prior knowledge. Ignored on an HTTPS connector, where HTTP/2 is already negotiated via ALPN.

### Collaborative editing

Several operators can work on the same tournament at once: changes propagate live to every screen over Server-Sent Events. Results and registration toggles update in place; other affected tabs reload on entry, and a tab with unsaved work prompts to reload or continue read-only.

Live propagation activates when the browser reaches pairgoth over HTTP/2 (SSE needs its multiplexing — HTTP/1.1 browsers cap concurrent connections per origin); otherwise pairgoth degrades gracefully to single-operator editing. In production that means either HTTPS (HTTP/2 via ALPN) or h2c behind a TLS-terminating proxy (above). Development mode (`env = dev`) always activates it.

### Store

Persistent storage for tournaments.

```
store = file
store.file.path = tournamentfiles
```

Values for `store`:
- `file` - Persistent XML files (default)
- `memory` - RAM-based (mainly for tests)

The `store.file.path` is relative to the current working directory.

### Ratings

#### Ratings directory

```
ratings.path = ratings
```

Directory for caching downloaded ratings files.

#### Rating sources

For each rating source (`aga`, `egf`, `ffg`):

```
ratings.<source> = <url>
```

URL override for the rating source. Schemes: `http://`, `https://`, `file://`. If not set, the built-in default URL for that source is used:

- FFG: https://ffg.jeudego.org/echelle/echtxt/ech_ffg_V3.txt
- EGF: https://pairgoth.jeudego.org/egd/allworld_lp.zip (pairgoth-hosted mirror, zipped)
- AGA: TBD

Use this property to point at a local mirror or to a static file when the upstream is unreachable.

#### Ratings freeze

```
ratings.date = YYYY-MM-DD
```

Upper bound for the ratings snapshot to use, applied globally to all sources. Designed for multi-day events where ratings must not drift mid-tournament.

Behaviour:
- `ratings.date` not set, or today is before it: dynamic — latest ratings are fetched as usual.
- Today is on or after `ratings.date`: load the most recent cached snapshot whose date is ≤ `ratings.date`. Cache files past the freeze are kept on disk but ignored at load time.
- Until a cached snapshot dated ≥ `ratings.date` exists, hourly fetches continue (so the cache grows toward the freeze date). Once one exists, fetches stop for that source.
- Set in advance and forget: configure on day -N, freeze takes effect automatically on day 0.

#### Enable/disable ratings

```
ratings.<source>.enable = true | false
```

Whether to display the rating source button in the Add Player popup.

```
ratings.<source>.show = true | false
```

Whether to show player IDs from this rating source on the registration page.

`enable` makes the source available in the Add Player search

Defaults when unset:
- EGF: enabled and shown everywhere
- FFG: enabled and shown only for French tournaments
- Other sources (e.g. AGA, ext): off

#### Source label

```
ratings.<source>.label = <text>
```

Display label for the source's search button and registration column. Defaults to the uppercase source code (e.g. `EGF`); an EGC deployment might set `ratings.ext.label = EGC`.

#### External registry (ext)

```
ratings.ext = <url>
```

Activates a fourth, generic source: a tournament website's roster served as JSON, searchable in the Add Player popup alongside the rating databases. The source is inactive when the property is unset (no behaviour change otherwise). Schemes: `http://`, `https://`, `file://` (the latter is handy for testing).

The URL serves:

```
{ "date": "YYYY-MM-DD", "players": [ { "name", "firstname", "country", "club", "rank", "rating", "ext" }, ... ] }
```

Field conventions match EGF entries: `rank` is a display string (`"4k"`), `rating` an int, `country` a 2-letter code. `ext` is the registry's own player id and lands in the player's external ids (`DatabaseId.EXT`).

Like any source, `ratings.ext.enable`, `ratings.ext.show` and `ratings.ext.label` apply; ext defaults to off and hidden for both. Two behaviours set ext apart, because it is a participant registry rather than a ratings snapshot:

- The global `ratings.date` freeze does **not** apply to ext — freezing it would hide late registrants.
- The refresh-ratings feature (EGD/FFG/AGA) ignores ext — it is not a ratings authority.

Known limitation: roster caches are date-granular (`EXT-yyyyMMdd.json`) and a same-day refetch is a no-op, so a registration made today only appears in pairgoth tomorrow. Same-day walk-ins can be typed manually in the Add Player popup.

#### Rank authority

```
ratings.rank_authoritative = true | false
```

Which of the two imported level fields wins when a [bulk import](#pairgoth-api-specification) entry carries a rank and a rating that disagree (the rating falls outside the rank's 100-point band):

- `false` (default): the rating is authoritative. Both values are imported as sent; the registration form shows the pair as unlinked (the rank is an honorary grade, pairing strength comes from the rating).
- `true`: the rank is authoritative. The rating is snapped to the rank's nominal value (band centre, e.g. 1d → 2100), so every imported player lands with rank and rating linked. In-band ratings are kept as-is (finer-grained). A manual unlink in the registration form does not survive the next sync — level exceptions belong on the source side, or behind the `locked` flag.

Meant for deployments where the event site is the level authority (e.g. an EGC pushing referee-validated ranks).

### SMTP

SMTP configuration for email notifications. Not yet functional.

```
smtp.sender = sender@example.com
smtp.host = smtp.example.com
smtp.port = 587
smtp.user = username
smtp.password = password
```

### Logging

Logging configuration.

```
logger.level = info
logger.format = [%level] %ip [%logger] %message
```

Log levels: `trace`, `debug`, `info`, `warn`, `error`

Format placeholders: `%level`, `%ip`, `%logger`, `%message`

#### Console colour

```
console.color = true | false
```

ANSI colour in the stdout log. Unset = auto-detect: on for a colour-capable terminal (including Windows Terminal), off for legacy consoles, redirected output and services. The `NO_COLOR` / `FORCE_COLOR` environment variables are also honoured.

### Webhook

Pairgoth can push content (pairings, results, standings) to an external
tournament website, and pull registered players from it. See
[Pairgoth Webhook specification](#pairgoth-webhook-specification) for the
endpoint contract a consumer must implement.

```
webhook.url    = https://my-tournament-site.example/api/pairgoth
webhook.secret = a-shared-secret
```

- `webhook.url` — base URL of the consumer's pairgoth-integration endpoint.
- `webhook.secret` — shared secret sent on every request as `X-Pairgoth-Secret` header

Behavior:

- If `webhook.url` is **unset or blank**, no webhook integration runs and the
  related UI buttons (Sync from website, Publish pairings/results/standings)
  are not shown.
- If `webhook.url` is set, pairgoth performs a `GET /health` against it at
  startup. **A failed health check is only logged as a warning** (not fatal):
  the tournament site is normally already running, but a co-located peer (e.g.
  a sibling docker container) may simply not be up yet — pairgoth keeps
  serving, and pushes/pulls retry at runtime. A missing `webhook.secret` while
  `webhook.url` is set is still a fatal misconfiguration.

### Display

```
display.pairing.blackFirst = false
```

When `true`, pairings are shown as "Black vs White" instead of the default "White vs Black".

### Session

```
session.timeout.minutes = 240
```

Overrides the HTTP session idle-timeout default (in minutes).

### Version check

```
version.check = true
```

At startup, pairgoth checks in the background whether a newer version has been published and prints an update hint. Set to `false` to suppress (it stays silent offline anyway).

### Example configurations

#### Standalone development

```properties
env = dev
mode = standalone
auth = none
store = file
store.file.path = tournamentfiles
logger.level = trace
```

#### Client-server deployment

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

## Pairgoth API specification

*To develop your own tools.*

### General remarks

The API expects an `Accept` header of `application/json`, with no encoding or an `UTF-8` encoding. Exceptions are some export operations which can have different MIME types to specify the expected format:
- `application/json` - JSON output (default)
- `application/xml` - OpenGotha XML export
- `application/egf` - EGF format
- `application/ffg` - FFG format
- `text/csv` - CSV format

GET requests return either an array or an object, as specified below.

POST, PUT and DELETE requests return either the 200 HTTP code with `{ "success": true }` (with an optional `"id"` field for some POST requests), or an invalid HTTP code and (for some errors) the body `{ "success": false, "error": <error message> }`.

All POST/PUT/DELETE requests use read/write locks for concurrency. GET requests use read locks.

When authentication is enabled, all requests require an `Authorization` header.

### Synopsis

+ /api/tour                  GET POST            Tournaments handling
+ /api/tour/#tid             GET PUT DELETE      Tournaments handling
+ /api/tour/#tid/part        GET POST            Registration handling
+ /api/tour/#tid/part/#pid   GET PUT DELETE      Registration handling
+ /api/tour/#tid/team        GET POST            Team handling
+ /api/tour/#tid/team/#tid   GET PUT DELETE      Team handling
+ /api/tour/#tid/pair/#rn    GET POST PUT DELETE Pairing
+ /api/tour/#tid/res/#rn     GET PUT DELETE      Results
+ /api/tour/#tid/standings   GET PUT             Standings
+ /api/tour/#tid/stand/#rn   GET                 Standings
+ /api/tour/#tid/explain/#rn GET                 Pairing explanation
+ /api/token                 GET POST DELETE     Authentication

### Tournament handling

+ `GET /api/tour` Get a list of known tournaments ids

    *output* json map (id towards shortName) of known tournaments

+ `GET /api/tour/#tid` Get the details of tournament #tid

    *output* json object for tournament #tid

    Supports `Accept: application/xml` to get OpenGotha XML export.

+ `POST /api/tour` Create a new tournament

    *input* json object for new tournament, or OpenGotha XML with `Content-Type: application/xml`

    Tournament JSON structure:
    ```json
    {
      "type": "INDIVIDUAL",
      "name": "Tournament Name",
      "shortName": "TN",
      "startDate": "2024-01-15",
      "endDate": "2024-01-16",
      "country": "fr",
      "location": "Paris",
      "online": false,
      "rounds": 5,
      "gobanSize": 19,
      "rules": "FRENCH",
      "komi": 7.5,
      "timeSystem": { ... },
      "pairing": { ... }
    }
    ```

    Tournament types: `INDIVIDUAL`, `PAIRGO`, `RENGO2`, `RENGO3`, `TEAM2`, `TEAM3`, `TEAM4`, `TEAM5`

    *output* `{ "success": true, "id": #tid }`

+ `PUT /api/tour/#tid` Modify a tournament

    *input* json object for updated tournament (only id and updated fields required)

    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid` Delete a tournament

    *output* `{ "success": true }`

### Players handling

+ `GET /api/tour/#tid/part` Get a list of registered players

    *output* json array of known players

+ `GET /api/tour/#tid/part/#pid` Get registration details for player #pid

    *output* json object for player #pid

+ `POST /api/tour/#tid/part` Register a new player

    *input*
    ```json
    {
      "name": "Lastname",
      "firstname": "Firstname",
      "rating": 1500,
      "rank": -5,
      "country": "FR",
      "club": "Club Name",
      "final": true,
      "mmsCorrection": 0,
      "egfId": "12345678",
      "ffgId": "12345",
      "agaId": "12345"
    }
    ```

    Rank values: -30 (30k) to 8 (9D). Rating in EGF-style (100 = 1 stone).

    *output* `{ "success": true, "id": #pid }`

+ `POST /api/tour/#tid/part` (with a json **array** body) Bulk-import a roster

    The array form is an idempotent upsert, meant for an event site pushing its whole roster: each
    entry is matched by `id`, else by external id (`ext` → `egf` → `ffg` → `aga`), else created; a
    partial entry merges onto the matched player. The whole roster is applied in one transaction and
    recorded as a single history entry.

    An entry may carry `"locked": true` to mark the player as a **rating exception** (official
    rating known wrong): from then on, that player's `rating`/`rank`/`pro` survive every bulk
    upsert — including this endpoint and the ratings refresh — until an entry with an explicit
    `"locked": false` unlocks them (which applies its own values in one shot). An absent flag
    never changes the lock state. Manual per-player edits (`PUT`) are not restricted.

    When [`ratings.rank_authoritative`](#rank-authority) is set, an entry carrying both `rank` and
    `rating` gets its out-of-band rating snapped to the rank's nominal value before the merge
    (locked players remain immune).

    *output* a journal `{ "success": true, "added": [ "Name Firstname", … ], "updated": [ { "player": "Name Firstname", "changes": "rating 2627→2630, rank 5→6" }, … ], "unchanged": [ "Name Firstname", … ], "failed": [ { "player": "...", "reason": "..." } ], "missing": [ "Name Firstname", … ] }` — each section lists its players (counts are the array lengths); `changes` is a compact field-level diff. E.g. a player already paired in a round the import tries to drop comes back in `failed` rather than aborting the batch. `missing` lists the pre-existing players the payload did not touch: since an import carries the full source roster, those have been removed on the source side — reported only, never deleted (empty for partial payloads, i.e. when `reason` is set).

+ `PUT /api/tour/#tid/part/#pid` Modify a player registration

    *input* json object for updated registration (only id and updated fields required)

    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid/part/#pid` Delete a player registration

    *output* `{ "success": true }`

### Teams handling

For team tournaments (PAIRGO, RENGO2, RENGO3, TEAM2-5).

+ `GET /api/tour/#tid/team` Get a list of registered teams

    *output* json array of known teams

+ `GET /api/tour/#tid/team/#teamid` Get registration details for team #teamid

    *output* json object for team #teamid

+ `POST /api/tour/#tid/team` Register a new team

    *input*
    ```json
    {
      "name": "Team Name",
      "playerIds": [1, 2, 3],
      "final": true,
      "mmsCorrection": 0
    }
    ```

    *output* `{ "success": true, "id": #teamid }`

+ `PUT /api/tour/#tid/team/#teamid` Modify a team registration

    *input* json object for updated registration (only id and updated fields required)

    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid/team/#teamid` Delete a team registration

    *output* `{ "success": true }`


### Pairing

+ `GET /api/tour/#tid/pair/#rn` Get pairable players for round #rn

    *output*
    ```json
    {
      "games": [ { "id": 1, "t": 1, "w": 2, "b": 3, "h": 0 }, ... ],
      "pairables": [ 4, 5, ... ],
      "unpairables": [ 6, 7, ... ]
    }
    ```

    - `games`: existing pairings for the round
    - `pairables`: player IDs available for pairing (not skipping, not already paired)
    - `unpairables`: player IDs skipping the round

+ `POST /api/tour/#tid/pair/#rn` Generate pairing for round #rn

    *input* `[ "all" ]` or `[ #pid, ... ]`

    Optional query parameters:
    - `legacy=true` - Use legacy pairing algorithm
    - `weights_output=<file>` - Output weights to file for debugging
    - `append=true` - Append to weights output file

    *output* `[ { "id": #gid, "t": table, "w": #wpid, "b": #bpid, "h": handicap }, ... ]`

+ `PUT /api/tour/#tid/pair/#rn` Manual pairing or table renumbering

    For manual pairing:
    *input* `{ "id": #gid, "w": #wpid, "b": #bpid, "h": <handicap> }`

    For table renumbering:
    *input* `{ "renumber": <game_id or null>, "orderBy": "mms" | "table" }`

    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid/pair/#rn` Delete pairing for round #rn

    *input* `[ "all" ]` or `[ #gid, ... ]`

    Games with results already entered are skipped unless `"all"` is specified.

    *output* `{ "success": true }`

### Results

+ `GET /api/tour/#tid/res/#rn` Get results for round #rn

    *output* `[ { "id": #gid, "res": <result> }, ... ]`

    Result codes:
    - `"w"` - White won
    - `"b"` - Black won
    - `"="` - Jigo (draw)
    - `"X"` - Cancelled
    - `"?"` - Unknown (not yet played)
    - `"#"` - Both win (unusual)
    - `"0"` - Both lose (unusual)

+ `PUT /api/tour/#tid/res/#rn` Save a result

    *input* `{ "id": #gid, "res": <result> }`

    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid/res/#rn` Clear all results for round

    *output* `{ "success": true }`

### Standings

+ `GET /api/tour/#tid/standings` Get standings after final round

    *output* `[ { "id": #pid, "place": place, "<crit>": value }, ... ]`

    Supports multiple output formats via Accept header:
    - `application/json` - JSON (default)
    - `application/egf` - EGF format
    - `application/ffg` - FFG format
    - `text/csv` - CSV format

    Optional query parameters:
    - `include_preliminary=true` - Include preliminary standings
    - `individual_standings=true` - For team tournaments with individual scoring

+ `GET /api/tour/#tid/stand/#rn` Get standings after round #rn

    Use round `0` for initial standings.

    *output* `[ { "id": #pid, "place": place, "<crit>": value }, ... ]`

    Criteria names include: `nbw`, `mms`, `sts`, `cps`, `sosw`, `sosm`, `sososw`, `sososm`, `sodosw`, `sodosm`, `cussw`, `cussm`, `dc`, `sdc`, `ext`, `exr`, etc.

+ `PUT /api/tour/#tid/standings` Freeze/lock standings

    *output* `{ "success": true }`

### Pairing explanation

+ `GET /api/tour/#tid/explain/#rn` Get detailed pairing criteria weights for round #rn

    *output* Detailed pairing weight analysis and criteria breakdown

    Used for debugging and understanding pairing decisions.

### Authentication

+ `GET /api/token` Check authentication status

    *output* Token information for the currently logged user, or error if not authenticated.

+ `POST /api/token` Create an access token

    *input* Authentication credentials (format depends on auth mode)

    *output* `{ "success": true, "token": "..." }`

+ `DELETE /api/token` Logout / revoke token

    *output* `{ "success": true }`

## Pairgoth Webhook specification

*To integrate pairgoth with a tournament website.*

### General remarks

The webhook is the inverse direction of the [Pairgoth API](#pairgoth-api-specification): pairgoth is the **client**,
the tournament website is the **server**. The direction is fixed because the tournament director can run pairgoth
on a venue laptop behind some NAT or firewall — the website cannot reach pairgoth, but pairgoth can reach the website
outbound.

Pairgoth's role:

- **Pull** registered players from the website on demand (Sync from website).
- **Push** content (pairings, results, standings) to the website when the operator clicks a Publish button.

Configuration is described in [Webhook](#webhook) under the Configuration section. When `webhook.url` is set, pairgoth
performs `GET <webhook.url>/health` at startup; a failed check is logged as a warning, not fatal.

All requests carry an `X-Pairgoth-Secret` header equal to the value of `webhook.secret`. The website **must** validate
it on every `{code}`-scoped endpoint and return `401` on mismatch. `/health` is instance-scoped and **may** be served
unauthenticated; it must expose nothing beyond `status`, `name` and `version`.

JSON responses follow the shape `{ "status": true | false, "message"?: string, … }`. A `false` status reaches the pairgoth UI as the error message.

The path component `{code}` is the tournament's `shortName` — used as a stable, human-meaningful identifier on the website side. `{round}` is a 1-based round number.

### Synopsis

Pairgoth calls the following endpoints, all relative to `webhook.url`:

+ /health                     GET    Health check (auth optional)
+ /players/{code}             GET    Pull registered players
+ /pairings/{code}/{round}    POST   Push pairings or results HTML
+ /standings/{code}/{round}   POST   Push standings HTML
+ /presences/{code}/{round}   POST   Push a referee's presence change back

### /health

+ `GET /health` — health check used at pairgoth startup.

    *output* `{ "status": true, "name"?: string, "version"?: string }`

    The `name` is shown in pairgoth's startup log line (`webhook at <url> healthy: <name>`) — useful for the operator to confirm the right backend.

### /players/{code}

+ `GET /players/{code}` — return all players registered for the tournament.

    *output* `{ "status": true, "players": [ { ... }, ... ] }` on success;
    `{ "status": false, "message": string }` on error (e.g. event not found → 404).

    Player JSON shape (each entry):

    ```json
    {
      "id":        12345,         // stable per-website id (used by pairgoth as DatabaseId.EXT)
      "lastname":  "Doe",
      "firstname": "Jane",
      "country":   "FR",          // ISO-3166 alpha-2; pairgoth normalizes GB → UK
      "club":      "75Pa",
      "rank":      "5k",          // string: "30k".."1k", "1d".."9d", "1p".."9p" (1p..9p doubles as pro flag)
      "rating":    1850,          // optional; if absent, pairgoth derives a default from rank
      "pin":       "12345678",    // optional EGF PIN — used as DatabaseId.EGF
      "rounds":    "1111100000"   // optional, one char per round; '1' = playing, '0' = skip
    }
    ```

    The `id` field is the website's primary key for that player, typically a registration id. Pairgoth stores it as `externalIds[EXT]` and uses it as the **primary** deduplication key on re-sync — taking precedence over `pin`, since a PIN entered wrong on the source side may be corrected later. Without an `id`, players without a PIN duplicate on every re-sync.

    **Re-sync semantics.** Pairgoth matches each website player against existing registrants by external id (EXT > EGF > FFG > AGA). Unmatched players are inserted; matched players are updated last-wins on rank, rating, club, country, name and round participation (`rounds`) — the website is treated as the source of truth. Players whose payload is identical to the current registration are counted as unchanged. Registered players the sync does not touch at all have been removed on the website: they are reported as `removed on website (kept here)` — pairgoth never deletes a player on sync, the operator decides. The operator gets a multi-line report (`X added / Y updated / Z unchanged / …`) at the end.

    One safeguard is server-enforced: pairgoth refuses to drop a player from a round in which they are already paired. Such rejections are surfaced separately as `N blocked — already paired: <name> (round R), …` so the operator can spot a misordered flow. The intended procedure is **freeze the round on the website first, then resync** — that prevents the website from shipping a `rounds` mask that excludes a paired player.

### /pairings/{code}/{round}

+ `POST /pairings/{code}/{round}` — receive pairings or results for a round.

    *Content-Type* `text/html; charset=UTF-8`

    *body* HTML fragment, see [Published payload](#published-payload).

    *output* `{ "status": true }` on success; `{ "status": false, "message": string }` on error.

    Both the Pairings tab's "Publish to website" and the Results tab's "Publish to website" hit this endpoint. The Results variant differs only in that its rendered table includes a `Result` column. The website should overwrite previous content for `(code, round)` on each call.

    For TEAM tournaments, the Pairings publish renders team-vs-team rows and the Results publish renders the per-board breakdown. Both ship to the same endpoint; the website sees whichever was published most recently.

### /standings/{code}/{round}

+ `POST /standings/{code}/{round}` — receive standings as of a given round.

    *Content-Type* `text/html; charset=UTF-8`

    *body* HTML fragment, see [Published payload](#published-payload). For TEAM* tournaments, the body contains both the team standings table and the individual standings table, each wrapped in a `<div class="standings-section team-standings">` / `<div class="standings-section individual-standings">`.

    *output* `{ "status": true }` on success; `{ "status": false, "message": string }` on error.

### /presences/{code}/{round}

+ `POST /presences/{code}/{round}` — mirror a referee's per-round presence change back to the website.

    *Content-Type* `application/json; charset=UTF-8`

    *body* a JSON array of presence entries for the given round:

    ```json
    [ { "id": 12345, "present": false } ]
    ```

    The `id` is the website's own player id (what pairgoth stores as `externalIds[EXT]` and receives as `id` from [`/players/{code}`](#playerscode)). `present` is `true` when the player plays the round, `false` when they skip it.

    *output* `{ "status": true }` on success; `{ "status": false, "message": string }` on error.

    The website owns presences (online (un)registration), but the referee may override one on the floor — typically a no-show. Pairgoth pushes only the **changed** entry, for the affected round. The two directions are deliberately asymmetric:

    - **Removal** (the referee marks a player *absent* for a round): applied locally first, then mirrored back best-effort. A push failure is non-fatal — the local change stands and the operator is warned to update the website by hand.
    - **Addition** (the referee marks a player *present* again): the local change is **gated** on a confirmed push, because the website owns each player's registration choices and may legitimately refuse a round they are not registered for. Pairgoth pushes first and applies the change locally only on success (`{ "status": true }`); on a rejection — or an unreachable website — the local toggle is **aborted** and the website's reason is surfaced to the operator. To accept the round, return `{ "status": true }`; to refuse it, return `{ "status": false, "message": "…" }` with the reason.

    Only website-sourced players (those that have an `id`/`EXT`) are gated or pushed; presence changes on referee-added players apply locally with no push. **Player removal is never synced at all** (see below).

    Player **removal** is the one operation deliberately left unsynced in both directions: a referee deleting a player in pairgoth, or a registrant withdrawing on the website, must be mirrored manually on the other side. (A website deletion that shipped via `/players/{code}` would otherwise have to delete a player pairgoth may already have paired.)

### Published payload

The HTML pushed to `/pairings/...` and `/standings/...` is **self-contained**: it carries its own `<style>` block and is wrapped in a `.pairgoth-published` container, so the website can drop it into any container without writing CSS:

```html
<style>@layer pairgoth-published {
  /* table sizing, headers, zebra rows, etc. */
}</style>
<div class="pairgoth-published">
  <!-- table or sections -->
</div>
```

Properties:

- The styles are inside a CSS `@layer` named `pairgoth-published`. **Unlayered styles in the consuming page take precedence over the layer**, so the website's own theme wins automatically — the published styles only show through where the website hasn't styled.
- All selectors are scoped under `.pairgoth-published`, so they don't leak.
- Each result cell carries `data-result="<code>"` where `<code>` is one of `?` `w` `b` `=` `X` `#` `0`. The website can re-style results without parsing the rendered string (`1-0` / `½-½` etc.).
- Each table row's `td.t` cell carries `data-table="<n>"` (the table number).

### Error responses

For all webhook endpoints, error responses should return:

- `401` for invalid or missing `X-Pairgoth-Secret`.
- `404` for unknown `{code}` (event not found on the website).
- `4xx`/`5xx` with body `{ "status": false, "message": string }` for other errors. The `message` is surfaced in the pairgoth UI.

If the website returns a non-2xx with no body, pairgoth synthesizes a `{ "status": false, "message": "upstream <status> with no body for <url>" }` so the browser-side parser does not choke.

