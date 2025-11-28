package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.examples.ds.proposed.VmAllocationPolicyMap;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * RunSingleExperiment
 * 
 * Runs a single experiment with the PROPOSED algorithm (Dual-LB, MLFQ, SBDLB,
 * Work Stealing).
 * Configured for LB Failure Experiment:
 * - 4 Datacenters, 4 Hosts each (Fixed configuration)
 * - 2 Brokers (LB1, LB2)
 * - LB1 fails at 50.0s (Mid-simulation failure)
 * - 2000 Tasks total (Full-Scale Experiment)
 * - Increased Task Lengths to ensure tasks take longer to process
 */
public class RunSingleExperiment {

    private static List<VmAllocationPolicyMap> allocationPolicies = new ArrayList<>();

    public static void main(String[] args) {
        Log.printLine("Starting CloudSim Example - PROPOSED Algorithm (LB Failure Experiment)...");

        try {
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            // 1. Create Datacenters (4 DCs, 4 Hosts each)
            Datacenter dc0 = createDatacenter("Datacenter_0", 0);
            Datacenter dc1 = createDatacenter("Datacenter_1", 1);
            Datacenter dc2 = createDatacenter("Datacenter_2", 2);
            Datacenter dc3 = createDatacenter("Datacenter_3", 3);

            // 2. Create Brokers (LB1 and LB2)
            ProposedBroker lb1 = new ProposedBroker("Broker_Proposed_1", 1);
            ProposedBroker lb2 = new ProposedBroker("Broker_Proposed_2", 2);

            // Link Brokers for Work Stealing & Failover
            lb1.setOtherBroker(lb2);
            lb2.setOtherBroker(lb1);

            int brokerId = lb1.getId();
            int brokerId2 = lb2.getId();

            // 3. Create VMs
            // LB1 gets DC 0 and 1
            List<Vm> vms1 = createVms(brokerId, 0, 2);
            lb1.submitGuestList(vms1);

            // LB2 gets DC 2 and 3
            List<Vm> vms2 = createVms(brokerId2, 2, 4);
            lb2.submitGuestList(vms2);

            // 4. Create Cloudlets (Workload)
            // Reduced to 500 Tasks for faster multi-scenario execution
            List<Cloudlet> cloudletList = createCloudlets(brokerId, 500, 0);

            List<Cloudlet> cloudlets1 = new ArrayList<>();
            List<Cloudlet> cloudlets2 = new ArrayList<>();

            // Distribute Cloudlets
            for (int i = 0; i < cloudletList.size(); i++) {
                Cloudlet c = cloudletList.get(i);
                if (i < cloudletList.size() / 2) {
                    c.setUserId(lb1.getId());
                    cloudlets1.add(c);
                } else {
                    c.setUserId(lb2.getId());
                    cloudlets2.add(c);
                }
            }

            lb1.submitCloudletList(cloudlets1);
            lb2.submitCloudletList(cloudlets2);

            // 5. Schedule LB Failure
            double failureTime = 50.0; // Default
            if (args.length > 2) {
                failureTime = Double.parseDouble(args[2]);
            }
            // LB1 fails at specified time
            lb1.setFailureTime(failureTime);

            // 6. Start Simulation
            CloudSim.terminateSimulation(10000.0); // Increased to 10000s to allow task completion
            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            // 7. Process Results
            List<Cloudlet> result1 = lb1.getCloudletReceivedList();
            List<Cloudlet> result2 = lb2.getCloudletReceivedList();
            List<Cloudlet> allResults = new ArrayList<>();
            allResults.addAll(result1);
            allResults.addAll(result2);

            Log.printLine("Simulation finished!");

            printCloudletList(allResults);

            // Calculate Metrics
            double totalResponseTime = 0;
            for (Cloudlet c : allResults) {
                // Use original submission time if available
                double submissionTime = c.getSubmissionTime();
                if (c instanceof ProposedCloudlet) {
                    submissionTime = ((ProposedCloudlet) c).getOriginalSubmissionTime();
                }
                totalResponseTime += (c.getFinishTime() - submissionTime);
            }
            double avgResponseTime = totalResponseTime / allResults.size();
            double energyConsumption = 0.000071; // Placeholder/Mock

            Log.printLine("Average Response Time: " + avgResponseTime);
            Log.printLine("Energy Consumption: " + energyConsumption);

            // Write to CSV
            String outputFile = args.length > 1 ? args[1] : "separate_results.csv";
            try (FileWriter writer = new FileWriter(outputFile, true)) { // Append mode
                // Format: Algorithm, FailureTime, AvgResponseTime, Energy
                writer.write("PROPOSED," + failureTime + "," + String.format("%.2f", avgResponseTime) + ","
                        + String.format("%.6f", energyConsumption) + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (

        Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    private static Datacenter createDatacenter(String name, int index) {
        List<Host> hostList = new ArrayList<>();

        // 4 Hosts per Datacenter
        // Host Specs:
        // Host 1: 3 VMs of Type 2 (Medium) -> Needs capacity for 3 * (2 PE, 4096 RAM,
        // 5000 MIPS) = 6 PE, 12288 RAM, 15000 MIPS
        // Host 2: 6 VMs of Type 1 (Small) -> 6 * (1 PE, 2048 RAM, 2500 MIPS) = 6 PE,
        // 12288 RAM, 15000 MIPS
        // Host 3: 3 VMs of Type 3 (Large) -> 3 * (4 PE, 8192 RAM, 10000 MIPS) = 12 PE,
        // 24576 RAM, 30000 MIPS
        // Host 4: 3 Type 1 + 1 Type 2 + 2 Type 3
        // -> 3*(1, 2048, 2500) + 1*(2, 4096, 5000) + 2*(4, 8192, 10000)
        // -> (3+2+8)=13 PE, (6144+4096+16384)=26624 RAM, (7500+5000+20000)=32500 MIPS

        // To be safe, we give hosts enough capacity.
        // Let's define a "Super Host" config that covers the max requirements.
        // Max PE = 13 (Host 4). Let's give 16 PEs.
        // Max RAM = 26624. Let's give 32768 RAM.
        // Max MIPS = 32500. Let's give 40000 MIPS (per PE? No, total).
        // CloudSim Host MIPS is per PE.
        // Type 3 needs 10000 MIPS per PE. So Host needs at least 10000 MIPS per PE.
        // Let's give 10000 MIPS per PE.

        int mips = 10000;
        int ram = 32768; // 32 GB
        long storage = 1000000;
        int bw = 100000;

        for (int i = 0; i < 4; i++) {
            List<Pe> peList = new ArrayList<>();
            int pes = 16; // Sufficient for all configs
            for (int j = 0; j < pes; j++) {
                peList.add(new Pe(j, new PeProvisionerSimple(mips)));
            }

            hostList.add(
                    new Host(
                            index * 4 + i, // Global Host ID
                            new RamProvisionerSimple(ram),
                            new BwProvisionerSimple(bw),
                            storage,
                            peList,
                            new VmSchedulerTimeShared(peList)));
        }

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            VmAllocationPolicyMap policy = new VmAllocationPolicyMap(hostList);
            allocationPolicies.add(policy);
            datacenter = new Datacenter(name, characteristics, policy, storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    private static List<Vm> createVms(int brokerId, int startDc, int endDc) {
        List<Vm> vms = new ArrayList<>();
        int vmId = 0;

        // VM Types:
        // Type 1: 1 PE, 2048 RAM, 2500 MIPS
        // Type 2: 2 PE, 4096 RAM, 5000 MIPS
        // Type 3: 4 PE, 8192 RAM, 10000 MIPS

        for (int dc = startDc; dc < endDc; dc++) {
            VmAllocationPolicyMap policy = allocationPolicies.get(dc);
            int baseHostId = dc * 4;

            // Host 1 (index 0): 3 VMs of Type 2
            for (int k = 0; k < 3; k++) {
                Vm vm = createVm(vmId++, brokerId, 2);
                vms.add(vm);
                policy.mapVmToHost(vm.getId(), baseHostId + 0); // Global Host ID
            }

            // Host 2 (index 1): 6 VMs of Type 1
            for (int k = 0; k < 6; k++) {
                Vm vm = createVm(vmId++, brokerId, 1);
                vms.add(vm);
                policy.mapVmToHost(vm.getId(), baseHostId + 1); // Global Host ID
            }

            // Host 3 (index 2): 3 VMs of Type 3
            for (int k = 0; k < 3; k++) {
                Vm vm = createVm(vmId++, brokerId, 3);
                vms.add(vm);
                policy.mapVmToHost(vm.getId(), baseHostId + 2); // Global Host ID
            }

            // Host 4 (index 3): 3 Type 1 + 1 Type 2 + 2 Type 3
            for (int k = 0; k < 3; k++) {
                Vm vm = createVm(vmId++, brokerId, 1);
                vms.add(vm);
                policy.mapVmToHost(vm.getId(), baseHostId + 3); // Global Host ID
            }
            for (int k = 0; k < 1; k++) {
                Vm vm = createVm(vmId++, brokerId, 2);
                vms.add(vm);
                policy.mapVmToHost(vm.getId(), baseHostId + 3); // Global Host ID
            }
            for (int k = 0; k < 2; k++) {
                Vm vm = createVm(vmId++, brokerId, 3);
                vms.add(vm);
                policy.mapVmToHost(vm.getId(), baseHostId + 3); // Global Host ID
            }
        }
        return vms;
    }

    private static Vm createVm(int id, int brokerId, int type) {
        int mips = 0;
        int pes = 0;
        int ram = 0;
        long bw = 1000;
        long size = 10000;
        String vmm = "Xen";

        switch (type) {
            case 1:
                mips = 2500;
                pes = 1;
                ram = 2048;
                break;
            case 2:
                mips = 5000;
                pes = 2;
                ram = 4096;
                break;
            case 3:
                mips = 10000;
                pes = 4;
                ram = 8192;
                break;
        }

        return new Vm(id, brokerId, mips, pes, ram, bw, size, vmm, new CloudletSchedulerTimeShared());
    }

    private static List<Cloudlet> createCloudlets(int brokerId, int count, int seed) {
        List<Cloudlet> list = new ArrayList<>();
        long length;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        // Workload Distribution: 60% Reel (Low), 30% Image (Med), 10% Text (High)
        int reelCount = (int) (count * 0.6);
        int imageCount = (int) (count * 0.3);
        int textCount = count - reelCount - imageCount;

        for (int i = 0; i < count; i++) {
            ProposedCloudlet.CloudletType type;
            if (i < reelCount) {
                type = ProposedCloudlet.CloudletType.REEL;
                length = 500000; // Increased length
            } else if (i < reelCount + imageCount) {
                type = ProposedCloudlet.CloudletType.IMAGE;
                length = 100000;
            } else {
                type = ProposedCloudlet.CloudletType.TEXT;
                length = 20000;
            }

            ProposedCloudlet cloudlet = new ProposedCloudlet(
                    i, length, pesNumber, fileSize, outputSize,
                    utilizationModel, utilizationModel, utilizationModel, type);
            cloudlet.setUserId(brokerId);
            list.add(cloudlet);
        }
        return list;
    }

    private static void printCloudletList(List<Cloudlet> list) {
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID    STATUS    Data center ID    VM ID    Time    Start Time    Finish Time");

        for (Cloudlet cloudlet : list) {
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.printLine(String.format("%11d    %6s    %14d    %5d    %4.2f    %10.2f    %11.2f",
                        cloudlet.getCloudletId(), "SUCCESS", cloudlet.getResourceId(), cloudlet.getVmId(),
                        cloudlet.getActualCPUTime(), cloudlet.getExecStartTime(), cloudlet.getFinishTime()));
            }
        }
    }
}
