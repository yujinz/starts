# An Optimization of STARTS

STARTS is a maven plugin for regression test selection, which enables the developers to only run tests that are impacted by the changes since the last build. It does the selection by maintaining a dependency graph of all source and test classes.

This repository implements an optimization of STARTS by running the execution phase (run affected tests) and the graph computation phase (find dependencies) concurrently (see figure below). This saved time solely spent on graph computation phase by 70%. In theory, it can speedup the overall running time of the plugin by 7.5% according to [this paper](https://dl.acm.org/doi/10.5555/3155562.3155684). In practice, the implementation achieved 5%. https://dl.acm.org/doi/10.5555/3155562.3155684

<img src="https://docs.google.com/drawings/d/e/2PACX-1vQ96OfimUFk4tom4YO6kzyiRAwN2gT8pgegFu7bBT-n5VShMP9OY3x0sMiwdyf65PVIOQGw5I9GIhZD/pub?w=740&amp;h=310">

Below is the original readme of STARTS with a new usage of the optimized version added to [Major Functionality](#major-functionality) section.

# STARTS (*STA*tic *R*egression *T*est *S*election) Overview

[![Build Status](https://travis-ci.org/TestingResearchIllinois/starts.svg?branch=master)](https://travis-ci.org/TestingResearchIllinois/starts)

STARTS is a tool for static class-level regression test selection tool
for Maven-based Java programs.

## Prerequisites

1. Java 1.8
2. Maven 3.2.5 and above
3. Maven Surefire 2.14 and above
4. Operating System: Linux or OSX

## Integrating STARTS Plugin from Maven Central

Change the pom.xml to add the configuration for the STARTS plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>edu.illinois</groupId>
      <artifactId>starts-maven-plugin</artifactId>
      <version>1.3</version>
    </plugin>
  </plugins>
</build>
```

## Building STARTS from source

1. `git clone https://github.com/TestingResearchIllinois/starts`
2. `cd starts`
3. `mvn install`

## Using the STARTS Maven Plugin

### Available Options

1. To see all the goals that STARTS provides, run `mvn starts:help`
2. To see the details for any of the goals, run `mvn starts:help -Ddetail=true -Dgoal=<goal>`;
   replace `<goal>` with the goal of interest.

### Major Functionality 

1. To see the **types** that changed since the last time STARTS was run:
  `mvn starts:diff`
2. To see the **types** that may be impacted by changes since the last
  time STARTS was run: `mvn starts:impacted`
3. To see the **tests** that are affected by the most recent changes:
  `mvn starts:select`
4. To perform RTS using STARTS (i.e., select tests and run the
  selected tests): `mvn starts:starts`
  - ***To perform optimized RTS: `mvn starts:starts -DwriteNonAffected=true -DofflineMode=true`***
5. To remove all artifacts that STARTS stores between versions
  (i.e. in the `.starts` directories): `mvn starts:clean`

__NOTE:__ By default, commands (1) - (3) *will not* update the
checksums of files in the latest version, while the command in (4)
*will* update the checksums of the files. Each command has a
`update**Checksums` option that can be used to change the default
behavior. For example, to update the checksums while checking the
diff, run `mvn starts:diff -DupdateDiffChecksums=true`.
