#!/bin/sh

mvn package && java -jar application/target/pairgoth-engine.jar
