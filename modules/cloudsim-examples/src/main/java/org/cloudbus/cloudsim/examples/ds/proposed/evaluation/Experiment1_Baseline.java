package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.List;

public class Experiment1_Baseline {

    public static void main(String[] args) {
        Log.printLine("==========================================");
        Log.printLine("Experiment 1: Baseline Performance Comparison");
        Log.printLine("==========================================");

        runProposed();
        runRoundRobin();
        runThrottled();
    }

    private static void runProposed() {
        Log.printLine("\n--- Running Proposed Approach (SBDLB + MLFQ) ---");
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            Datacenter dc1 = Helper.createDatacenter("Datacenter_1");
            Datacenter dc2 = Helper.createDatacenter("Datacenter_2");

            ProposedBroker broker = new ProposedBroker("ProposedBroker", 1);

            List<Vm> vms = Helper.createVmList(broker.getId(), 50);
            broker.submitGuestList(vms);

            List<ProposedCloudlet> cloudlets = Helper.createCloudletList(broker.getId(), 100);
            broker.submitCloudletList(cloudlets);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<ProposedCloudlet> result = broker.getCloudletReceivedList();
            printMetrics("Proposed", result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runRoundRobin() {
        Log.printLine("\n--- Running Baseline: Round Robin ---");
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            Datacenter dc1 = Helper.createDatacenter("Datacenter_1");
            Datacenter dc2 = Helper.createDatacenter("Datacenter_2");

            DatacenterBroker broker = new DatacenterBroker("RoundRobinBroker");

            List<Vm> vms = Helper.createVmList(broker.getId(), 50);
            broker.submitGuestList(vms);

            List<ProposedCloudlet> cloudlets = Helper.createCloudletList(broker.getId(), 100);
            broker.submitCloudletList(cloudlets);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> result = broker.getCloudletReceivedList();
            printMetrics("RoundRobin", result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runThrottled() {
        Log.printLine("\n--- Running Baseline: Throttled ---");
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            Datacenter dc1 = Helper.createDatacenter("Datacenter_1");
            Datacenter dc2 = Helper.createDatacenter("Datacenter_2");

            ThrottledBroker broker = new ThrottledBroker("ThrottledBroker");

            List<Vm> vms = Helper.createVmList(broker.getId(), 50);
            broker.submitGuestList(vms);

            List<ProposedCloudlet> cloudlets = Helper.createCloudletList(broker.getId(), 100);
            broker.submitCloudletList(cloudlets);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            List<Cloudlet> result = broker.getCloudletReceivedList();
            printMetrics("Throttled", result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printMetrics(String algo, List<? extends Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        double totalTime = 0;
        double minStart = Double.MAX_VALUE;
        double maxFinish = Double.MIN_VALUE;

        for (Cloudlet c : list) {
            totalTime += (c.getFinishTime() - c.getSubmissionTime());
            if (c.getSubmissionTime() < minStart)
                minStart = c.getSubmissionTime();
            if (c.getFinishTime() > maxFinish)
                maxFinish = c.getFinishTime();
        }

        double avgResponseTime = totalTime / size;
        double throughput = size / (maxFinish - minStart);

        DecimalFormat dft = new DecimalFormat("###.##");
        Log.printLine("Algorithm: " + algo);
        Log.printLine("Tasks Executed: " + size);
        Log.printLine("Avg Response Time: " + dft.format(avgResponseTime) + " s");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/s");
    }
}
