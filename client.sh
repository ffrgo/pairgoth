#!/bin/sh

# debug version
mvn -DskipTests package && java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 -Dpairgoth.mode=client -jar application/target/pairgoth-engine.jar

# mvn -DskipTests package && java -Dpairgoth.mode=client -jar application/target/pairgoth-engine.jar
