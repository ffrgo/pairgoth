# Pairgoth Pairing Documentation

[TOC]

How pairgoth pairs, and every setting that influences it. The everyday workflow is covered by the [tutorial](tutorial); this document is for organizers who want to understand or tune the draw, and for developers reading or writing the `pairing` section of a tournament through the [API](technical#pairgoth-api-specification).

## How pairing works

For each round, every candidate pair of players is given a **cost** built from the criteria below: score difference, rematch avoidance, colour balance, draw-up/draw-down, seeding, geographical splitting… Pairgoth then computes the pairing of **minimal total cost** over all pairable players, a weighted perfect matching of the same family as OpenGotha's, with results compatible by design.

Consequences worth knowing:

- The pairing is **globally** optimal: a seemingly odd individual pair can be the price of a better whole.
- Several distinct pairings can share the minimal cost; the pairing tab then lets you browse them (*optimal pairing 1 / N*).
- No criterion is absolute. Even a rematch is only a very high cost, so that pairing stays possible in small tournaments.

In a **Swiss** tournament, players start at score 0 and the main cost is the score difference. In a **McMahon** tournament, players start at their McMahon score (rank-based, clamped between the *floor* and the *bar*), so strong players meet immediately while beginners are protected; handicap can be enabled below a rank threshold. The **French system** sits in between: a Swiss whose rating-based bonus groups shrink round after round, down to a plain Swiss for the last two rounds (see [French system](#french-system)).

## Pairing types

| Type | Description |
|------|-------------|
| `SWISS` | Swiss system |
| `MAC_MAHON` | McMahon system |
| `FRENCH` | French system |
| `ROUND_ROBIN` | Round robin (not implemented) |

## EGF tournament system rules

Pairgoth implements the [EGF tournament system rules](https://www.eurogofed.org/egf/toursysrules.htm). What they prescribe and pairgoth applies, without asking anything of the organizer:

- **Game values**: a win 1, a jigo ½, a loss 0; a bye is a default win, and a team match is won, lost or drawn on the sum of its board results.
- **Accumulated scores are rounded down** (*round down NBW/MMS score*, on by default), tie-breaks included: they read the same rounded scores.
- **Non-played rounds** are worth `mmsValueAbsent` in the McMahon score and `nbwValueAbsent` in the number of wins (the EGF gives ½ *if the tournament rules say so*, hence the settings). For SOS they bring the player's own starting McMahon score in a McMahon, and 0 in a Swiss.
- **Equal players share a place number**, one greater than the number of better placed players.
- **Pairing the same players twice** is the most expensive thing the pairing can do; when it cannot be avoided, the two players get **inverse colours**. Otherwise colour balance drives the choice.
- **Above the top bar**, pairing is not biased by geography (*secondary criteria threshold*).

What the rules leave to the organizer, with their recommendation:

- **Tie-breaks.** The EGF recommends, in this order: number of board wins (team tournaments), direct comparison (EGFDC, "generally it should be the first or even the only tiebreaker" for the final results), then one of SOS-2, SOS-1 or SOS, then rating, previous order, lottery. Only one SOS flavour may be used. SOSOS, SODOS and CUSS are explicitly *not* recommended; they stay available as long-standing national habits. Criteria may differ between the final results and the draw (*Player ordering for pairing*), which the EGF encourages: SOS is sound for making pairings and doubtful for the standings.
- **Handicap.** The EGF default is *no handicap*; when handicap is used, its default is the rank difference minus two, for 15 kyu and below. Pairgoth ships OpenGotha's habits instead: no handicap in a Swiss, and in a McMahon the rank difference minus one below 1d (*Hd correction* and *No hd threshold*, on the information tab).
- **Rating** as a tie-break is the rating "just before the tournament's start": freeze it with the `ratings.date` setting, otherwise a ratings refresh moves it mid-tournament.

Not implemented: knockout, league and match systems (only Swiss, McMahon and the French system are), and McMahon supergroups, which the rules do not use by default either.

## McMahon settings

Set on the information tab; the JSON keys live directly under `pairing`.

| Setting | JSON key | Description | Default |
|---------|----------|-------------|---------|
| MM floor | `mmFloor` | Lowest rank with its own McMahon group; weaker players start at the floor's score | -20 (20k) |
| MM bar | `mmBar` | Highest rank with its own McMahon group; stronger players start at the bar's score | 0 (1d) |
| Hd correction | `handicap.correction` | Stones subtracted from the rank difference; the form shows it negative (-1 = one stone less), the JSON stores it positive | 1 |
| No hd threshold | `handicap.threshold` | Rank from which games are even: no handicap when at least one player is at or above it | 0 (1d) |

Ranks are integers: 0 is 1d, positive values are dan, negative values are kyu (-1 = 1k, -30 = 30k).

## French system

A Swiss with a sliding McMahon-like bonus, proposed by a French organizer (*système fanfan*, *système français*) as a blend of accelerated Swiss and the Amalfi system (a Swiss where the top players sit out the first rounds). For a tournament of *r* rounds, at round *k*:

- the players taking part in the round are sorted by starting level (rating) and split into *G* = *r* − *k* groups of equal size (⌊*n*/*G*⌋, the remainder going to the bottom group);
- the top group gets a bonus of *G* points, the next one *G* − 1, …, the bottom group 1;
- the pairing score is wins + bonus (shown as MMS), and each score group is drawn by split-and-slip, ordered by starting level.

At round *r* − 1 everyone is in a single group (+1 for all, no effect), and round *r* has no bonus: the last two rounds are a plain Swiss. Compared with a McMahon, it is as if starting levels ran from (*r*−1)d down to 1d at round 1, (*r*−2)d..1d at round 2, and everyone were 1d for the last two rounds. A weak player can still win the tournament by winning every game, but has to beat the top players sooner or later, while games stay closer in level than in Amalfi throughout.

Example, 42 players, 5 rounds: round 1, four groups of 10/10/10/12 with bonus 4/3/2/1; round 2, three groups of 14 with 3/2/1; round 3, two groups of 21 with 2/1; rounds 4 and 5, plain Swiss.

Standings are plain wins (NBW, SOSW, SOSOSW by default); pairing order defaults to the pairing score, then rating. Other defaults and advanced parameters are those of the Swiss: no handicap, no geographical criteria; the McMahon-only settings (floor, bar, handicap correction, MMS for non-played rounds) don't apply, and non-played rounds count as `nbwValueAbsent`. Changing the number of rounds during the tournament changes the bonus schedule of the remaining rounds, by construction.

## Advanced parameters

The **Parameters** button of the information tab opens the advanced parameters, grouped as below. Each table gives the dialog's wording, the JSON key under `pairing.<section>`, what the setting does, and the default. Settings marked *API only* have no field in the dialog; they are OpenGotha's internal weights, kept for compatibility and for the curious. Defaults that differ by pairing type are given as Swiss / McMahon.

Weights are costs: the higher a weight, the more the pairing avoids what it penalises. Their orders of magnitude are OpenGotha's, so that both programs produce the same draw from the same settings.

### Base parameters (`pairing.base`)

| Setting | JSON key | Description | Default |
|---------|----------|-------------|---------|
| Randomness | `random`, `deterministic` | *none*: no random term. *deterministic*: a random term seeded by the players, so the same tournament always gives the same draw. *non-deterministic*: a fresh random term at each pairing | none (`random` 0, `deterministic` true) |
| Balance white and black | `colorBalanceWeight` | Weight of giving each player as many whites as blacks; a rematch overrides it, the two players get inverse colours | 1000 |
| *API only* | `nx1` | Concavity of the cost curves (0 to 1); 0.5 is OpenGotha's standard value | 0.5 |
| *API only* | `dupWeight` | Weight of avoiding a rematch; the largest weight of all | 5e14 |

### Main parameters (`pairing.main`)

| Setting | JSON key | Description | Default |
|---------|----------|-------------|---------|
| Round down NBW/MMS score | `roundDownScore` | Round accumulated scores down to the integer, as the EGF rules prescribe | true |
| Number of wins for non-played rounds | `nbwValueAbsent` | 0, ½ or 1 win for a round a player did not play | 0 |
| MMS score for non-played rounds | `mmsValueAbsent` | 0, ½ or 1 McMahon point for a round a player did not play (McMahon only) | ½ |
| SOS for non-played rounds | `sosValueAbsentUseBase` | What a non-played round brings to the player's SOS: the player's own base MMS, or base MMS + half the rounds (McMahon only) | base MMS (true) |
| Apply first seeding method up to round | `firstSeedLastRound` | Last round using the first seeding method; later rounds use the second | 2 |
| First seeding method | `firstSeed` | How players of a score group are matched in the early rounds (see [Seeding methods](#seeding-methods)) | split and slip / split and random |
| First seeding method, add a sorting on rating | `firstSeedAddCrit` | Order the group by rating before splitting it (`RATING`), or keep the standings order (`NONE`) | `RATING` |
| Second seeding method | `secondSeed` | How players of a score group are matched in the later rounds | split and slip |
| Second seeding method, add a sorting on rating | `secondSeedAddCrit` | Same as above for the second method | `RATING` / `NONE` |
| Draw-up / draw-down: try to compensate | `upDownCompensate` | When a player must meet someone from another score group, prefer one who was drawn the other way in a previous round | true |
| Pair a player in the … of the upper group | `upDownUpperMode` | Which part of the stronger group gives the player drawn down: `TOP`, `MIDDLE` or `BOTTOM` | `MIDDLE` |
| … with a player in the … of the lower group | `upDownLowerMode` | Which part of the weaker group gives the player drawn up | `MIDDLE` |
| *API only* | `scoreWeight` | Weight of pairing players of equal score; the main criterion | 1e11 |
| *API only* | `upDownWeight` | Weight of the draw-up/draw-down preferences above | 1e8 |
| *API only* | `maximizeSeeding` | Weight of the seeding method | 5e6 |
| *API only* | `catWeight` | Weight of keeping categories apart (Swiss with categories, not implemented) | 2e13 |

### Seeding methods

How the players of one score group are matched among themselves. The group is first ordered (by the standings criteria, then by rating if the *add a sorting on rating* box is ticked), then cut in two halves:

- `SPLIT_AND_FOLD`: first of the top half against last of the bottom half, second against second to last, and so on.
- `SPLIT_AND_SLIP`: first of the top half against first of the bottom half, second against second, and so on.
- `SPLIT_AND_RANDOM`: top half against bottom half at random.

### Secondary parameters (`pairing.secondary`)

The secondary criteria are the geographical and handicap preferences below. These settings switch them off where they would hurt the sporting result.

| Setting | JSON key | Description | Default |
|---------|----------|-------------|---------|
| Do not apply for players with a MMS equal to or stronger than | `rankThreshold` | Rank (as a McMahon score) from which secondary criteria are ignored | -30 (30k) / 0 (1d) |
| Do not apply for players who won at least half of their games | `winsThreshold` | Switch secondary criteria off for players in the running | true / false |
| Do not apply for players above the McMahon bar | `barThreshold` | Switch secondary criteria off above the bar | true |
| *API only* | `secWeight` | Weight of the secondary criteria as a whole | 2e13 / 1e11 |

### Geographical parameters (`pairing.geo`)

| Setting | JSON key | Description | Default |
|---------|----------|-------------|---------|
| Prefer a score gap of … rather than pairing players of the same country | `mmsDiffCountry` | Score difference accepted to avoid a same-country game | 1 |
| Prefer a score gap of … rather than pairing players of the same club | `mmsDiffClub` | Score difference accepted to avoid a same-club game | 3 |
| Avoid pairing players from the same club with the same family name | `avoidSameFamily` | Treat namesakes of one club as relatives to keep apart | false |
| Apply main-club adjustment | `mainClubAdjustment` | When one club gathers most of the field (a club tournament), relax the same-club avoidance between its members and drop the country criterion | false |
| Main club detected when a single club gathers more than … % of the field | `mainClubDetectionThreshold` | Share of the field that makes a club the main club (stored as a fraction, 0.4 for 40 %) | 0.4 |
| *API only* | `weight` | Weight of the geographical criteria; 0 disables them | 0 / 1e11 |
| *API only* | `mmsDiffClubGroup` | Score difference accepted to avoid a game between clubs of the same group (groups are not implemented) | 2 |

### Handicap parameters (`pairing.handicap`)

The correction and threshold are on the information tab (see [McMahon settings](#mcmahon-settings)).

| Setting | JSON key | Description | Default |
|---------|----------|-------------|---------|
| Use MMS rather than rank for handicap | `useMMS` | Compute the handicap from the McMahon score difference instead of the rank difference | true |
| Handicap ceiling | `ceiling` | Maximum number of handicap stones (0 to 9) | 9 |
| *API only* | `weight` | Weight of minimising handicap (Swiss with categories, not implemented) | 0 |

### Player ordering for pairing (`pairing.pairingPlacement`)

Up to four criteria ordering the players when a round is paired, when they must differ from the standings criteria (see the [reference](reference#placement-criteria)). Left all `NONE`, the standings criteria are used. Pairgoth refuses a list that uses one criterion twice, two SOS flavours or two direct-confrontation criteria, as it does for the standings.
