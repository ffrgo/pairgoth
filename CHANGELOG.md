# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
<!--
and this project *will* adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with its 1.0.0 release.
-->

## [Unreleased]

### Added

- Pairing a fresh round with an external website configured now asks for confirmation when no roster sync happened since the previous pairing (stale presences guard).
- The wins-based tie-breaks (SOSW, SOSWM1, SOSWM2, SOSOSW, SODOSW) now really sum opponents' wins, without handicap adjustment, in every tournament type — in a Mac-Mahon tournament they used to silently evaluate as their MMS-based cousins (SOSM & co). Ranking on NBW + SOSW + SOSOSW gives a handicap-blind "swiss" placement inside a handicapped Mac-Mahon.

- Standings: direct confrontation placement criteria (DC, SDC), OpenGotha-compatible; the standings tab now offers four placement-criterion slots (a slot left on NONE is ignored).
- Standings: EGFDC placement criterion, the European Go Federation's Direct Comparison — wins among the tied players only, zero for the whole group unless they all played the same number of games against each other, applied again on whoever is still tied.
- Bulk roster import: `POST /api/tour/{id}/part` with a json array upserts a whole roster in one idempotent request; the in-app Sync-website / Refresh-ratings / Mac-Mahon-reset actions use it too (one history entry per roster operation).
- Version check at startup, suppressible with `version.check = false`.
- Documentation split per audience: reference (model), pairing (new), technical (new: configuration, API and webhook specifications, deployment profiles), hands-on tutorial.
- Optimal-pairing navigator: when several optimal pairings of equal weight exist, a prev/next line on the pairing tab browses them (GitLab #42).
- Undo: an "Undo" button in the header lists past actions, labelled and timestamped; restore the tournament to just before any of them.
- Collaborative editing: several operators can work the same tournament at once, changes propagated live over Server-Sent Events (requires HTTP/2; degrades gracefully to single-operator editing).
- "external" auth mode: a fronting website owns the accounts and hands operators over with single-use SSO tickets, with per-operator tournament visibility (ACL symlinks) and an admin list; its backend obtains an API bearer through the same door.
- Standings: country and club column display toggles (the club column is new), remembered in the browser and carried into the published HTML standings.
- Optional cleartext HTTP/2 (`webapp.h2c`) on the plain connector, letting a TLS-terminating reverse proxy keep HTTP/2 end-to-end; HTTP/1.1 clients are still served.
- Presence write-back: a referee toggling a website-sourced player's per-round participation pushes the change back to the website (`POST <webhook.url>/presences/{code}/{round}`), so a later resync keeps it. Marking a player *present* again is gated on the website accepting it (it may refuse a round the player isn't registered for); marking *absent* commits locally then mirrors back best-effort.
- Level lock: a bulk-import entry may carry `"locked": true` to mark a player as a rating exception — their rating/rank/pro then survive website syncs and ratings refreshes until an explicit `"locked": false`. Manual edits still apply; no UI, the flag is meant to be set by the event site.
- Bulk roster import reports players removed on the source side: registered players a full-roster import leaves untouched come back in a `missing` journal section (shown as "removed on website (kept here)" after a sync) — reported only, never deleted.
- `ratings.rank_authoritative`: on bulk roster imports, make the imported rank the level authority — a rating outside the rank's band is snapped to the rank's nominal value, so imported players always land with rank and rating linked (default off: the rating stays authoritative and discrepancies show as unlinked honorary grades).
- External registry player source (`ratings.ext = <url>`): a tournament website's roster, served as JSON, becomes searchable in the Add Player popup alongside the rating databases (inactive when the property is unset). It is a participant registry, not a ratings authority, so the `ratings.date` freeze and the ratings refresh both leave it alone; `ratings.<source>.enable`/`.show`/`.label` configure it like any source (off and hidden by default). The roster is polled hourly and same-day registrations show up at the next poll.
- `nbwValueAbsent` pairing parameter (Advanced parameters › Handling of players absent from a round): what a non-played round is worth in the number of wins, the swiss counterpart of `mmsValueAbsent`. Default 0 as in OpenGotha; the EGF tournament system rules give ½ when the tournament rules agree. OpenGotha files carrying a non-zero "NBW for absent player" are now imported instead of being refused.
- Players-are-playing guard: once a round's pairing has been made public — result sheets or the pairing tab printed, pairings published to the website, or a first result entered (screen-only tournaments) — a global unpair or an optimal-pairing navigation asks for confirmation before destroying it. Partial unpairs, single-game edits and table renumbering stay unguarded (they are the mid-play repair tools).
- Server-restart detection (collaborative mode): the event stream greets each subscriber with a boot id, so every open tournament page notices a webapp restart and reloads itself — or warns first when a reload would lose on-screen work — picking up redeployed code and fresh sessions without manual hard reloads. Event ids are seeded with the boot time, so a replay cursor from a previous boot can no longer be mistaken for a valid position in the new one.

