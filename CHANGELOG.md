# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
<!--
and this project *will* adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with its 1.0.0 release.
-->

## [Unreleased]

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


