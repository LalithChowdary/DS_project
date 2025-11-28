package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Cloudlet;
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
 * Large-scale VM Failure experiment with configurable failure count
 * 4 Datacenters, 72 VMs, 2000 Tasks
 * Tests: 1, 4, 8, 12, 16 VM failures
 */
public class Experiment2_MultiVmFailure {

    public static void main(String[] args) {
        // Configurable failure count (default: 4)
        int failureCount = 4; // Change to 1, 4, 8, 12, or 16
        if (args.length > 0) {
            failureCount = Integer.parseInt(args[0]);
        }

        runExperiment(failureCount);
    }

    private static void runExperiment(int failureCount) {
        Log.printLine("=========================================");
        Log.printLine("Experiment 2: VM Failure (Large Scale)");
        Log.printLine("Setup: 4 DC, 72 VMs, 2000 Tasks");
        Log.printLine("Failing VMs: " + failureCount);
        Log.printLine("=========================================");

        try {
            // 1. Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 2. Create 4 Datacenters
            for (int i = 0; i < 4; i++) {
                PerformanceHelper.createDatacenter("Datacenter_" + i, i);
            }

            // 3. Create Broker
            ProposedBroker broker = new ProposedBroker("ProposedBroker", 1);

            // 4. Create 72 VMs
            List<Vm> vms = PerformanceHelper.createVms(broker.getId());
            broker.submitGuestList(vms);

            // 5. Create 2000 Cloudlets
            List<ProposedCloudlet> cloudlets = PerformanceHelper.createCloudlets(broker.getId(), "PROPOSED");
            broker.submitCloudletList(cloudlets);

            // 6. Schedule VM Failures based on count
            int[] failingVms = getFailingVms(failureCount);
            double[] failureTimes = getFailureTimes(failureCount);

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
            printMetrics(failureCount + " VM Failures", result, endTime - startTime, failureCount);

            Log.printLine("\nExperiment 2 (" + failureCount + " VM Failures) finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static int[] getFailingVms(int count) {
        // Distribute failures across all 72 VMs
        // Use every (72/count)th VM
        int[] vms = new int[count];
        int step = 72 / count;
        for (int i = 0; i < count; i++) {
            vms[i] = 5 + (i * step); // Start from VM #5
        }
        return vms;
    }

    private static double[] getFailureTimes(int count) {
        // Stagger failures from 3000s to 10000s
        double[] times = new double[count];
        double interval = (count > 1) ? 7000.0 / (count - 1) : 0;
        for (int i = 0; i < count; i++) {
            times[i] = 3000.0 + (i * interval);
        }
        return times;
    }

    private static void printMetrics(String algo, List<? extends Cloudlet> list, long realTimeMs, int failedVmCount) {
        int successCount = 0;
        double totalTrueResponseTime = 0; // Use TRUE response time (first submit to final complete)
        double minStart = Double.MAX_VALUE;
        double maxFinish = Double.MIN_VALUE;
        java.util.Set<Integer> uniqueTaskIds = new java.util.HashSet<>();

        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                successCount++;
                uniqueTaskIds.add(c.getCloudletId());

                // Use getTrueResponseTime() for retried tasks
                double responseTime;
                if (c instanceof ProposedCloudlet) {
                    responseTime = ((ProposedCloudlet) c).getTrueResponseTime();
                } else {
                    responseTime = c.getFinishTime() - c.getSubmissionTime();
                }
                totalTrueResponseTime += responseTime;

                if (c.getSubmissionTime() < minStart)
                    minStart = c.getSubmissionTime();
                if (c.getFinishTime() > maxFinish)
                    maxFinish = c.getFinishTime();
            }
        }

        double avgTrueResponseTime = (successCount > 0) ? totalTrueResponseTime / successCount : 0;
        double throughput = (maxFinish > minStart) ? successCount / (maxFinish - minStart) : 0;
        double makespan = maxFinish - minStart;

        java.text.DecimalFormat dft = new java.text.DecimalFormat("###.##");
        Log.printLine("\n==========================================");
        Log.printLine("Results for PROPOSED (" + algo + ")");
        Log.printLine("==========================================");
        Log.printLine(
                "Failed VMs: " + failedVmCount + " out of 72 VMs (" + dft.format((failedVmCount * 100.0) / 72) + "%)");
        Log.printLine("Total Executions (including retries): " + list.size());
        Log.printLine("Unique Tasks Completed: " + uniqueTaskIds.size() + " / 2000");
        Log.printLine("Total Successful Executions: " + successCount);
        Log.printLine("Success Rate (Unique Tasks): " + dft.format((uniqueTaskIds.size() * 100.0) / 2000) + "%");
        Log.printLine("Avg TRUE Response Time: " + dft.format(avgTrueResponseTime) + " s (includes retry overhead)");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/s");
        Log.printLine("Makespan (Total Time): " + dft.format(makespan) + " s");
        Log.printLine("Real Execution Time: " + (realTimeMs / 1000.0) + " s");
        Log.printLine("==========================================\n");
    }
}