### Changed

- The NBW criterion is now the EGF "Number of Wins Score" everywhere — games won (a jigo counting half) plus what the tournament gives for a non-played round, rounded down like the Mac-Mahon score — instead of a raw win count on the standings side and something slightly different on the pairing side.
- Standings: an "Inactive players" display toggle beside the country/club ones hides players who never played a real game (never paired, or byes only) — on screen, in print, and in every published format alike. The Publish dialog checkboxes are gone: exports follow the on-screen state, and published standings never include unconfirmed preliminary players. Places are recomputed among the remaining players (the server-side exclusion used to keep the full-standings numbering, leaving gaps).
- Loaded tournaments are cached in memory across requests; hand-edits to the `.tour` files are still picked up.
- A failed webhook health check at startup is now a warning instead of a fatal error, so a co-located webhook peer (e.g. a sibling container) that isn't up yet no longer takes pairgoth down. A missing `webhook.secret` is still fatal.
- Rank-to-rating conversion now anchors ranks at the EGD band centre (1d = 2100) instead of the weak edge (2050), so a rank-derived rating tolerates ±49 points of drift without flipping rank.

### Fixed

- The cumulative score (CUSS) counted every round but the last twice as much as it should: the running total was added to itself at each step (after three rounds a first-round win weighed 4 instead of 3).
- SOS-1 and SOS-2 (SOSWM1/SOSMM1, SOSWM2/SOSMM2) now ignore the round of *smallest* value, as the EGF tournament system rules define them and as OpenGotha computes them — they used to drop the largest one, i.e. the strongest opponent, turning the tie-break upside down. A round without an opponent (bye or missed round) is a candidate for the drop like any other, instead of being added back after the fact.
- A drawn team match (equal board sums) is now recorded as a draw — half a point for each team, as the EGF tournament system rules prescribe — instead of staying "unknown", which read as a match still being played and scored nothing for either team. A match is only called a draw once every board is in.
- A jigo (½-½) is worth half a point to each player, as the EGF tournament system rules prescribe. It used to be worth nothing anywhere but in the EGFDC tie-break: entering ½-½ silently scored a double loss in NBW, MMS, SOS and SODOS, and an OpenGotha tournament containing a jigo imported with different standings than it had.
- An unexpected server exception during an API call now comes back as the standard JSON error instead of the container's HTML error page — which the browser used to render as an *empty* red error box (HTTP/2 responses carry no reason phrase to fall back on).
- Static scripts and stylesheets are cache-busted by content hash instead of release version: redeploying the same version (venue hotfixes) used to leave browsers running stale cached scripts until a manual hard reload.
- Syncing from the website can no longer unregister the players of a team tournament: team registrations happen on paper (the website cannot register teams), so its roster — typically empty — is not authoritative there. The Sync button is replaced by Refresh-ratings on team tournaments, and the server ignores the missing-players section for them regardless of the client.
- A late arrival can now join an already paired team: the new member is automatically marked as sitting out the rounds the team has already been paired in (the pairing stays untouched), instead of the edit being refused with "team is playing round #N".
- Team creation and edition now refuse players already belonging to another team: a create-team response lost in transit and resubmitted used to silently duplicate the team, leaving its players pairable twice (the team buttons also stay disabled while a request is in flight).
- Streamed API responses containing characters that need JSON escaping (double quotes, backslashes, control characters) were corrupted — or crashed the request — by a from/to vs offset/length mismatch in the JSON writer adapter.
- Same-club avoidance no longer treats club-less players as clubmates: an empty club or a placeholder ("xxxx", "NoCb") never matches, and such players can't be detected as the host club either.
- The Mac Mahon groups dialog of a published tournament showed groups keyed on *final* scores (mid-field players around the bar, no top player in sight): the frozen standings snapshot now only serves the final round, and earlier rounds — notably round 0, whose MMS is the initial Mac Mahon score the dialog keys on — are computed again. The bar+1 list is also a catch-all now: corrections beyond +1 (OpenGotha imports) stay visible there instead of vanishing.
- A `ratings.date` freeze predating every cached ratings snapshot no longer breaks the hourly ratings refresh with an unexplained exception (and player search with it): the affected source now logs a clear warning and is reported unavailable.
- Mac Mahon group edits and ratings refreshes are now labelled as such in the undo/history list, instead of all sharing the roster-import label (they were already snapshotted and undoable, just indistinguishable).
- The ratings refresh no longer overwrites honorary ranks (rank decoupled from rating in the edit form): for those players only the rating is refreshed, and the report lists them separately.
- SSO tickets and API bearers are encoded in UTF-8 regardless of the platform default charset.
- Standalone with authentication no longer requires an explicit `auth.shared_secret`: the launcher generates it once for both webapps (each webapp used to generate its own, breaking the internal token exchange).
- The tournament store directory is created at API startup rather than on first use.
- Docker packaging refreshed: current LTS Java image, configuration read from `docker/pairgoth.properties`, `run.sh` picks up a freshly built engine.
- Website re-sync no longer crashes when a player's round participation changed length: json array comparison was broken in essential-kson (crash on shorter, false equality on longer) — fixed upstream, dependency bumped 2.4 → 2.15 (Kotlin toolchain 2.1 → 2.3 to match).
- Player search over a single source (or filtered by country) no longer pads the results up to the 20-hit cap with non-matching entries of that source (Lucene `BooleanQuery` `minimumNumberShouldMatch` fix in `PlayerIndex`).
- The Advanced-parameters dialog no longer reloads the page on Update or Cancel, so pending edits in the main tournament form survive a trip through the dialog (they used to be silently discarded). Its updates now dispatch a dedicated `PairingParamsUpdated` event, labelled "Edit pairing parameters" in the undo list.
- Editing the tournament form of a team tournament left the teams bound to the pre-edit tournament object: player changes made afterwards (like benching a substitute) were invisible to team pairability until the tournament was reloaded from disk — a fully-manned team could show as unpairable while the roster displayed the skip. Teams are now rebuilt on the updated tournament.
- A team Mac Mahon tournament with SCOREX as first placement criterion no longer breaks the whole tournament page (the standings table adds an MMS column in that case, but team standings rows carried no MMS value — the resulting template error blanked the page for good until the criteria were reverted by hand).
- Editing tournament settings (including a standings criteria change) no longer resets the last-sync/last-pairing timestamps, which silently disarmed the stale-presences confirmation on the next pairing.
- Default pairing parameters realigned on OpenGotha's per-system presets: a new Swiss tournament now seeds split-and-slip for all rounds (the creation form preselected split-and-random/split-and-fold, OpenGotha's raw pre-preset values); Mac Mahon seeds split-and-slip after round 1 (was split-and-fold) and gets the intended no-handicap threshold (1d) and secondary-criteria behaviour whether or not the request spells them out. Partial API payloads now fall back to the pairing-type defaults instead of type-blind ones. Existing tournaments keep their stored parameters.
- Entering a team-tournament result no longer fails with "Team game not found" after a server restart: the game id counter was restored from team games only, ignoring the individual board games drawn from the same sequence, so pairing the next round could reissue board ids already used by a previous round — a result on such a board then resolved to the wrong round's match. The restore now scans board ids too, and result propagation looks the board up within its own round, which also makes tournament files already carrying recycled ids harmless.

