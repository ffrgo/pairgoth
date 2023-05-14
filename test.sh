#!/bin/bash

curl -s --header "Accept: application/json" --header "Content-Type: application/json" \
  --request POST \
  --data '{ "type":"INDIVIDUAL","name":"Mon Tournoi", "shortName": "mon-tournoi", "startDate": "2023-05-10", "endDate": "2023-05-12", "country": "FR", "location": "Marseille", "online": false, "timeSystem": { "type": "fisher", "mainTime": "1200", "increment": "10" }, "pairing": { "type": "ROUNDROBIN" } }' \
  http://localhost:8080/api/tournament

curl -s --header "Accept: application/json" http://localhost:8080/api/tournament

curl -s --header "Accept: application/json" http://localhost:8080/api/tournament/1

curl -s --header "Accept: application/json" --header "Content-Type: application/json" \
  --request POST \
  --data '{ "name": "Burma", "firstname": "Nestor", "rating": 1600, "rank": -2, "country": "FR", "club": "13Ma" }' \
  http://localhost:8080/api/player

curl -s --header "Accept: application/json" http://localhost:8080/api/player

curl -s --header "Accept: application/json" --header "Content-Type: application/json" \
  --request POST \
  --data '{ "id": 1 }' \
  http://localhost:8080/api/tournament/1/registration

curl -s --header "Accept: application/json" http://localhost:8080/api/tournament/1/registration



