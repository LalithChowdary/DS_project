package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedTags;
import org.cloudbus.cloudsim.examples.ds.proposed.evaluation.Helper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Experiment3_LbFailure {

    public static void main(String[] args) {
        Log.printLine("Starting Experiment 3: Load Balancer Failover...");

        try {
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 1. Create Datacenters
            Datacenter dc1 = Helper.createDatacenter("Datacenter_1");
            Datacenter dc2 = Helper.createDatacenter("Datacenter_2");

            // 2. Create Brokers (LB1 and LB2)
            ProposedBroker lb1 = new ProposedBroker("LB1", 1);
            ProposedBroker lb2 = new ProposedBroker("LB2", 2);

            // 3. Link Brokers
            lb1.setOtherBroker(lb2);
            lb2.setOtherBroker(lb1);

            // 4. Create VMs
            // LB1 manages Region A (DC1), LB2 manages Region B (DC2)
            List<Vm> vms1 = Helper.createVmList(lb1.getId(), 20); // 20 VMs for LB1
            List<Vm> vms2 = Helper.createVmList(lb2.getId(), 20); // 20 VMs for LB2

            lb1.submitGuestList(vms1);
            lb2.submitGuestList(vms2);

            // 5. Create Cloudlets
            List<ProposedCloudlet> cloudlets1 = Helper.createCloudletList(lb1.getId(), 50);
            List<ProposedCloudlet> cloudlets2 = Helper.createCloudletList(lb2.getId(), 50);

            lb1.submitCloudletList(cloudlets1);
            lb2.submitCloudletList(cloudlets2);

            // 6. Schedule LB Failure
            // Inject failure into LB2 at T=50s
            double failureTime = 50.0;
            Log.printLine("Scheduling failure injection for LB2 at " + failureTime + "s.");
            CloudSim.send(lb2.getId(), lb2.getId(), failureTime, ProposedTags.INJECT_LB_FAILURE, null);

            // 7. Start Simulation
            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            Log.printLine("\n--- Experiment 3 Results ---");
            List<ProposedCloudlet> result1 = lb1.getCloudletReceivedList();
            List<ProposedCloudlet> result2 = lb2.getCloudletReceivedList();

            printMetrics("LB1 (Survivor)", result1);
            printMetrics("LB2 (Failed)", result2);

            Log.printLine("Experiment 3 Finished.");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void printMetrics(String algo, List<? extends Cloudlet> list) {
        int size = list.size();
        int successCount = 0;
        double totalTime = 0;
        double minStart = Double.MAX_VALUE;
        double maxFinish = Double.MIN_VALUE;

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
        Log.printLine("\nResults for " + algo);
        Log.printLine("Tasks Submitted: " + size);
        Log.printLine("Tasks Completed: " + successCount);
        Log.printLine("Avg Response Time: " + dft.format(avgResponseTime) + " s");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/s");
    }
}
