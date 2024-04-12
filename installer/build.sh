#!/bin/bash

echo Generating installer script for pairgoth-$VERSION

# cleanup
rm -rf target/*

# parse version
VERSION=$(grep '<version>' ../pom.xml | head -1 | egrep  -o '[0-9.]+')

# files
cp -r resources/files target
mkdir -p target/files/lib
cp resources/pairgoth.ico target/files
cp ../application/target/pairgoth-engine.jar target/files/lib
cp -r resources/data target/data

# jre
unzip -d target/files resources/jre.zip

# installer script
sed -r -e "s/@VERSION@/$VERSION.0.0/g" resources/installer.nsi > target/installer.nsi
cat target/installer.nsi | makensis -V4 -