## [0.26] - 2026-06-22

### Added

- Team tournaments: a board's colours can be overridden from the result screen (swap), without changing the match result.

### Fixed

- Team tournaments: fixed team tournament results behavior
- Team tournaments: editing a paired team match no longer wipes the entered board results ; a table move keeps them, swapping the two teams' colours cascades to every board, and only a genuinely new matchup rebuilds the boards.
- Team tournaments: deleting a player who belongs to a team is now refused — it used to leave a dangling member and make the whole tournament unloadable.
- Manual game edit: the white player was validated against the black id (copy-paste), letting an invalid white through.
- A failed view→api call now logs the method, URL and `api.external.url` source, instead of a bare connection error.
- Team tournaments: toggling a team's participation on the pairing page now drops/restores all of its players for that round (a team has no skip of its own).
- EGF export: even-game tournaments (swiss, or McMahon at zero correction) now get the `.h9` extension instead of `.h0`.
- Changing the standalone port (`webapp.port`) no longer requires updating `webapp.external.url`/`api.external.url` by hand: they are derived from the connector when not explicitly set.
- A malformed or stale API bearer now gets a 401 instead of a 500.

## [0.25] - 2026-05-28

### Added

- French tournaments: unlicenced player licences are displayed in red.
- Option to drop players who didn't play any game (on by default).

