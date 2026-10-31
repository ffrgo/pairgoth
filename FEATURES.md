# Pairgoth features

Status as of 0.26 (2026-06-22), established from the code. ✅ = implemented, 🟡 = partial, ❌ = missing.
Items marked *(stub)* are declared in the model or UI but do nothing yet.

## Implemented

### Tournaments
- ✅ Individual tournaments, plus pair/team formats: Pair Go, Rengo (2, 3 players), Team (2 to 5 boards).
- ✅ Team tournaments: board-by-board individual games, team result from board wins, colour cascade on team
  colour swap, per-board colour override from the result screen, team participation toggle per round.
- ✅ Rules: AGA, French, Japanese, Chinese; goban 9/13/19; komi.
- ✅ Time systems: Fischer (with optional max time), Canadian byo-yomi, Japanese byo-yomi, sudden death;
  live "adjusted time" preview, used for the EGF tournament class (A/B/C/D/X).
- ✅ Online / offline flag, number of rounds, table exclusions per round, table renumbering.

### Pairing
- ✅ Swiss and McMahon, using OpenGotha's weight-based algorithm (max-weight matching via JGraphT), validated
  against OpenGotha weight dumps of real tournaments.
- ✅ Seeding methods: split & fold, split & random, split & slip.
- ✅ Configurable base / main / secondary criteria, draw-up/draw-down, handicap parameters (correction,
  ceiling, ...), McMahon bar and floor.
- ✅ Geographic criteria: avoid same country / club / family, main-club adjustment with detection threshold.
- ✅ Partial pairing (selected players), manual game edit, unpairing, byes, skipped rounds per player.
- ✅ "Explain" page showing the weights behind a pairing.

### Players and ratings
- ✅ Registration with search in EGF, FFG and AGA rating lists (downloaded and indexed locally); EGD access
  through a proxy (Cloudflare).
- ✅ Refresh ratings of registered players; freeze-by-date rating snapshot; configurable rating sources.
- ✅ Rating ↔ rank linking (or honorary grade when unlinked), pro ranks, preliminary vs final registration.

### Standings and results
- ✅ Placement criteria: NBW, MMS, SCOREX, RANK, RATING, SOS/SOSM and their −1/−2 variants, SODOS, SOSOS, CUSS.
- ✅ Result entry, standings per round, individual standings for team tournaments, option to drop players
  who never played.
- ✅ Printable registration list, result sheets, published pairings/results pages.

### Import / export
- ✅ Import: OpenGotha XML, MacMahon39, Pairgoth JSON; bundled example tournaments.
- ✅ Export: OpenGotha XML, Pairgoth JSON, EGF results (`.h9` / `.h<correction>`), FFG results (`.tou`), CSV.
- ✅ French tournaments: unlicensed players highlighted, reminder and mailto to send results to the FFG.

### Platform
- ✅ Deployment modes: standalone (single jar), server (API only), client (UI only); Docker image; installer.
- ✅ Authentication: none, shared password ("sesame"), OAuth (FFG, Google, Facebook, Instagram, Twitter);
  per-user tournament storage with OAuth.
- ✅ Storage: in memory or JSON files.
- ✅ Live updates between browsers (server-sent events).
- ✅ Webhooks to sync registrations from / publish pairings, results and standings to a tournament website.
- ✅ UI in English, French, German, Korean.
- ✅ Online documentation rendered from `doc/` (reference).
- ✅ HTTPS / HTTP2 support in the embedded server.

## Missing or partial

### Pairing and placement
- ❌ Round-robin pairing *(stub)*: `RoundRobin.solver()` is `TODO("not implemented")`; the UI option is
  commented out.
- ❌ Placement criteria declared but always empty *(stub)*: CATEGORY, STS (Strasbourg score), CPS (cup score),
  SOSTS, EXT / EXR (attempted / succeeded achievements), SDC / DC (direct confrontation). Using CATEGORY in
  the pairing weights throws (`TODO()` in `BasePairingHelper`).
- ❌ Categories (e.g. split by rank groups) in Swiss pairing.
- ❌ Club groups in the geographic criterion (`commonGroup = false // TODO`).
- 🟡 Pairing randomness cannot be seeded from configuration (`Random(/* seed from properties - TODO */)`).
- 🟡 Team tournaments: no "team of individuals" pairing mode (`CB TODO team of individuals pairing`).

### Tournament management
- 🟡 No checks when lowering the number of rounds after games were played (`TournamentHandler`).
- 🟡 OpenGotha import: always imported as an individual tournament; bye players are not imported.
- 🟡 Pairings can only reliably be edited in the last round (the restriction is commented out).

### Security and robustness
- ❌ No brute-force protection on login (`AuthFilter`).
- 🟡 OAuth `state` parameter is not checked (`OAuthHelper`).
- 🟡 Several code paths use the JVM default charset (AES secrets, test fixtures); on Windows the test suite
  used to fail until surefire was forced to UTF-8.

### Documentation and tests
- 🟡 Tutorial still in preparation; the reference is described as too technical (CHANGELOG 0.24).
- ❌ No tests for `view-webapp` (templates, translations, ratings import, OAuth, webhooks) or `webserver`
  (property resolution, derived external URLs).
- ❌ No browser / end-to-end tests of the JS UI.

### Known bugs (found while adding tests)
- `LinkedHashBiMultiMap.remove()` is bypassed when called through the `Map` interface (no JVM bridge
  method), and `put()` does not detach a value from its previous key, so `inverse` goes stale. Latent: nothing
  reads `inverse` today. Disabled tests in `pairgoth-common/src/test/kotlin/CommonUtilsTest.kt`.
- Manual game edit (`PUT /api/tour/{id}/pair/{round}`): checks such as "already in another game" or
  "not final" run after the game was modified, so a refused edit is still applied in memory.
  `PairingTests` relies on this to force OpenGotha pairings game by game, so it needs an atomic
  "re-pair these games" API before it can be fixed.
