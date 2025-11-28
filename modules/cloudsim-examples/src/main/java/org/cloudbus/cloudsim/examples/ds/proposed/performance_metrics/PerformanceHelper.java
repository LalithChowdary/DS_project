package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * Helper class for performance experiments
 * Shared infrastructure creation logic
 */
public class PerformanceHelper {

    public static Datacenter createDatacenter(String name, int index) {
        List<Host> hostList = new ArrayList<>();

        // Host Configuration: 4 Hosts per Datacenter
        int hostCount = 4;
        int coresPerHost = 8;
        int mipsPerCore = 10000;
        int ram = 16384; // 16 GB
        long storage = 1000000; // 1 TB
        int bw = 10000; // 10 Gbps

        for (int i = 0; i < hostCount; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < coresPerHost; j++) {
                peList.add(new Pe(j, new PeProvisionerSimple(mipsPerCore)));
            }

            hostList.add(
                    new Host(
                            i,
                            new RamProvisionerSimple(ram),
                            new BwProvisionerSimple(bw),
                            storage,
                            peList,
                            new VmSchedulerSpaceShared(peList)));
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

    public static List<Vm> createVms(int brokerId) {
        return createVmsForDCs(brokerId, 4);
    }

    public static List<Vm> createVmsForDCs(int brokerId, int dcCount) {
        List<Vm> vms = new ArrayList<>();
        int vmId = 0;

        // Create 18 VMs per Datacenter
        for (int dc = 0; dc < dcCount; dc++) {
            // Type 3 (Large): 2 VMs, 4 Cores, 10000 MIPS, 8GB RAM
            for (int k = 0; k < 2; k++) {
                vms.add(createVm(vmId++, brokerId, 3));
            }

            // Type 2 (Medium): 6 VMs, 2 Cores, 5000 MIPS, 4GB RAM
            for (int k = 0; k < 6; k++) {
                vms.add(createVm(vmId++, brokerId, 2));
            }

            // Type 1 (Small): 10 VMs, 1 Core, 2500 MIPS, 2GB RAM
            for (int k = 0; k < 10; k++) {
                vms.add(createVm(vmId++, brokerId, 1));
            }
        }

        return vms;
    }

    private static Vm createVm(int id, int brokerId, int type) {
        int mips = 0;
        int pesNumber = 0;
        int ram = 0;
        int bw = 1000; // 1 Gbps

        switch (type) {
            case 1: // Small
                mips = 2500;
                pesNumber = 1;
                ram = 2048; // 2 GB
                break;
            case 2: // Medium
                mips = 5000;
                pesNumber = 2;
                ram = 4096; // 4 GB
                break;
            case 3: // Large
                mips = 10000;
                pesNumber = 4;
                ram = 8192; // 8 GB
                break;
        }

        return new Vm(id, brokerId, mips, pesNumber, ram, bw, 10000, "Xen", new CloudletSchedulerTimeShared());
    }

    public static List<ProposedCloudlet> createCloudlets(int brokerId, String algorithmName) {
        List<ProposedCloudlet> list = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelFull();

        // Total 2000 Tasks
        int totalTasks = 2000;
        int reelsCount = (int) (totalTasks * 0.60); // 1200
        int imagesCount = (int) (totalTasks * 0.30); // 600
        int textCount = totalTasks - reelsCount - imagesCount; // 200

        Random random = new Random(42);
        int cloudletId = 0;

        // Create Reels Tasks (60%)
        for (int i = 0; i < reelsCount; i++) {
            long length = 50000000L + (long) (random.nextDouble() * (5000000000L - 50000000L));
            list.add(createCloudlet(cloudletId++, length, brokerId, utilizationModel));
        }

        // Create Images Tasks (30%)
        for (int i = 0; i < imagesCount; i++) {
            long length = 2500000L + (long) (random.nextDouble() * (150000000L - 2500000L));
            list.add(createCloudlet(cloudletId++, length, brokerId, utilizationModel));
        }

        // Create Text Tasks (10%)
        for (int i = 0; i < textCount; i++) {
            long length = 5000L + (long) (random.nextDouble() * (50000L - 5000L));
            list.add(createCloudlet(cloudletId++, length, brokerId, utilizationModel));
        }

        // Shuffle the list
        java.util.Collections.shuffle(list, random);

        return list;
    }

    private static ProposedCloudlet createCloudlet(int id, long length, int brokerId,
            UtilizationModel utilizationModel) {
        int pesNumber = 1;
        if (length >= 50000000L) {
            pesNumber = 2; // Reels use 2 Cores
        }
        long fileSize = 300;
        long outputSize = 300;

        // Assign type based on length
        ProposedCloudlet.CloudletType type;
        if (length >= 50000000L)
            type = ProposedCloudlet.CloudletType.REEL;
        else if (length >= 2500000L)
            type = ProposedCloudlet.CloudletType.IMAGE;
        else
            type = ProposedCloudlet.CloudletType.TEXT;

        ProposedCloudlet cloudlet = new ProposedCloudlet(id, length, pesNumber, fileSize, outputSize,
                utilizationModel, utilizationModel, utilizationModel, type);
        cloudlet.setUserId(brokerId);
        return cloudlet;
    }
}
