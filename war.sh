#!/bin/bash
FILES=$(mktemp)
DIR=war/WEB-INF
find src -name *.java > $FILES
cat $FILES
mkdir -p $DIR/classes $DIR/lib bin
javac -classpath $CATALINA_HOME/lib/servlet-api.jar:lib/jSerialComm-1.3.11.jar -d $DIR/classes -Xdiags:verbose -Xlint:unchecked @${FILES}
cp lib/* $DIR/lib
cp res/web.xml $DIR
cp -a res/html war
cd war
jar cf ../bin/x10.war .
cd ..
find war
rm -rf war
rm $FILES
