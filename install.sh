#!/bin/bash
rm -rf lib/*
mkdir -p lib
wget -O lib/gson-2.8.5.jar http://search.maven.org/remotecontent?filepath=com/google/code/gson/gson/2.8.5/gson-2.8.5.jar
wget -O lib/logicng-1.4.1.jar http://central.maven.org/maven2/org/logicng/logicng/1.4.1/logicng-1.4.1.jar
wget -O lib/antlr4-runtime-4.7.1.jar http://central.maven.org/maven2/org/antlr/antlr4-runtime/4.7.1/antlr4-runtime-4.7.1.jar
wget -O lib/jgrapht-core-1.3.0.jar http://central.maven.org/maven2/org/jgrapht/jgrapht-core/1.3.0/jgrapht-core-1.3.0.jar
wget -O lib/jheaps-0.9.jar http://central.maven.org/maven2/org/jheaps/jheaps/0.9/jheaps-0.9.jar

