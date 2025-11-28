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
 */
public class CloudSimExampleProposed {

    private static final int NUM_DATACENTERS = 8;
    private static final int VMS_PER_DATACENTER = 18;
    private static final int TOTAL_TASKS = 2000;

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
            // LB1 manages Zone 1 (Datacenters 0-3, VMs 0-71)
            // LB2 manages Zone 2 (Datacenters 4-7, VMs 72-143)
            List<Vm> vmsList1 = createVMs(broker1.getId(), 0, 4 * VMS_PER_DATACENTER);
            List<Vm> vmsList2 = createVMs(broker2.getId(), 4 * VMS_PER_DATACENTER, 4 * VMS_PER_DATACENTER);

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
            int ram = 16384; // 16GB
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

        // Type 1: 1 Core, 2GB RAM (Small)
        // Type 2: 2 Core, 4GB RAM (Medium)
        // Type 3: 4 Core, 8GB RAM (Large)

        for (int i = 0; i < count; i++) {
            // Mix of VM Types (Type 1, 2, 3)
            // 10 Small (2GB), 6 Medium (4GB), 2 Large (8GB) -> Total 60GB (Fits in 64GB)
            int mips = 2500;
            long size = 10000; // image size (MB)
            int ram = 2048;
            long bw = 1000;
            int pesNumber = 1;
            String vmm = "Xen";

            if (i >= 10 && i < 16) { // Medium (6 VMs)
                mips = 5000;
                ram = 4096;
                pesNumber = 2;
            } else if (i >= 16) { // Large (2 VMs)
                mips = 10000;
                ram = 8192;
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
