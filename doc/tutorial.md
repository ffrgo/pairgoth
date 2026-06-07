# Pairgoth Tutorial

[TOC]

A hands-on walkthrough: from an empty pairgoth to published standings. For exhaustive descriptions, see the [reference](reference) (model), the [pairing](pairing) (pairing parameters) and the [technical](technical) (configuration, API) documentation.

## Getting Started

Pairgoth runs as a local web server and is used from your browser.

- **Windows**: run the installer, then launch Pairgoth from the start menu; a browser opens automatically.
- **Linux / macOS**: `java -jar pairgoth-engine.jar`, then browse to `http://localhost:8080`.

The home page lists your tournaments. Three buttons get you started:

- **New tournament** — create one from scratch (next section),
- **Import tournament** — load an existing OpenGotha XML file,
- **Clone example tournament** — instant playground to try the workflow risk-free.

Tournament files are plain JSON `.tour` files stored in the `tournamentfiles` directory (configurable); every change is saved immediately, and every previous state is archived — the **Undo** button in the header lets you restore any earlier state, with each action labelled.

## Creating a Tournament

Click **New tournament** and fill the information form:

- **Name** — the display name; **Short name** — used in filenames and exports (letters, digits, `-`, `_`, `.`).
- **Dates and location** (or *online*).
- **Rounds** — can be changed later.
- **Time system** — informative for exports (Fischer, byo-yomi…).
- **Pairing system** — *Mac Mahon* or *Swiss*. For Mac Mahon, set the **MM bar** and **floor** (rank limits of the McMahon groups), the **handicap correction** (0 = full rank-difference handicap, -1 = handicap reduced by one stone, … down to -9 = even games) and the **no-handicap threshold** (players at or above this rank always play even). See the [pairing documentation](pairing) for the full story.

Save: the tournament is created and the other tabs appear. You can return to this tab anytime (the **Edit** button unlocks the form, **Parameters** opens the advanced pairing parameters).

## Registration

### Adding Players

On the **Registration** tab, click the **+** button (or press `+`). Type a few letters of the player's name in the search field: pairgoth searches the rating lists (EGF, FFG…, filterable by country) as you type. Pick the right player — name, rank, rating, country and club are filled in — then confirm with **Register** (Enter). The dialog stays open, ready for the next player: registering a whole club takes a minute.

Two details worth knowing:

- **Rank and rating are chained** (the link icon): editing the rating adjusts the rank accordingly. Unchain them to register an honorary rank different from the playing strength.
- A player is **preliminary** (italic) until marked **final** — only final players are paired. Click the registration status to toggle it. The round circles on each row set per-round participation: green = plays, red = absent.

To edit a player later, click their row; to remove them, open the edition dialog and **Unregister** (only possible while they have no game).

### Importing Players

Instead of registering manually you can:

- **Import an OpenGotha tournament** from the home page — players, pairings and results come along.
- **Refresh ratings** — before round one, re-syncs every registered player's rank/rating with the current rating lists (useful when registration started weeks before the tournament).

## Pairing

### Automatic Pairing

On the **Pairing** tab, choose the round with the « » arrows. The left list shows the *pairable* players (final, participating, not yet paired); the *unpairable* list below shows who is excluded from the draw.

Click **Pair** with no selection to pair everybody, or select a subset (click, shift-click) and pair just it — useful for late arrivals. Games appear on the right with table numbers and handicaps; the **Exclude table numbers** field (e.g. `1-4, 13`) keeps prestigious or missing tables out of the attribution.

When several optimal pairings exist (same total pairing cost), a navigation line appears: **optimal pairing 1 / N** with previous/next arrows — browse them and keep the one you prefer. The *explain pairing* link details why each pair was made; *result sheets* prints per-table slips.

### Manual Adjustments

- **Unpair**: select games (or nothing for all) and click **Unpair**, then re-pair as you wish.
- **Edit a game**: double-click it — change the table, exchange colors, adjust the handicap.
- **Drop / restore a player for this round**: double-click them in the pairable (or unpairable) list and toggle *Pairable for round N*. In a team tournament this drops/restores the whole team's players.
- **Renumber tables** compacts table numbers after edits.

## Entering Results

On the **Results** tab, click the **winner's name** — done. Clicking the result cell cycles through all results (jigo, both lose, cancelled…); double-click resets to unknown. The `(known / total)` counter in the header tracks progress, and the filter checkbox hides finished games so the remaining ones stand out.

Several operators can enter results simultaneously from different computers: changes propagate live to every screen.

## Standings

The **Standings** tab ranks players using the placement criteria (MMS, SOS, SOSOS… — click a criterion header to change it; see the [reference](reference) for definitions). Results of every round are shown per player.

Once the tournament is over, **freeze** the standings if you want later corrections (names, ranks) to leave the official results untouched.

## Exporting Data

The **Publish** button on the standings tab offers:

- **HTML** — a standalone page of the standings,
- **CSV** — spreadsheet-friendly,
- **EGF** — the European Go Database result file (`.h9` for even tournaments, `.h<n>` for handicap ones),
- **FFG** — the French federation `.tou` file,
- **website** — push standings to your tournament website through the [webhook](technical) (pairings and results have the same button on their tabs).

The OpenGotha XML export of the whole tournament is available through the [API](technical), and the `.tour` files themselves are portable JSON — copying them is a complete backup.
