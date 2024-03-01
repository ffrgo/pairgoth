#!/bin/bash

VERSION=$(grep '<version>' pom.xml | head -1 | egrep  -o '[0-9.]+')

if [ !-f "$HOME/.m2/repository/org/jeudego/pairgoth/pairgoth-common/$VERSION/pairgoth-common-$VERSION.jar" ]
then
    mvn -DskipTests=true install # needed for pairgoth-common    
fi

export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
mvn -DskipTests=true --projects api-webapp package jetty:run -Dpairgoth.mode=server
