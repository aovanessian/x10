#!/bin/bash
FILES=$(mktemp)
T=tmp
find src -name *.java ! -path *http* > $FILES
cat $FILES
rm -rf $T
mkdir -p $T bin
javac -classpath lib/jSerialComm-1.3.11.jar -d $T -Xdiags:verbose -Xlint:unchecked @${FILES}
cd $T
jar xf ../lib/*jar
rm -rf META-INF
jar cfe ../bin/x10.jar ca.tpmd.x10.X10 *
cd ..
rm -rf $T $FILES
