#!/bin/sh
DIR=`pwd`
CLASSPATH=$DIR/out/production/gbm_summarize
CLASSPATH=$CLASSPATH:$DIR/lib/guava-18.0.jar:$DIR/lib/opencsv-3.3.jar

if [ $# -lt 1 ]; then
    # print usage
    echo "\nPlease enter at least one gene symbol as argument\n"
    echo "Usage: $0 [geneSymbols]"
    echo "   e.g. $0 TP53"
    echo "        $0 TP53 MDM2\n"
    exit 1
fi
java -classpath $CLASSPATH com.daisyflemming.AlterationFrequency $*