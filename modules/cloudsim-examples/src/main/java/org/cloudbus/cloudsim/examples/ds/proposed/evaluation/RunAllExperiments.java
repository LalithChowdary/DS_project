package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Log;

public class RunAllExperiments {

    public static void main(String[] args) {
        Log.printLine("==========================================");
        Log.printLine("Running All Experiments for Research Paper");
        Log.printLine("==========================================");

        // Run Experiment 1
        Experiment1_Baseline.main(args);
        Log.printLine("\n------------------------------------------\n");

        // Run Experiment 2
        Experiment2_VmFailure.main(args);
        Log.printLine("\n------------------------------------------\n");

        // Run Experiment 3
        Experiment3_LbFailure.main(args);
        Log.printLine("\n------------------------------------------\n");

        // Run Experiment 4
        Experiment4_WorkStealing.main(args);
        Log.printLine("\n------------------------------------------\n");

        Log.printLine("All Experiments Completed.");
    }
}
