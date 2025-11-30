# Pairgoth Project

## Purpose

**Pairgoth** is a modern Go tournament pairing engine - successor to OpenGotha. It manages tournaments using Swiss and MacMahon pairing systems, handling player registration, automatic pairing, results entry, and standings calculation.

**Version:** 0.20 | **License:** org.jeudego (French Go Association)

## Tech Stack

- **Backend:** Kotlin 2.1 + Maven + Jetty 10
- **Frontend:** Fomantic UI CSS 2.9.2 + Vanilla JavaScript (no jQuery/React)
- **Templates:** Apache Velocity 2.4
- **Storage:** File-based XML (no database required)
- **JDK:** 11+

## Project Structure

```
pairgoth/
├── pairgoth-common/     # Shared utilities (JSON, XML, crypto, logging)
├── api-webapp/          # REST API backend (port 8085)
│   └── model/           #   Domain: Tournament, Player, Game, Pairing
│   └── pairing/         #   Solvers: Swiss, MacMahon algorithms
│   └── store/           #   Persistence: File/Memory storage
│   └── api/             #   Handlers: Tournament, Player, Results, etc.
│   └── ext/             #   OpenGotha import/export
├── view-webapp/         # Web UI frontend (port 8080)
│   └── webapp/js/       #   Vanilla JS: domhelper, api, main, tour-*.inc
│   └── webapp/sass/     #   Styles: main, tour, explain, index
│   └── templates/       #   Velocity: index, tour, explain, login
│   └── kotlin/          #   Servlets, OAuth, Ratings integration
├── webserver/           # Standalone Jetty launcher
├── application/         # Final JAR packaging
└── docker/              # Container deployment
```

## Architecture

### Dual-Webapp Pattern

```
[Browser] <--8080--> [view-webapp] <--8085--> [api-webapp]
              │              │                     │
        Velocity HTML    ApiClient.kt         REST JSON
        + vanilla JS                          + FileStore
```

- **api-webapp** - Pure REST API, business logic, pairing engine
- **view-webapp** - Web UI, proxies API calls, handles auth/i18n/ratings

### Key Architectural Decisions

1. **No JS Framework** - 2200 lines of vanilla JS vs typical 50KB+ bundle
2. **Fomantic CSS Only** - Using CSS framework without its jQuery-dependent JS
3. **CSS @layer** - Clean cascade: `semantic` layer < `pairgoth` layer
4. **File Storage** - XML files for portability, no database setup needed
5. **Read/Write Locks** - Simple concurrency on API servlet
6. **SSE Events** - Real-time updates via Server-Sent Events

## Domain Model

```
Tournament (sealed class)
├── IndividualTournament
├── PairTournament
├── TeamTournament
└── RengoTournament

Player → Pairable (interface)
Game { white, black, result, handicap }
Pairing { Swiss | MacMahon }
TimeSystem { ByoYomi | SuddenDeath | Canadian | Fischer }
Rules { French | Japanese | AGA | Chinese }
```

## Pairing Engine

Location: `api-webapp/src/main/kotlin/org/jeudego/pairgoth/pairing/`

- **SwissSolver** - Swiss system pairing algorithm
- **MacMahonSolver** - MacMahon bands system
- **HistoryHelper** - Criteria: wins, SOS, SOSOS, colors, CUSS, etc.
- **PairingListener** - Progress callbacks for UI

## Key Files

| Purpose | Path |
|---------|------|
| DOM utilities | `view-webapp/.../js/domhelper.js` |
| API client | `view-webapp/.../js/api.js` |
| Core UI | `view-webapp/.../js/main.js` |
| Main styles | `view-webapp/.../sass/main.scss` |
| Tournament model | `api-webapp/.../model/Tournament.kt` |
| Swiss solver | `api-webapp/.../pairing/solver/SwissSolver.kt` |
| API router | `api-webapp/.../server/ApiServlet.kt` |
| App launcher | `webserver/.../application/Pairgoth.kt` |

## Build & Run

```bash
# Build
mvn clean package

# Run standalone (both webapps)
java -jar application/target/pairgoth-engine.jar

# Or separate:
# API: localhost:8085/api
# UI:  localhost:8080/
```

## Configuration

File: `pairgoth.properties` (user) or `pairgoth.default.properties` (defaults)

```properties
webapp.port = 8080
api.port = 8085
store = file                    # file | memory
store.file.path = tournamentfiles
auth = none                     # none | oauth | sesame
```

## Frontend Patterns

### State via CSS Classes
- `.active` - tabs, accordions, visible elements
- `.shown` - modals/popups
- `.hidden` / `.disabled` / `.selected` / `.dimmed`

### Component Communication
```javascript
// Custom events
box.dispatchEvent(new CustomEvent('listitem-dblclk', { detail: id }));

// jQuery-like API (domhelper.js)
$('.item').addClass('active').on('click', handler);
```

### API Integration
```javascript
api.getJson('/tour/123/players')
   .then(players => render(players))
   .catch(err => error(err));
```

## External Integrations

- **Ratings:** FFG (French), EGF (European), AGA (Australian)
- **OAuth:** FFG, Google, Facebook, Twitter, Instagram
- **Import/Export:** OpenGotha XML format compatibility

## i18n

Translations in `view-webapp/.../WEB-INF/translations/`
- English (default)
- French (fr)
- German (de)
- Korean (ko)

## Current Work

### User Preferences (feature/user-preferences branch)
Implemented "black vs white" display order option:
- Gear icon in header opens settings modal
- Preference stored in cookie (`blackFirst`) for server-side Velocity rendering
- localStorage backup via store2 (`prefs.blackFirst`)
- Velocity conditionals in tour-pairing.inc.html, tour-results.inc.html, result-sheets.html
- ViewServlet reads cookie and sets `$blackFirst` in Velocity context

Files modified:
- `view-webapp/.../layouts/standard.html` - gear icon + settings modal
- `view-webapp/.../sass/main.scss` - settings modal styles
- `view-webapp/.../js/main.js` - prefs object + modal handlers + cookie set
- `view-webapp/.../kotlin/.../ViewServlet.kt` - read blackFirst cookie
- `view-webapp/.../tour-pairing.inc.html` - `#if($blackFirst)` conditionals
- `view-webapp/.../tour-results.inc.html` - `#if($blackFirst)` conditionals + inverted result display
- `view-webapp/.../result-sheets.html` - `#if($blackFirst)` conditionals
