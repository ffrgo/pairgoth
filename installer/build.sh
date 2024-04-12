#!/bin/bash

VERSION=$(grep '<version>' ../pom.xml | head -1 | egrep  -o '[0-9.]+')
echo Generating installer script for pairgoth-$VERSION

# root files
rm -rf target/*
cp -r resources/files target

# icon
cp resources/pairgoth.ico target/files

# java library
mkdir -p target/files/lib
cp ../application/target/pairgoth-engine.jar target/files/lib

# jre
unzip -d target/files resources/jre.zip

# installer script
sed -r -e "s/@VERSION@/$VERSION.0.0/g" resources/installer.nsi > target/installer.nsi

cat target/installer.nsi | makensis -V4 -
