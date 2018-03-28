#!/bin/bash
FILES=$(mktemp)
find src -name *.java > $FILES
cat $FILES
mkdir -p bin
javac -classpath lib/jSerialComm-1.3.11.jar -d bin -Xdiags:verbose -Xlint:unchecked @${FILES}
rm $FILES
