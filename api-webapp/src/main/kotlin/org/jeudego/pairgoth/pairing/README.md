# File hierarchy
- `HistoryHelper.kt` computes all the criterion for pairings and standings like number of wins, colors, sos, etc
- `BasePairingHelper.kt` extends the `Pairable` objects with attributes corresponding to these criteria. This class plays the role of the `ScoredPlayer` in OpenGotha.
- `solver` folder contains the actual solver base class and the concrete solvers for different tournaments type (Swiss, MacMahon, etc).

# Weights internal name
## Base criteria
- avoiddup
- random
- color
## Main criteria
- score
- seed
## Secondary criteria