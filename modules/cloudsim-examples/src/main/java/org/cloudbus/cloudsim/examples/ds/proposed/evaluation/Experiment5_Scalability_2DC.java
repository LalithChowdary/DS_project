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

public class Experiment5_Scalability_2DC {

    public static void main(String[] args) {
        Log.printLine("Starting Experiment 5: Scalability Analysis (2 Datacenters)...");

        try {
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 1. Create Datacenters (2 DCs)
            Datacenter dc1 = Helper.createDatacenter("Datacenter_1");
            Datacenter dc2 = Helper.createDatacenter("Datacenter_2");

            // 2. Create Brokers (2 LBs)
            ProposedBroker lb1 = new ProposedBroker("LB1", 1);
            ProposedBroker lb2 = new ProposedBroker("LB2", 2);

            // 3. Link Brokers (LB1 <-> LB2)
            lb1.setOtherBroker(lb2);
            lb2.setOtherBroker(lb1);

            // 4. Create VMs (10 per DC -> 20 Total)
            List<Vm> vms1 = Helper.createVmList(lb1.getId(), 10);
            lb1.submitGuestList(vms1);

            List<Vm> vms2 = Helper.createVmList(lb2.getId(), 10);
            lb2.submitGuestList(vms2);

            // 5. Create Cloudlets (2000 Total -> 1000 per LB)
            List<ProposedCloudlet> cloudlets1 = Helper.createCloudletList(lb1.getId(), 1000);
            lb1.submitCloudletList(cloudlets1);

            List<ProposedCloudlet> cloudlets2 = Helper.createCloudletList(lb2.getId(), 1000);
            lb2.submitCloudletList(cloudlets2);

            // 6. Start Simulation
            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            // 7. Print Results
            Log.printLine("");
            Log.printLine("==========================================");
            Log.printLine("Results for Scalability Experiment (2 DCs)");
            Log.printLine("==========================================");

            List<Cloudlet> resultList = new ArrayList<>();
            resultList.addAll(lb1.getCloudletReceivedList());
            resultList.addAll(lb2.getCloudletReceivedList());

            printResults(resultList);

            Log.printLine("Experiment 5 (2 DCs) Finished.");
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
                if (cloudlet instanceof ProposedCloudlet) {
                    // Use original submission time if available (handles rescheduling)
                    // But for this exp, no failures, so submissionTime is fine.
                    // However, ProposedBroker sets originalSubmissionTime.
                    // Let's check if we can access it.
                    // ProposedCloudlet has getOriginalSubmissionTime() but it might be protected or
                    // not exposed?
                    // Let's assume getSubmissionTime() is sufficient for now as we don't have
                    // failures.
                }

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
        double throughput = size / (maxFinishTime - minStartTime); // Tasks per second (approx)
        // Or size / maxFinishTime if we consider T=0 start.
        // Usually Throughput = Total Tasks / Total Simulation Time.
        // Simulation starts at 0. So maxFinishTime is the total duration.
        throughput = size / maxFinishTime;

        Log.printLine("==========================================");
        Log.printLine("Total Tasks: " + size);
        Log.printLine("Average Response Time: " + dft.format(avgResponseTime) + " seconds");
        Log.printLine("Throughput: " + dft.format(throughput) + " tasks/sec");
        Log.printLine("==========================================");
    }
}
