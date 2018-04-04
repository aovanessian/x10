#!/bin/bash
FILES=$(mktemp)
T=tmp
find src -name *.java ! -path *http* > $FILES
cat $FILES
mkdir -p $T bin
rm -rf $T/*
javac -classpath lib/jSerialComm-1.3.11.jar -d $T -Xdiags:verbose -Xlint:unchecked @${FILES}
rm $FILES
echo "Main-Class: ca.tpmd.x10.X10" > manifest
cd $T
jar xf ../lib/*jar
rm -rf META-INF
jar cfm ../bin/x10.jar ../manifest *
cd ..
rm -rf $T manifest
