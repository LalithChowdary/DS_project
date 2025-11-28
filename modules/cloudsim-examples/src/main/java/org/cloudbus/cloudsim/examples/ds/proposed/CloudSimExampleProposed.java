package org.cloudbus.cloudsim.examples.ds.proposed;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.*;

/**
 * Main Runner for the Proposed Distributed CloudSim Architecture.
 * Features:
 * - Dual Load Balancers (LB1, LB2)
 * - MLFQ Scheduling (Text/Image/Reel)
 * - Two-Level Redis Cache (Mocked)
 * - Fault Tolerance & Work Stealing
 * 
 * Experimental Setup:
 * - 2 Datacenters (1 per LB/Zone)
 * - 4 Hosts per Datacenter (8 cores, 16GB RAM, 10K MIPS/core, 10Gbps, 1TB)
 * - 18 VMs per Datacenter: 10 Small + 6 Medium + 2 Large = 36 total VMs
 * - 500 Tasks
 */
public class CloudSimExampleProposed {

    private static final int NUM_DATACENTERS = 2; // 1 per zone
    private static final int VMS_PER_DATACENTER = 18; // 10 Small + 6 Medium + 2 Large
    private static final int TOTAL_TASKS = 500;

    public static void main(String[] args) {
        Log.printLine("Starting CloudSimExampleProposed...");

        try {
            // 1. Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 2. Create Datacenters
            List<Datacenter> datacenters = new ArrayList<>();
            for (int i = 0; i < NUM_DATACENTERS; i++) {
                datacenters.add(createDatacenter("Datacenter_" + i));
            }

            // 3. Create Brokers (Dual LB)
            ProposedBroker broker1 = new ProposedBroker("LB1", 1);
            ProposedBroker broker2 = new ProposedBroker("LB2", 2);

            // Connect them for Work Stealing
            broker1.setOtherBroker(broker2);
            broker2.setOtherBroker(broker1);

            // 4. Create VMs and Assign to Brokers
            // LB1 manages Zone 1 (Datacenter 0, VMs 0-17)
            // LB2 manages Zone 2 (Datacenter 1, VMs 18-35)
            List<Vm> vmsList1 = createVMs(broker1.getId(), 0, VMS_PER_DATACENTER);
            List<Vm> vmsList2 = createVMs(broker2.getId(), VMS_PER_DATACENTER, VMS_PER_DATACENTER);

            broker1.submitGuestList(vmsList1);
            broker2.submitGuestList(vmsList2);

            // 5. Create Cloudlets (Tasks)
            List<ProposedCloudlet> cloudletList = createCloudlets(broker1.getId(), broker2.getId());

            // Distribute tasks randomly between brokers (Dispatcher Logic)
            List<ProposedCloudlet> list1 = new ArrayList<>();
            List<ProposedCloudlet> list2 = new ArrayList<>();

            Random rand = new Random();
            for (ProposedCloudlet c : cloudletList) {
                if (rand.nextBoolean()) {
                    c.setUserId(broker1.getId());
                    list1.add(c);
                } else {
                    c.setUserId(broker2.getId());
                    list2.add(c);
                }
            }

            broker1.submitCloudletList(list1);
            broker2.submitCloudletList(list2);

            // 6. Start Simulation
            CloudSim.startSimulation();

            // 7. Stop Simulation
            CloudSim.stopSimulation();

            // 8. Print Results
            List<Cloudlet> newList = broker1.getCloudletReceivedList();
            newList.addAll(broker2.getCloudletReceivedList());

            // Check Quarantine Queues
            List<ProposedCloudlet> quarantine1 = broker1.getQuarantineQueue();
            List<ProposedCloudlet> quarantine2 = broker2.getQuarantineQueue();

            Log.printLine("Simulation Finished!");
            printCloudletList(newList);

            Log.printLine("Tasks in Quarantine (LB1): " + quarantine1.size());
            Log.printLine("Tasks in Quarantine (LB2): " + quarantine2.size());

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();

        // 4 Hosts per Datacenter
        for (int i = 0; i < 4; i++) {
            int mips = 10000; // 10k MIPS per core
            int ram = 16384; // 16GB RAM per host
            long storage = 1000000; // 1TB
            int bw = 10000; // 10Gbps

            List<Pe> peList = new ArrayList<>();
            // 8 Cores per Host
            for (int j = 0; j < 8; j++) {
                peList.add(new Pe(j, new PeProvisionerSimple(mips)));
            }

            hostList.add(
                    new Host(
                            i,
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
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    private static List<Vm> createVMs(int brokerId, int startId, int count) {
        List<Vm> vms = new ArrayList<>();

        // VM Types per your experimental setup:
        // Type 1 (Small): 1 Core, 2GB RAM, 2500 MIPS - 10 VMs
        // Type 2 (Medium): 2 Core, 4GB RAM, 5000 MIPS - 6 VMs
        // Type 3 (Large): 4 Core, 8GB RAM, 10000 MIPS - 2 VMs
        // Total: 18 VMs per Datacenter
        // Total RAM: 10*2 + 6*4 + 2*8 = 20 + 24 + 16 = 60GB (fits in 4 hosts * 16GB =
        // 64GB)

        for (int i = 0; i < count; i++) {
            int mips;
            long size = 10000; // image size (MB)
            int ram;
            long bw = 1000;
            int pesNumber;
            String vmm = "Xen";

            if (i < 10) { // Small VMs (0-9)
                mips = 2500;
                ram = 2048; // 2GB
                pesNumber = 1;
            } else if (i < 16) { // Medium VMs (10-15)
                mips = 5000;
                ram = 4096; // 4GB
                pesNumber = 2;
            } else { // Large VMs (16-17)
                mips = 10000;
                ram = 8192; // 8GB
                pesNumber = 4;
            }

            vms.add(new Vm(startId + i, brokerId, mips, pesNumber, ram, bw, size, vmm,
                    new CloudletSchedulerTimeShared()));
        }
        return vms;
    }

    private static List<ProposedCloudlet> createCloudlets(int brokerId1, int brokerId2) {
        List<ProposedCloudlet> list = new ArrayList<>();
        long length = 25000; // Average length
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        Random rand = new Random();

        for (int i = 0; i < TOTAL_TASKS; i++) {
            // Randomize Task Type
            ProposedCloudlet.CloudletType type;
            int r = rand.nextInt(100);
            if (r < 50)
                type = ProposedCloudlet.CloudletType.TEXT; // 50% Text
            else if (r < 80)
                type = ProposedCloudlet.CloudletType.IMAGE; // 30% Image
            else
                type = ProposedCloudlet.CloudletType.REEL; // 20% Reel

            // Vary length based on type
            long taskLen = length;
            if (type == ProposedCloudlet.CloudletType.TEXT)
                taskLen = 5000;
            if (type == ProposedCloudlet.CloudletType.IMAGE)
                taskLen = 15000;
            if (type == ProposedCloudlet.CloudletType.REEL)
                taskLen = 50000;

            // Add random variation (+/- 10%)
            taskLen = (long) (taskLen * (0.9 + (rand.nextDouble() * 0.2)));

            ProposedCloudlet cloudlet = new ProposedCloudlet(
                    i, taskLen, pesNumber, fileSize, outputSize,
                    utilizationModel, utilizationModel, utilizationModel, type);
            // UserId set later
            list.add(cloudlet);
        }
        return list;
    }

    private static void printCloudletList(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent + "Data Center ID" + indent + "VM ID" + indent + "Time"
                + indent + "Start Time" + indent + "Finish Time");

        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.print("SUCCESS");
                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId()
                        + indent + indent + String.format("%.2f", cloudlet.getActualCPUTime()) + indent + indent
                        + String.format("%.2f", cloudlet.getExecStartTime()) + indent + indent
                        + String.format("%.2f", cloudlet.getExecFinishTime()));
            } else {
                Log.print("FAILED");
                Log.printLine(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId()
                        + indent + indent + String.format("%.2f", cloudlet.getActualCPUTime()) + indent + indent
                        + String.format("%.2f", cloudlet.getExecStartTime()) + indent + indent
                        + String.format("%.2f", cloudlet.getExecFinishTime()));
            }
        }
    }
}
