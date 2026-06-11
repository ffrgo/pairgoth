#!/bin/bash

mkdir -p data/app

# the properties bind mount needs an existing file (defaults are all commented out)
[ -f pairgoth.properties ] || cp ../pairgoth.properties.example pairgoth.properties

# pick up a freshly built engine
jar=../application/target/pairgoth-engine.jar
if [ -f "$jar" ] && [ "$jar" -nt data/app/pairgoth-engine.jar ]; then
  cp "$jar" data/app/
fi

APP_UID=$(id -u) APP_GID=$(id -g) docker compose up
