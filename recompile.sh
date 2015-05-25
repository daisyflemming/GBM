#!/bin/sh                                                                       
DIR=`pwd`
CLASSPATH=$DIR/lib/guava-18.0.jar:$DIR/lib/opencsv-3.3.jar

javac -classpath $CLASSPATH src/com/daisyflemming/AlterationFrequency.java
mv src/com/daisyflemming/AlterationFrequency.class $DIR/out/production/gbm_summarize/com/daisyflemming/
