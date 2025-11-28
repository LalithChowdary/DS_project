================================================================================
                        CLOUDSIM PROJECT INSTRUCTIONS
================================================================================

This document provides instructions on how to compile and run the CloudSim 
simulation examples.

PREREQUISITES:
--------------
- Java Development Kit (JDK) 21 or lower
- Apache Maven installed and configured in your system PATH

================================================================================
1. COMPILATION
================================================================================

To compile the entire project, run the following command from the root directory 
(where pom.xml is located):

    mvn clean install -DskipTests

This command will download necessary dependencies and compile all modules.

================================================================================
2. RUNNING THE EXAMPLES
================================================================================

You can run the examples using the Maven exec plugin. Run these commands from 
the root directory.

--------------------------------------------------------------------------------
A. Run CloudSimExampleSBDLB (Score-Based Load Balancer)
--------------------------------------------------------------------------------
File: modules/cloudsim-examples/src/main/java/org/cloudbus/cloudsim/examples/ds/CloudSimExampleSBDLB.java

Command:
    mvn exec:java -Dexec.mainClass="org.cloudbus.cloudsim.examples.ds.CloudSimExampleSBDLB" -pl modules/cloudsim-examples

--------------------------------------------------------------------------------
B. Run CloudSimExampleProposed (Proposed Architecture)
--------------------------------------------------------------------------------
File: modules/cloudsim-examples/src/main/java/org/cloudbus/cloudsim/examples/ds/proposed/CloudSimExampleProposed.java

Command:
    mvn exec:java -Dexec.mainClass="org.cloudbus.cloudsim.examples.ds.proposed.CloudSimExampleProposed" -pl modules/cloudsim-examples

