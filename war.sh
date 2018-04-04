#!/bin/bash
FILES=$(mktemp)
T=tmp
W=$T/WEB-INF
find src -name *.java > $FILES
cat $FILES
mkdir -p $W/classes $W/lib bin
javac -classpath $CATALINA_HOME/lib/servlet-api.jar:lib/jSerialComm-1.3.11.jar -d $W/classes -Xdiags:verbose -Xlint:unchecked @${FILES}
cp lib/* $W/lib
cp res/web.xml $W
cp -a res/html $T
jar cf bin/x10.war -C $T .
find $T
rm -rf $T $FILES
