#!/bin/bash

export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
mvn -DskipTests --projects api-webapp package jetty:run
