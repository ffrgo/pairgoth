# PairGoth model

## Entity Relationship Diagram

```mermaid
erDiagram

  %% entities

Tournament {
    name
    shortName
    startDate
    endDate
    location
    isOnline
  }

  Round {
    number
  }

  Game {
    black
    white
    result
  }

  Player {
    lastname
    firstname
    country
    club
  }

  Ladder {
    name
    lastUpdated
  }
  
  %% relationships

  Tournament ||--|{ Round
  Round ||--|{ Game
  Game |}--|| Player : black
  Game |}--|| Player : white
  Player }o--o{ Ladder : rating
  
```