### Changed

- Rating/rank: when unlinked, the rank becomes an honorary grade — pairing always uses the rating-derived rank.
- EGD ratings are now fetched through a proxy to bypass Cloudflare bot protection; Refresh Ratings feature polished.
- Preliminary players shown in italic.
- Pretty-print tournament JSON export.
- Reviewed terminal colors / no-colors handling.
- Synchronized config-properties documentation between code, `doc/` and `pairgoth.properties.example`.

### Fixed

- Negative ratings are now allowed as input.
- Chain button display problem on older Firefox versions.
- Toggling final/preliminary in player edit now enables Save.
- Suppressed a spurious invalid-round error.

## [0.24] - 2026-05-22

### Added

- Webhook feature: allows to connect Pairgoth to a tournament or EGC website.
- Chained rating/rank toggle in the player dialog (link/unlink rating and rank).
- Pro ranks handling.
- Ratings: button to refresh ratings of registered players from EGD/FFG; freeze-by-date for the rating snapshot used by a tournament; configurable ratings source URLs.
- Online documentation with markdown rendering from the `doc/` folder. Reference ready (yet too much of a technical doc), Tutorial still in preparation.
- Adjusted-time preview shown live below the time-settings row (and added as a comment line in `.h9` exports).
- Geographic parameters: explicit "main-club adjustment" toggle (off by default) with a configurable detection threshold and a live "detected main club" readout in the parameters dialog.
- `session.timeout.minutes` property override for the HTTP session idle timeout (WAR default raised to 240 min).
- `display.pairing.blackFirst` property to set the Black-vs-White display order at the deployment level (replaces the per-user cookie).
- Sync-from-website now updates already-registered players (last-wins on rank, rating, club, country, name, round participation, external ids) and returns a structured report (added / updated / unchanged / blocked / failed).
- French tournaments: reminder in the publish dialog to send the EGF and FFG result files to `echelle@jeudego.org` (with a pre-addressed mailto link).

### Changed

- Case of players names is now aligned with the expected EGF / FFG formats and encodings.
- Results page: winner / loser colors switched from OpenGotha's darkred/blue to muted green / dimmed gray.

