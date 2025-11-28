package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.examples.ds.proposed.evaluation.Helper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.text.DecimalFormat;

public class Experiment5_Scalability_4DC {

    public static void main(String[] args) {
        Log.printLine("Starting Experiment 5: Scalability Analysis (4 Datacenters)...");

        try {
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            int numDCs = 4;
            int vmsPerDC = 10;
            int totalTasks = 2000;
            int tasksPerLB = totalTasks / numDCs;

            List<Datacenter> dcs = new ArrayList<>();
            List<ProposedBroker> brokers = new ArrayList<>();

            // 1. Create Datacenters and Brokers
            for (int i = 1; i <= numDCs; i++) {
                Datacenter dc = Helper.createDatacenter("Datacenter_" + i);
                dcs.add(dc);

                ProposedBroker lb = new ProposedBroker("LB" + i, i);
                brokers.add(lb);
            }

            // 2. Link Brokers (Ring Topology)
            for (int i = 0; i < numDCs; i++) {
                ProposedBroker current = brokers.get(i);
                ProposedBroker next = brokers.get((i + 1) % numDCs);
                current.setOtherBroker(next);
            }

            // 3. Create VMs and Cloudlets
            for (ProposedBroker lb : brokers) {
                // VMs
                List<Vm> vms = Helper.createVmList(lb.getId(), vmsPerDC);
                lb.submitGuestList(vms);

                // Cloudlets
                List<ProposedCloudlet> cloudlets = Helper.createCloudletList(lb.getId(), tasksPerLB);
                lb.submitCloudletList(cloudlets);
            }

            // 4. Start Simulation
            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            // 5. Print Results
            Log.printLine("");
            Log.printLine("==========================================");
            Log.printLine("Results for Scalability Experiment (4 DCs)");
            Log.printLine("==========================================");

            List<Cloudlet> resultList = new ArrayList<>();
            for (ProposedBroker lb : brokers) {
                resultList.addAll(lb.getCloudletReceivedList());
            }

            printResults(resultList);

            Log.printLine("Experiment 5 (4 DCs) Finished.");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static void printResults(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        double totalResponseTime = 0;
        double minStartTime = Double.MAX_VALUE;
        double maxFinishTime = Double.MIN_VALUE;

        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.print("SUCCESS");

                double submissionTime = cloudlet.getSubmissionTime();
                double responseTime = cloudlet.getFinishTime() - submissionTime;
                totalResponseTime += responseTime;

                if (cloudlet.getExecStartTime() < minStartTime) {
                    minStartTime = cloudlet.getExecStartTime();
                }
                if (cloudlet.getFinishTime() > maxFinishTime) {
                    maxFinishTime = cloudlet.getFinishTime();
                }

                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId()
                        + indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent
                        + dft.format(cloudlet.getExecStartTime()) + indent + indent
                        + dft.format(cloudlet.getFinishTime()));
            }
        }

        double avgResponseTime = totalResponseTime / size;
        double throughput = size / maxFinishTime;

        Log.printLine("==========================================");
        Log.printLine("Total Tasks: " + size);
        Log.printLine("Average Response Time: " + dft.format(avgResponseTime) + " seconds");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/sec");
        Log.printLine("==========================================");
    }
}
