#!/bin/bash
FILES=$(mktemp)
find src -name *.java > $FILES
cat $FILES

javac -classpath lib/jSerialComm-1.3.11.jar -d bin -Xdiags:verbose @${FILES}
rm $FILES
