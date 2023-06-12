#!/bin/bash

export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
mvn --projects api-webapp package jetty:run-war