### Fixed

- `avoid same family` and `main club adjustments` parameters were not persisted.
- Club-code casing regression in `.tou`/`.h` exports re-introduced in 0.22 (legacy `toCapitals` was lowercasing FFG club codes like `75Al`).

## [0.23] - 2025-11-30

### Added

- `avoidSameFamily` geographic criterion (avoid pairing players from the same club with the same family name).
- Per-user preference for "Black vs White" display order

### Fixed

- Results display for PAIRGO and RENGO tournaments.

## [0.22] - 2025-11-29

### Added

- MacMahon 3.9 file import support.
- EGD PIN appended to `.h9` export rows.
- Local-club nuanced geographic criteria: when a club gathers > 40% of the field, same-club avoidance is relaxed between locals (full / half / no bonus depending on local-vs-stranger configuration).

### Fixed

- Null `teamName` in team-tournament registration view.

## [0.21] - 2025-11-29

### Added

- Team-of-individuals tournament support (teams composition page, display of individual standings below team standings, constraints on team updates).
- Beta of the *explain* page (per-pair pairing-weight breakdown, heat map).
- `PairingListener` class to collect or print weights once (avoids double-computation in tests).
- API / configuration / model documentation refresh.

### Changed

- Upgrade Kotlin to 2.1.21.
- Use nicer HTTP headers when querying ratings.
- Normalize country code to UK instead of GB on incoming data.

### Fixed

