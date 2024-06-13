# Pairgoth API specification

## General remarks

The API expects an `Accept` header of `application/json`, with no encoding or an `UTF-8` encoding. Exceptions are some export operations which can have different MIME types to specify the expected format.

GET requests return either an array or an object, as specified below.

POST, PUT and DELETE requests return either the 200 HTTP code with `{ "success": true }` (with an optional `"id"` field for some POST requests), or and invalid HTTP code and (for some errors) the body `{ "success": false, "error": <error message> }`.

## Synopsis

+ /api/tour                GET POST            Tournaments handling
+ /api/tour/#tid           GET PUT DELETE      Tournaments handling
+ /api/tour/#tid/part      GET POST            Registration handling
+ /api/tour/#tid/part/#pid GET PUT DELETE      Registration handling
+ /api/tour/#tid/team      GET POST            Team handling
+ /api/tour/#tid/team/#tid GET PUT DELETE      Team handling
+ /api/tour/#tid/pair/#rn  GET POST PUT DELETE Pairing
+ /api/tour/#tid/res/#rn   GET PUT DELETE      Results
+ /api/tour/#tid/standings GET                 Standings
+ /api/tour/#tid/stand/#rn GET                 Standings

## Tournament handling

+ `GET /api/tour` Get a list of known tournaments ids
    
    *output* json map (id towards shortName) of known tournaments (subject to change)

+ `GET /api/tour/#tid` Get the details of tournament #tid
 
    *output* json object for tournament #tid

+ `POST /api/tour` Create a new tournament
 
    *input* json object for new tournament (see `Tournament.fromJson` in the sources)
 
    *output* `{ "success": true, "id": #tid }`

+ `PUT /api/tour/#tid` Modify a tournament

    *input* json object for updated tournament (only id and updated fields required)
 
    *output* `{ "success": true }`

## Players handling

+ `GET /api/tour/#tid/part` Get a list of registered players
 
    *output* json array of known players

+ `GET /api/tour/#tid/part/#pid` Get regitration details for player #pid

    *output* json object for player #pid

+ `POST /api/tour/#tid/part` Register a new player
 
    *input* `{ "name":"..." , "firstname":"..." , "rating":<rating> , "rank":<rank> , "country":"XX" [ , "club":"Xxxx" ] [ , "final":true/false ] [ , "mmsCorrection":0 ] }`
 
    *output* `{ "success": true, "id": #pid }`

+ `PUT /api/tour/#tid/part/#pid` Modify a player registration
 
    *input* json object for updated registration (only id and updated fields required)
 
    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid/part/#pid` Delete a player registration
 
    *input* `{ "id": #pid }`
    
    *output* `{ "success": true }`

## Teams handling

+ `GET /api/tour/#tid/team` Get a list of registered teams
 
    *output* json array of known teams

+ `GET /api/tour/#tid/team/#tid` Get regitration details for team #tid

    *output* json object for team #tid

+ `POST /api/tour/#tid/team` Register a new team
 
    *input* json object for new team
 
    *output* `{ "success": true, "id": #tid }`

+ `PUT /api/tour/#tid/team/#tid` Modify a team registration
 
    *input* json object for updated registration (only id and updated fields required)
 
    *output* `{ "success": true }`

+ `DELETE /api/tour/#tid/team/#tid` Delete a team registration
 
    *input* `{ "id": #tid }`
    
    *output* `{ "success": true }`


## Pairing

+ `GET /api/tour/#tid/pair/#rn` Get pairable players for round #rn
 
    *output* `{ "games": [ games... ], "pairables:" [ #pid, ... of players not skipping and not playing the round ], "unpairables": [ #pid, ... of players skipping the round ] }`

+ `POST /api/tour/#tip/pair/#n` Generate pairing for round #n and given players (or string "all") ; error if already generated for provided players
 
    *input* `[ "all" ]` or `[ #pid, ... ]`
 
    *output* `[ { "id": #gid, "t": table, "w": #wpid, "b": #bpid, "h": handicap }, ... ]`

+ `PUT /api/tour/#tip/pair/#n` Manual pairing (with optional handicap)
 
    *input* `{ "id": #gid, "w": #wpid, "b": #bpid, "h": <handicap> }`

    *output* `{ "success": true }`

+ `DELETE /api/tour/#tip/pair/#n` Delete pairing for round #n and given players (or string "all") ; games with results entered are skipped
 
    *input* `[ "all" ]` or `[ #gid, ... ]`
 
    *output* `{ "success": true }`

## Results

+ `GET /api/tour/#tip/res/#rn` Get results for round #rn
 
    *output* `[ { "id": #gid, "res": <result> } ]` with `res` being one of: `"w"`, `"b"`, `"="` (jigo), `"x"` (cancelled),`"?"` (unknown), `"#"` (both win), or `"0"` (both loose).
 
+ `PUT /api/tour/#tip/res/#rn` Save a result (or put it back to unknown)
 
    *input* `{ "id": #gid, "res": <result> }` with `res` being one of: `"w"`, `"b"`, `"="` (jigo), `"x"` (cancelled)
 
    *output* `{ "success": true }`

+ `DELETE /api/tour/#tip/res/#rn` Clear all results (put back all results to unknown)

  *input* none

  *output* `{ "success": true }`

## Standings

+ `GET /api/tour/#tid/stand/#rn` Get standings after round #rn (or initial standings for round '0')
 
    *output* `[ { "id": #pid, "place": place, "<crit>": double }, ... ]`
      where `<crit>` is the name of a criterium, among "score", "nbw", "mms", "sosm", "sososm", ...

## Authentication

+ `GET /api/token` Get the token of the currently logged user, or give an error.

+ `POST /api/token` Create an access token. Expects an authentication json object.

+ `DELETE /api/token` Delete the token of the currently logged user.

