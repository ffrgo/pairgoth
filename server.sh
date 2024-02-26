#!/bin/sh

# debug version
mvn -DskipTests package && java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Dpairgoth.mode=server -jar application/target/pairgoth-engine.jar

# mvn -DskipTests=true package && java -Dpairgoth.mode=server -jar application/target/pairgoth-engine.jar
