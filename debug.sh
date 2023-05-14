#!/bin/sh

mvn package
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5006 -jar application/target/pairgoth-engine.war