- EGF/FFG export name case: Title_Case instead of UPPERCASE (per Sylvain's August 2024 report — corrected again in 0.24).
- `.tou` and `.h` export format issues (inner `name=`, country code casing, version coherence).
- Treat zero byoyomi / zero increment as Sudden death in the time-system comment.
- Stale Lucene reader after ratings index rebuild.
- Race condition in player search-index synchronization.
- Ratings fetch: don't request brotli compression.

## [0.20] - 2025-05-16

### Added

- Add a threshold in main club proportion after which geographic criteria are not applied

### Changed

- Reduce default value of the white/black balance weight from 1e6 to 1e3

## [0.19] - 2025-01-20

Maintenance release. Some tests still need some fixing.

### Added

- Added *this* changelog

### Changed

- Same behaviour than latest opengotha for detRandom: symmetric for pairings, asymmetric for colors
- Refactor pairing tests for better readability

### Fixed

- Correction of ByePlayer choice in Swiss system
- MM floor and bar were resetted to default values when editing advanced parameters
- Review DUDD

## [0.18] - 2024-12-02

Maintenance release.

### Fixed

- Choosing *ScoreX* placement parameter in a Swiss tournament would break the interface

## [0.17] - 2024-09-09

Maintenance release

### Changed

- Use 'Japanese byo-yomi' rather than 'Standard byo-yomi' everywhere

### Fixed

- Default displayed round feature was broken
- SOS and friends were displayed as 0 in some cases
- Default date format was broken in the en-US locale

## [0.16] - 2024-09-05

This is a major release which integrates all the additions and fixes coded during the EGC 2024.

### Added

- Review Korean translation, thanks to Oh Chimin
- Add config options to enable/disable and show/hide egf & ffg ratings (defaults depend on tournament country)
- Choose a round to display by default (first incomplete one, or last for the standings)
- Add an option to *freeze* the standings for the last round. Once frozen, names, clubs, levels and even pairings can be changed, but the scores and the standings will stay the same.
- Double-click on result set it back to unknown
- Show previous games on focused pairable on the pairings tab
- Add ScoreX standings parameter
- Display a mouse-over popup tooltip with the opponent name in the standings
- Allow sorting in the standings
- Tables numbers exclusion mechanism
- Manual tables handing ; keep track of manually changed tables numbers, kept when renumbering games
- Ask confirmation if a table number manual change would trigger a renumbering

### Changed

- Align on opengotha for SOS and SOSOS missed rounds calculations
- Review randomness parameter interface (-> none/deterministic/non-deterministic)
- Review rounding option: correct choice is 'round down' or 'no rounding'
- Smaller font in lists, but only on screen
- Review registration page and display MMS of preliminary players
- Implement a specfic version of popup mouse-over tooltips for handled devices
- Show handicap in results tab
- Show MMS in registration page
- Store backups in an 'history' subdirectory

- [Tests] Display expected and actual pairings when pairing tests fail
- [Tests] Symmetric deterministic randomness

### Fixed

- Fix scores calculation in Swiss tournaments
- Correctly import OpenGotha BYE players
- Export all BYE players in OpenGotha format
- 'BIP' should always be black, BYE player white.
- Fix CUSSW calculation for 'round 0'
- Sanitize character set in ISO exports
- Escape XML entities in OpenGotha exports
- Fix missing result sheets bug
- Fix filtered stripped tables
- Fix handicap calculation
- Bugfix in tournament deletion
- Fix controls display bug in tournament creation on mm/swiss changes

## [0.15] 2024-07-22

### Added

- Add a Korean translation, thanks to Ariane Ougier

### Changes

- Do not count preliminary players in round stats
- MM/Swiss can only be chosen at tournament creation (and display according fields properly)
- Translate `d` and `k`

### Fixes

- Fix handling of BIP game in tables renumbering

## [0.14] 2024-06-19

### Added

- Integrate German translation, thanks to Roland Illig
- Add a Clear Results button on the results tab

### Fixes

- Fix several issues when printing
- Fix a potential NPE in recomputeDUDD

## [0.13] 2024-05-30

### Changes

- Use middle of groups for DUDD by default
- Remove encoding choice at export, choose encoding automatically
- Use MMS to choose ByePlayer if Mac-Mahon tournament
- Use mmBase for the starting Mac-Mahon score in secondary criteria
- Lots of refactoring in tests
- Update secondary criteria to match OpenGotha v3.52
- Backport DUDD calculation from 3.52
- Do not apply secondary criteria when MMS>bar and NBwin>round/2
- Make A-Z browsing a toggle button
- Review results highlighting
- Ladder browsing mode improvement

### Fixes

- Never take current round into account for scoring bonus of unplayed rounds
- Fix scores calculation problem: all pairables must be known, even if not playing previous rounds
- Recompute DUDD at import
- Parameter barThresholdActive was not taken into account

## [0.12] 2024-05-10

### Fixes

- Fix language header parsing
- Disable spellcheck on text input fields
- Take handicap into account in SOS, SOSOS, SODOS
- Fix firstSeed and secondSeed display problem in advanced parameters
- Fix mmsFloor update problem
- Protection against non-parsable Accept-Language header

## [0.11] 2024-05-06

### Changes

- Review up/down arrow behavior and scroll into view in search result list

### Fixes

- Fix registration button state in players form

## [0.10] 2024-04-19

### Added

- Tournament short name autofill
- Teams tournaments handling
- Let registration status tune participation column opacity
- Handle clicks on participation disks
- Search by EGF PIN prefix
- Add a tournament overview dialog
- Add a Windows installer

### Changes

- Review automatic rating/rank calculations
- Print komi on result sheets
- Only colorize logs on unix/linux platforms
- Little more compact and cleaner form inputs

### Fixes

- Better handling of underscores in player index
- Fix countries order in dropdown controls
- EGF format uses handicap correction for file extension
- Bugfix: at H-2, 1h should become 0
- Importing json should tolerate a BOM
- Do not put BOM when exporting json file

## [0.9] 2024-04-10

### Changes

- Review search scroll behavior
- Ask for confirmation before dropping changes or unregistering a player
- Display FFG licence or PIN

### Fixes

- Fix printing under chrome and firefox
- Don't check empty pins in duplicates check
- Fix import/export of egf pin and ffg licence
- Click on final/preliminary was resetting skipped rounds
- Fix OpenGotha import of standard byoyomi
- Review FFG ratings import

## [0.8] 2024-03-30

### Added

- Pairgoth Json export

### Changes

- Fall back to last fetched ratings file on i/o error while updating
- Defaults players country codes to uppercase

### Fixes

- Review EGF ratings import

## [0.7] 2024-03-25

### Fixes

- Fix OpenGotha import

## [0.6] 2024-03-15

### Added

- CSV Export

## [0.5.1] 2024-03-15

### Added

- Add rating date tootip, and avoid registering twice a player
- Add config property for ratings date freeze

### Fixes

- Fix tournament creation regression

## [0.5] 2024-03-14

### Added

- Option to use baseMMS+round/2 for SOS
- Add roundDownScore option to options dialog
- Add tournament director field
- Display more infos in MM groups popup
- Delete button for tournaments
- Let user specify encoding for export

### Changes

- Review printing
- Review maxTime: by convention to 0 if none
- Use player base score for non-played rounds SOS
- Review MMS rounding
- One tournament files directory per user for oauth

### Fixes

- Fix SOSOS calculation
- Fix end date display
- Fix skipped rounds in OpenGotha import
- Fix results page sorting and filtering
- Fix pairgoth import and EGF/FFG export missing flush
- Fix translation of top menu
- Review filtering on registration status
- Fix .tou format publication
- Fix sorting on Reg column
- Fix UK/GB problem
- Fix date display format

## [0.4] 2024-02-29

### Added

- Add HTML format export
- Add a 'final' filter to registration page
- A-Z browsing in registration dialog

### Changes

- More compact display for table cells by default
- Review API authentication
- Review players search behavior (arrow keys and click outside)

## [0.3] 2024-02-21

### Added

- Email/pass logins using sqlite db
- Show license status for French players in EGF ladder
- Allow sorting on participation column
- Implement rounding option
- OAuth authentication
- Visual feedback for registration
- Tables reordering (and use pseudo-ranks for table level)
- Results filtering feature
- Registration dialog: rank gives rating if not updated manually before
- Pairing tab: display stats at top, and persist scroll

### Changes

- Handle additionnal seeding criterium
- B&W printing for participation color disks
- Sort by descending rating and not rank in groups edition popup
- Pairing tab: print pairables instead of games when no game yet

### Fixes

- Fix country import in ratings
- Review responsive layout
- Fix printing
- Fix sticky headers
- Fix tables number OpenGotha export
- Positive corrections need a '+' sign for clarity
- Fix bug in missed rounds computation
- Fix FFG license handling

## [0.2] 2024-01-28

### Added

- Advanced parameters dialog
- Edit pairable round status in pairing window
- Mac Mahon groups edition
- Result sheets printing
- Persistence of search toggle buttons in registration dialog
- Persistence of scroll position on refresh
- Persistence of tables sorting on refresh
- Registration status handling
- Game edition dialog
- Select all in lists for pair/unpair
- Allow results changes in previous rounds
- Handling of half MMS point for missed rounds
- Always choose white for the strongest player with handicap
- Implement 'sesame' authentication

### Changes

- Remove parameters we do not support
- Centralized versionning, and web server ressources cache fooling
- For FFG, display licence state in search window
- Add individual correctionMms field
- Persistent dialog state and recap for registration
- Don't list non final pairables in standings
- Handicap based on MMS
- Review page layout and margins
- Remove games against ByePlayer when computing SOSOS and SODOS
- Remove games against ByePlayer when computing color balance
- Recomputing dudd when adding games
- Allow unpairing of games without result in previous rounds
- Remove special handing for location of online tournaments

### Fixes

- Fix opengotha export header
- Fix MMS computation for current round and while pairing
- Accept utf BOM prefix in imported xml file
- Fix threshold edition
- Fix results count update
- Fix result sheets printing with BYE
- Fix mmsCorrection import/export
- Fix pseudo rank
- Fix BIP unpairing

## [0.1] 2023-12-26

Initial release.


