# Dependency Tracker

This code is based on the [ASM](http://asm.ow2.org/) example code.

I needed to refactor Hive such that the classes under
org.apache.hadoop.hive.ql.io.orc depend on as few classes
and libraries as possible. Therefore, I built a fat jar with
Hive and all of its transitive dependencies.

Starting from the classes in the orc package, I wanted to find the set
of classes that they indirectly depend on, but ignore a set of classes
that I wanted to ignore.

* *org.apache.hadoop* (but not *org.apache.hadoop.hive*)
* *java*
* *com.google.protobuf*

That still left 16k out of the 42k classes.

Next I computed the transitive dependency set for each class and I sorted
the classes first by their distance to one of my root classes and then by
the size of their transitive dependency set. That meant that the classes
I cared most about are at the front of the Depth 1 section, because they
are the classes that orc depends on directly that have large dependency
sets.

To run the program:

````
% mvn package
% cd hive
% mvn package
% cd ..
% java -jar target/depgraph-1.0-jar-with-dependencies.jar hive/target/bundle-1.0-jar-with-dependencies.jar
````