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
 * Load Balancer Failover experiment with FULL-SCALE setup
 * Same setup as EXPERIMENT_SETUP.md: 4 DC, 72 VMs, 2000 Tasks
 * Each broker manages 2 DCs with 36 VMs and 1000 tasks
 */
public class Experiment3_LbFailure_LargeScale {

    public static void main(String[] args) {
        Log.printLine("=========================================");
        Log.printLine("Experiment 3: LB Failover (Large Scale)");
        Log.printLine("Setup: 4 DC, 72 VMs, 2000 Tasks");
        Log.printLine("Dual-Broker Architecture");
        Log.printLine("=========================================");

        try {
            // 1. Initialize CloudSim
            int num_user = 2; // Two brokers
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 2. Create 4 Datacenters
            for (int i = 0; i < 4; i++) {
                PerformanceHelper.createDatacenter("Datacenter_" + i, i);
            }

            // 3. Create Two Brokers (Region A and Region B)
            ProposedBroker lb1 = new ProposedBroker("LB1", 1);
            ProposedBroker lb2 = new ProposedBroker("LB2", 2);

            // 4. Set brokers as peers for failover
            lb1.setOtherBroker(lb2);
            lb2.setOtherBroker(lb1);

            // 5. Create 72 VMs - split between brokers
            List<Vm> allVms = PerformanceHelper.createVms(0); // Get base VMs
            List<Vm> lb1Vms = new java.util.ArrayList<>();
            List<Vm> lb2Vms = new java.util.ArrayList<>();

            // Split VMs: First 36 to LB1, Last 36 to LB2
            for (int i = 0; i < allVms.size(); i++) {
                Vm vm = allVms.get(i);
                if (i < 36) {
                    vm.setUserId(lb1.getId());
                    lb1Vms.add(vm);
                } else {
                    vm.setUserId(lb2.getId());
                    lb2Vms.add(vm);
                }
            }

            lb1.submitGuestList(lb1Vms);
            lb2.submitGuestList(lb2Vms);

            Log.printLine("LB1: Managing " + lb1Vms.size() + " VMs (Datacenters 0-1)");
            Log.printLine("LB2: Managing " + lb2Vms.size() + " VMs (Datacenters 2-3)");

            // 6. Create 2000 Cloudlets - split between brokers
            List<ProposedCloudlet> allCloudlets = PerformanceHelper.createCloudlets(0, "PROPOSED");
            List<ProposedCloudlet> lb1Cloudlets = new java.util.ArrayList<>();
            List<ProposedCloudlet> lb2Cloudlets = new java.util.ArrayList<>();

            // Split tasks: First 1000 to LB1, Last 1000 to LB2
            for (int i = 0; i < allCloudlets.size(); i++) {
                ProposedCloudlet cloudlet = allCloudlets.get(i);
                if (i < 1000) {
                    cloudlet.setUserId(lb1.getId());
                    lb1Cloudlets.add(cloudlet);
                } else {
                    cloudlet.setUserId(lb2.getId());
                    lb2Cloudlets.add(cloudlet);
                }
            }

            lb1.submitCloudletList(lb1Cloudlets);
            lb2.submitCloudletList(lb2Cloudlets);

            Log.printLine("LB1: Managing " + lb1Cloudlets.size() + " tasks");
            Log.printLine("LB2: Managing " + lb2Cloudlets.size() + " tasks");

            // 7. Schedule LB2 Failure at T=5000s
            double failureTime = 5000.0;
            Log.printLine("=========================================");
            Log.printLine("Scheduling LB2 failure at " + failureTime + "s");
            Log.printLine("=========================================");
            CloudSim.send(lb2.getId(), lb2.getId(), failureTime, ProposedTags.INJECT_LB_FAILURE, null);

            // 8. Start Simulation
            long startTime = System.currentTimeMillis();
            CloudSim.startSimulation();
            CloudSim.stopSimulation();
            long endTime = System.currentTimeMillis();

            // 9. Collect Results from both brokers
            List<ProposedCloudlet> lb1Results = lb1.getCloudletReceivedList();
            List<ProposedCloudlet> lb2Results = lb2.getCloudletReceivedList();

            Log.printLine("\n--- Experiment 3 (Large Scale) Results ---\n");
            printMetrics("LB1 (Survivor)", lb1Results, endTime - startTime);
            printMetrics("LB2 (Failed)", lb2Results, 0);

            // Combined results
            List<ProposedCloudlet> combined = new java.util.ArrayList<>();
            combined.addAll(lb1Results);
            combined.addAll(lb2Results);
            printMetrics("COMBINED (Both Brokers)", combined, endTime - startTime);

            Log.printLine("\nExperiment 3 (Large Scale LB Failover) finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void printMetrics(String algo, List<? extends Cloudlet> list, long realTimeMs) {
        int successCount = 0;
        double totalTrueResponseTime = 0;
        double minStart = Double.MAX_VALUE;
        double maxFinish = Double.MIN_VALUE;
        java.util.Set<Integer> uniqueTaskIds = new java.util.HashSet<>();

        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                successCount++;
                uniqueTaskIds.add(c.getCloudletId());

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
        double makespan = (maxFinish > minStart) ? maxFinish - minStart : 0;

        java.text.DecimalFormat dft = new java.text.DecimalFormat("###.##");
        Log.printLine("\nResults for " + algo);
        Log.printLine("Tasks Submitted: " + list.size());
        Log.printLine("Unique Tasks Completed: " + uniqueTaskIds.size());
        Log.printLine("Total Successful Executions: " + successCount);
        Log.printLine("Success Rate: " + dft.format((uniqueTaskIds.size() * 100.0) / list.size()) + "%");
        Log.printLine("Avg TRUE Response Time: " + dft.format(avgTrueResponseTime) + " s");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/s");
        Log.printLine("Makespan: " + dft.format(makespan) + " s");
        if (realTimeMs > 0) {
            Log.printLine("Real Execution Time: " + (realTimeMs / 1000.0) + " s");
        }
    }
}
