package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedTags;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics.PerformanceHelper;

import java.util.Calendar;
import java.util.List;

/**
 * Large-scale VM Failure experiment using performance metrics setup
 * 4 Datacenters, 72 VMs, 2000 Tasks
 */
public class Experiment2_VmFailure_LargeScale {

    public static void main(String[] args) {
        Log.printLine("=========================================");
        Log.printLine("Experiment 2: VM Failure (Large Scale)");
        Log.printLine("Setup: 4 DC, 72 VMs, 2000 Tasks");
        Log.printLine("=========================================");

        try {
            // 1. Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 2. Create 4 Datacenters (same as RunSingleExperiment)
            for (int i = 0; i < 4; i++) {
                PerformanceHelper.createDatacenter("Datacenter_" + i, i);
            }

            // 3. Create Broker
            ProposedBroker broker = new ProposedBroker("ProposedBroker", 1);

            // 4. Create 72 VMs (same distribution as performance experiments)
            List<Vm> vms = PerformanceHelper.createVms(broker.getId());
            broker.submitGuestList(vms);

            // 5. Create 2000 Cloudlets
            List<ProposedCloudlet> cloudlets = PerformanceHelper.createCloudlets(broker.getId(), "PROPOSED");
            broker.submitCloudletList(cloudlets);

            // 6. Schedule MULTIPLE VM Failures (15 VMs at different times)
            // This stress-tests the recovery system
            int[] failingVms = { 5, 12, 18, 25, 31, 38, 42, 49, 55, 58, 63, 67, 70, 45, 22 };
            double[] failureTimes = { 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000, 8500, 9000,
                    9500, 10000 };

            Log.printLine("=========================================");
            Log.printLine("Scheduling " + failingVms.length + " VM failures:");
            for (int i = 0; i < failingVms.length; i++) {
                Log.printLine("  VM #" + failingVms[i] + " will fail at " + failureTimes[i] + "s");
                CloudSim.send(broker.getId(), broker.getId(), failureTimes[i],
                        ProposedTags.INJECT_VM_FAILURE, failingVms[i]);
            }
            Log.printLine("=========================================");

            // 7. Start Simulation
            long startTime = System.currentTimeMillis();
            CloudSim.startSimulation();
            CloudSim.stopSimulation();
            long endTime = System.currentTimeMillis();

            // 8. Collect Results
            List<ProposedCloudlet> result = broker.getCloudletReceivedList();
            printMetrics("PROPOSED (15 VM Failures)", result, endTime - startTime, failingVms.length);

            Log.printLine("\nExperiment 2 (Large Scale - Multiple Failures) finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void printMetrics(String algo, List<? extends Cloudlet> list, long realTimeMs, int failedVmCount) {
        int successCount = 0;
        double totalTime = 0;
        double minStart = Double.MAX_VALUE;
        double maxFinish = Double.MIN_VALUE;
        java.util.Set<Integer> uniqueTaskIds = new java.util.HashSet<>();

        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                successCount++;
                uniqueTaskIds.add(c.getCloudletId()); // Track unique task IDs
                totalTime += (c.getFinishTime() - c.getSubmissionTime());
                if (c.getSubmissionTime() < minStart)
                    minStart = c.getSubmissionTime();
                if (c.getFinishTime() > maxFinish)
                    maxFinish = c.getFinishTime();
            }
        }

        double avgResponseTime = (successCount > 0) ? totalTime / successCount : 0;
        double throughput = (maxFinish > minStart) ? successCount / (maxFinish - minStart) : 0;
        double makespan = maxFinish - minStart;

        java.text.DecimalFormat dft = new java.text.DecimalFormat("###.##");
        Log.printLine("\n==========================================");
        Log.printLine("Results for " + algo);
        Log.printLine("==========================================");
        Log.printLine("Failed VMs: " + failedVmCount + " out of 72 VMs");
        Log.printLine("Total Executions (including retries): " + list.size());
        Log.printLine("Unique Tasks Completed: " + uniqueTaskIds.size() + " / 2000");
        Log.printLine("Total Successful Executions: " + successCount);
        Log.printLine("Success Rate (Unique Tasks): " + dft.format((uniqueTaskIds.size() * 100.0) / 2000) + "%");
        Log.printLine("Avg Response Time: " + dft.format(avgResponseTime) + " s");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/s");
        Log.printLine("Makespan (Total Time): " + dft.format(makespan) + " s");
        Log.printLine("Real Execution Time: " + (realTimeMs / 1000.0) + " s");
        Log.printLine("==========================================\n");
    }
}
