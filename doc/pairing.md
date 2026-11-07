# Pairgoth Pairing Documentation

[TOC]

How pairgoth pairs, and every knob that influences it. The casual workflow is covered by the [tutorial](tutorial); this document is for organizers who want to understand or tune the draw.

## How pairing works

For each round, every candidate pair of players is given a **cost** built from the criteria below: score difference, already-played avoidance, color balance, draw-up/draw-down, seeding, geographical splitting… Pairgoth then computes a pairing of **minimal total cost** over all pairable players (a weighted perfect matching — the same family of algorithms as OpenGotha, with results compatible by design).

Consequences worth knowing:

- The pairing is **globally** optimal: a seemingly odd individual pair can be the price of a better whole.
- Several distinct pairings can share the minimal cost; the pairing tab then lets you browse them (*optimal pairing 1 / N*).
- Every weight below only *biases* costs: no criterion is absolute except the impossibility of pairing a player twice with the same opponent (and even that is a very high cost, not a hard rule, so that pairing remains possible in small tournaments).
- The *explain pairing* link on the pairing tab shows the actual cost components of each game of a round.

In a **Swiss** tournament, players start at score 0 and the main cost is the score difference. In a **Mac Mahon** tournament, players start at their McMahon score (rank-based, clamped between the *floor* and the *bar*), so strong players meet immediately while beginners are protected; handicap can be enabled below a rank threshold.

## Pairing Types

| Type | Description |
|------|-------------|
| SWISS | Swiss system |
| MAC_MAHON | MacMahon system |
| ROUND_ROBIN | Round robin (not implemented) |

## MacMahon-specific

| Field | Type | Description |
|-------|------|-------------|
| mmFloor | int | MacMahon floor (default: -20 = 20k) |
| mmBar | int | MacMahon bar (default: 0 = 1D) |

## Base Parameters

| Parameter | Description |
|-----------|-------------|
| nx1 | Concavity curve factor (0.0-1.0) |
| dupWeight | Duplicate game avoidance weight |
| random | Randomization factor |
| deterministic | Deterministic pairing |
| colorBalanceWeight | Color balance importance (a rematch overrides it: two players meeting again get inverse colours) |
| byeWeight | Bye assignment weight |

## Main Parameters

| Parameter | Description |
|-----------|-------------|
| categoriesWeight | Avoid mixing categories |
| scoreWeight | Minimize score differences |
| drawUpDownWeight | Draw-up/draw-down weighting |
| compensateDrawUpDown | Enable DUDD compensation |
| drawUpDownUpperMode | TOP, MIDDLE, or BOTTOM |
| drawUpDownLowerMode | TOP, MIDDLE, or BOTTOM |
| seedingWeight | Seeding importance |
| lastRoundForSeedSystem1 | Round cutoff for system 1 |
| seedSystem1 | First seeding method |
| seedSystem2 | Second seeding method |
| mmsValueAbsent | MMS for absent players |
| nbwValueAbsent | Number of wins for absent players (the EGF gives ½, if the tournament rules say so) |
| roundDownScore | Floor vs round scores |

## Seed Methods

- `SPLIT_AND_FOLD`
- `SPLIT_AND_RANDOM`
- `SPLIT_AND_SLIP`

## Secondary Parameters

| Parameter | Description |
|-----------|-------------|
| barThresholdActive | Don't apply below bar |
| rankSecThreshold | Rank limit for criteria |
| nbWinsThresholdActive | Score threshold |
| defSecCrit | Secondary criteria weight |

## Geographical Parameters

| Parameter | Description |
|-----------|-------------|
| avoidSameGeo | Avoid same region |
| preferMMSDiffRatherThanSameCountry | Country preference |
| preferMMSDiffRatherThanSameClubsGroup | Club group preference |
| preferMMSDiffRatherThanSameClub | Club preference |

## Handicap Parameters

| Parameter | Description |
|-----------|-------------|
| weight | Handicap minimization weight |
| useMMS | Use MMS vs rank |
| rankThreshold | Rank threshold |
| correction | Handicap reduction |
| ceiling | Max handicap stones |


## EGF tournament system rules

Pairgoth implements the [EGF tournament system rules](https://www.eurogofed.org/egf/toursysrules.htm).
What they prescribe and pairgoth applies, without asking anything of the organizer:

- **Game values** — a win 1, a jigo ½, a loss 0; a bye is a default win, and a team match is won,
  lost or drawn on the sum of its board results.
- **Accumulated scores are rounded down** (*round down NBW/MMS score*, on by default), tie-breaks
  included: they read the same rounded scores.
- **Non-played rounds** — worth `mmsValueAbsent` in the McMahon score and `nbwValueAbsent` in the
  number of wins (the EGF gives ½ *if the tournament rules say so*, hence the settings). For SOS
  they bring the player's own starting McMahon score in a McMahon, and 0 in a swiss.
- **Equal players share a place number**, one greater than the number of better placed players.
- **Pairing the same players twice** is the most expensive thing the pairing can do; when it cannot
  be avoided, the two players get **inverse colours**. Otherwise colour balance drives the choice.
- **Above the top bar**, pairing is not biased by geography (*secondary criteria threshold*).

What the rules leave to the organizer, with their recommendation:

- **Tie-breaks.** The EGF recommends, in this order: number of board wins (team tournaments),
  direct comparison (EGFDC — "generally it should be the first or even the only tiebreaker" for
  the final results), then one of SOS-2, SOS-1 or SOS, then rating, previous order, lottery. Only
  one SOS flavour may be used, and SOSOS, SODOS and CUSS are explicitly *not* recommended — they
  are still available, being long-standing national habits. Criteria may differ between the final
  results and the draw (*Player ordering for pairing*), which the EGF encourages: SOS is sound for
  making pairings and doubtful for the standings.
- **Handicap.** The EGF default is *no handicap*; when handicap is used, its default is the rank
  difference minus two, for 15 kyu and below. Pairgoth ships OpenGotha's habits instead — no
  handicap in a swiss, and in a McMahon a rank difference minus one below 1 dan (*Hd correction*
  and *No hd threshold*, on the information tab).
- **Rating** as a tie-break is the rating "just before the tournament's start": freeze it with the
  `ratings.date` setting, otherwise a ratings refresh moves it mid-tournament.

Not implemented: knockout, league and match systems (only swiss and McMahon are), and McMahon
supergroups — which the rules do not use by default either.
