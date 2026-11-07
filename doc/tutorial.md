# Pairgoth Tutorial

[TOC]

This walkthrough takes you from installing pairgoth to publishing the final standings of your first tournament. For complete descriptions, see the [reference](reference) (data model, event websites), the [pairing documentation](pairing) (pairing systems and parameters) and the [technical documentation](technical) (configuration, API).

## Getting started

Pairgoth runs as a local web server and is used from your browser.

- **Windows**: run the installer, then launch pairgoth from the Start menu; a browser opens automatically.
- **Linux / macOS**: install Java 11 or later, download `pairgoth-engine.jar`, run `java -jar pairgoth-engine.jar`, then open `http://localhost:8080` in your browser.

The home page lists your tournaments. Three buttons get you started:

- **New tournament**: create one from scratch (next section).
- **Import tournament**: load an existing OpenGotha XML file.
- **Clone example tournament**: an instant playground to try the workflow without risk.

Each tournament is a plain JSON `.tour` file in the `tournamentfiles` directory (see the [technical documentation](technical#store) to change it). Every change is saved immediately and every previous state is archived: the **Undo** button in the header (the ↶ icon) lists your past actions and restores any earlier state.

## Creating a tournament

Click **New tournament** and fill in the information form:

- **Name**: the full display name.
- **Short name**: used in file names, exports and website addresses (letters, digits, `-`, `_`, `.`).
- **Dates** and **Location** (or tick **online tournament**).
- **Tournament type**: individual players, or one of the pair go, rengo and team formats.
- **Rounds**: can be changed later.
- **Time system** (Fischer, byo-yomi…): for information only, copied into exports.
- **Pairing**: *McMahon*, *Swiss* or *French* (see the [pairing documentation](pairing)). For McMahon, also set:
    - **MM bar** and **MM floor**: the strongest and weakest ranks that get distinct McMahon starting scores; players above the bar or below the floor are grouped with it.
    - **Hd correction**: 0 = handicap equal to the rank difference, -1 = one stone less, …, -9 = always even games.
    - **No hd threshold**: players at or above this rank always play even games.

Click **Create**: the tournament is created and the other tabs appear. You can return to the **Information** tab at any time: **Edit** unlocks the form (then **Update** saves it), and **Advanced parameters** opens the advanced pairing parameters.

## Registration

### Adding players

On the **Registration** tab, click **Add player** (or press `+`). Type a few letters of the player's name in the search field: pairgoth searches the rating lists (EGF, FFG…, optionally filtered by country) as you type. Pick the right player (name, rank, rating, country and club are filled in), then confirm with **Register** (or press Enter). The dialog stays open, ready for the next player: registering a whole club takes a minute.

Three details worth knowing:

- **Rank and rating are chained** (the link icon): changing one updates the other. Click the icon to unchain them when a player's displayed rank must differ from their rating (an honorary rank).
- A player is **preliminary** (shown in italic) until marked **final**, and only final players are paired. Click the status cell (? / ✓) on the player's row to toggle it.
- The round circles on each row set participation per round: green = plays, red = absent. Click a circle to toggle it.

To edit a player later, click their row; to remove them, open the edit dialog and click **Unregister** (only possible while they have not been paired).

### Other ways to fill the roster

Instead of registering players one by one you can:

- **Import an OpenGotha tournament** from the home page: players, pairings and results come along.
- **Sync from website**, if your tournament is connected to an event website (see [below](#connecting-to-an-event-website)).

Once players are registered, **Refresh ratings** updates every registered player's rank and rating from the current rating lists, which is useful when registration opened weeks before the tournament. Do it before round 1.

## Teams

For a team tournament (a team, pair go or rengo **Tournament type** on the **Information** tab), a **Teams** tab appears after **Registration**. Register players as usual, then group them into teams there: select players, type a team name and click **Team up**; **Join** and **Split** add players to or remove them from an existing team. Pairgoth then pairs teams instead of players; in the team formats, each team game is split into one game per board.

## Pairing

### Automatic pairing

On the **Pairing** tab, choose the round with the **«** / **»** buttons. The left list shows the *pairable* players (final, participating, not yet paired); the *unpairable* list below it shows who is excluded from this round's draw.

Click **Pair** with nothing selected to pair everybody, or select some players (click, shift-click) and pair only them, which is useful for late arrivals. Games appear on the right with table numbers and handicaps. The **Exclude table numbers** field (e.g. `1-4, 13`) keeps reserved or unavailable tables out of the table assignment.

When several optimal pairings exist (same total pairing cost), a navigation line appears: **optimal pairing 1 / N** with previous/next arrows. Browse them and keep the one you prefer. The **result sheets** button prints one slip per table.

### Manual adjustments

- **Unpair**: select games and click **Unpair**. With nothing selected, the whole round is unpaired (pairgoth asks for confirmation). Then pair again.
- **Edit a game**: double-click it to change the table, swap colours or adjust the handicap.
- **Drop / restore a player for this round**: double-click them in the pairable (or unpairable) list and toggle **Pairable for round N?**. In a team tournament this drops or restores the whole team.
- **Renumber** compacts table numbers after edits.

## Entering results

On the **Results** tab, click the winner's name. Clicking the result cell cycles through all results (jigo, both lose, cancelled…); double-click resets it to unknown. The `(known / total)` counter in the header tracks progress, and the **Filter** checkbox hides finished games so the remaining ones stand out.

Several people can enter results at the same time from different computers; changes appear on every screen.

## Standings

The **Standings** tab ranks players by up to four placement criteria (e.g. MMS, then SOS, then SOSOS), applied in order: the first criterion that differs decides. Choose them in the **Crit #1**–**Crit #4** dropdowns and click **Change**; see the [reference](reference#placement-criteria) for each criterion's definition. The table also shows each player's result in every round.

## Publishing and exporting

The **Publish** button on the **Standings** tab offers:

- **HTML**: a standalone page of the standings.
- **CSV**: spreadsheet-friendly.
- **EGF**: the European Go Database (EGD) result file (`.h9` for even tournaments, `.h<n>` for handicap ones).
- **FFG**: the French Go Federation `.tou` file.
- **Website**: push the standings to your event website through the [webhook](technical#pairgoth-webhook-specification). The **Pairing** and **Results** tabs have their own **Publish to website** button.

The OpenGotha XML export of the whole tournament is available through the [API](technical#pairgoth-api-specification), and the `.tour` files themselves are portable JSON: copying them is a complete backup.

## Connecting to an event website

If your tournament has a website (a club page, a federation site or a larger platform), pairgoth can **publish** pairings, results and standings to it (the **Publish to website** buttons) and **pull** the player roster from it (**Sync from website** on the **Registration** tab). For a single club tournament, that is all you need. Larger multi-event setups, such as a national championship or the EGC, can add single sign-on and per-operator access. The [reference](reference#connecting-to-an-event-website) summarizes the possible setups and the [technical documentation](technical#deployment-profiles) explains how to configure them.
