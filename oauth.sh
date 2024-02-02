#!/bin/bash

trap 'kill $CSSWATCH; exit' INT
( cd view-webapp; ./csswatch.sh ) &
CSSWATCH=$!

export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5006"
#mvn --projects view-webapp -Dpairgoth.api.url=http://localhost:8085/api/ package jetty:run
mvn -DskipTests=true \
	-Dpairgoth.auth=oauth \
	-Dpairgoth.oauth.providers=ffg \
	-Dpairgoth.oauth.ffg.secret=43f3a67bffcb5054d2f1b0e2a2374bdc \
	-Dwebapp.external.url=http://localhost:8080
	--projects view-webapp package jetty:run
kill $CSSWATCH
