# Pairgoth API specification

## General remarks

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

## Synopsis

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

## Tournament handling

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

## Players handling

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

+ `PUT /api/tour/#tid/part/#pid` Modify a player registration

    *input* json object for updated registration (only id and updated fields required)

    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid/part/#pid` Delete a player registration

    *output* `{ "success": true }`

## Teams handling

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


## Pairing

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

## Results

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

## Standings

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

## Pairing explanation

+ `GET /api/tour/#tid/explain/#rn` Get detailed pairing criteria weights for round #rn

    *output* Detailed pairing weight analysis and criteria breakdown

    Used for debugging and understanding pairing decisions.

## Authentication

+ `GET /api/token` Check authentication status

    *output* Token information for the currently logged user, or error if not authenticated.

+ `POST /api/token` Create an access token

    *input* Authentication credentials (format depends on auth mode)

    *output* `{ "success": true, "token": "..." }`

+ `DELETE /api/token` Logout / revoke token

    *output* `{ "success": true }`
