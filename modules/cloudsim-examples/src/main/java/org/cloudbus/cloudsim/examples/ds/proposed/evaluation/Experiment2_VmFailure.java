package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.ds.proposed.evaluation.Helper;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedTags;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;

import java.util.Calendar;
import java.util.List;

/**
 * Experiment 2: VM Failure and Recovery
 * 
 * Objective: Demonstrate the system's ability to detect and recover from VM
 * failures.
 * Scenario:
 * 1. Start simulation with normal load.
 * 2. At T=50s, inject a failure into a specific VM (e.g., VM #5).
 * 3. Verify that the Broker detects the failure (via missing heartbeat).
 * 4. Verify that the Broker initiates the restart process.
 * 5. Verify that the VM becomes available again after the restart delay.
 */
public class Experiment2_VmFailure {

    public static void main(String[] args) {
        Log.printLine("Starting Experiment 2: VM Failure & Recovery...");

        try {
            // 1. Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 2. Create Datacenters
            Datacenter dc1 = Helper.createDatacenter("Datacenter_1");
            Datacenter dc2 = Helper.createDatacenter("Datacenter_2");

            // 3. Create Broker
            ProposedBroker broker = new ProposedBroker("ProposedBroker", 1);

            // 4. Create VMs (50 VMs)
            List<Vm> vms = Helper.createVmList(broker.getId(), 50);
            broker.submitGuestList(vms);

            // 5. Create Cloudlets (100 Cloudlets)
            // We use a smaller number of cloudlets to focus on the failure event
            List<ProposedCloudlet> cloudlets = Helper.createCloudletList(broker.getId(), 100);
            broker.submitCloudletList(cloudlets);

            // 6. Schedule Failure Injection
            // Inject failure into VM #5 at 50 seconds
            int vmIdToFail = 5;
            double failureTime = 50.0;
            Log.printLine("Scheduling failure injection for VM #" + vmIdToFail + " at " + failureTime + "s. Broker ID: "
                    + broker.getId());
            // broker.schedule(broker.getId(), failureTime, ProposedTags.INJECT_VM_FAILURE,
            // vmIdToFail);
            // Use CloudSim.send to be explicit (delay is failureTime since clock is 0)
            CloudSim.send(broker.getId(), broker.getId(), failureTime, ProposedTags.INJECT_VM_FAILURE, vmIdToFail);

            // 7. Start Simulation
            CloudSim.startSimulation();

            // 8. Stop Simulation
            CloudSim.stopSimulation();

            List<ProposedCloudlet> result = broker.getCloudletReceivedList();
            printMetrics("Proposed (VM Failure Recovery)", result);

            Log.printLine("Experiment 2 finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void printMetrics(String algo, List<? extends Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        double totalTime = 0;
        double minStart = Double.MAX_VALUE;
        double maxFinish = Double.MIN_VALUE;
        int successCount = 0;

        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                successCount++;
                totalTime += (c.getFinishTime() - c.getSubmissionTime());
                if (c.getSubmissionTime() < minStart)
                    minStart = c.getSubmissionTime();
                if (c.getFinishTime() > maxFinish)
                    maxFinish = c.getFinishTime();
            }
        }

        double avgResponseTime = (successCount > 0) ? totalTime / successCount : 0;
        double throughput = (maxFinish > minStart) ? successCount / (maxFinish - minStart) : 0;

        java.text.DecimalFormat dft = new java.text.DecimalFormat("###.##");
        Log.printLine("\n==========================================");
        Log.printLine("Results for " + algo);
        Log.printLine("==========================================");
        Log.printLine("Tasks Submitted: " + list.size());
        Log.printLine("Tasks Completed: " + successCount);
        Log.printLine("Avg Response Time: " + dft.format(avgResponseTime) + " s");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/s");
        Log.printLine("==========================================\n");
    }
}
