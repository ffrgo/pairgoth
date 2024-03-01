#!/bin/bash

mvn -DskipTests=true install # needed for pairgoth-common

export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
mvn -DskipTests=true --projects api-webapp package jetty:run -Dpairgoth.mode=server
