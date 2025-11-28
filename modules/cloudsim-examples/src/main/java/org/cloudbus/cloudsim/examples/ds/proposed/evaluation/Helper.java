package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Helper {

    public static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<Host>();

        // 4 Hosts per Datacenter
        for (int i = 0; i < 4; i++) {
            List<Pe> peList = new ArrayList<Pe>();
            int mips = 10000;
            // 8 Core Host
            for (int j = 0; j < 8; j++) {
                peList.add(new Pe(j, new PeProvisionerSimple(mips)));
            }

            int hostId = i;
            int ram = 65536; // 64GB
            long storage = 1000000; // 1TB
            int bw = 100000;

            hostList.add(
                    new Host(
                            hostId,
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
        LinkedList<Storage> storageList = new LinkedList<Storage>();

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

    public static List<Vm> createVmList(int brokerId, int count) {
        List<Vm> vms = new ArrayList<Vm>();
        long size = 10000; // image size (MB)
        int ram = 2048; // vm memory (MB)
        int mips = 1000;
        long bw = 1000;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < count; i++) {
            // Heterogeneous VMs
            if (i % 3 == 0) {
                // High Performance
                mips = 2000;
                ram = 4096;
                pesNumber = 2;
            } else if (i % 3 == 1) {
                // Medium Performance
                mips = 1000;
                ram = 2048;
                pesNumber = 1;
            } else {
                // Low Performance
                mips = 500;
                ram = 1024;
                pesNumber = 1;
            }

            vms.add(new Vm(i, brokerId, mips, pesNumber, ram, bw, size, vmm,
                    new org.cloudbus.cloudsim.CloudletSchedulerTimeShared()));
        }

        return vms;
    }

    public static List<ProposedCloudlet> createCloudletList(int brokerId, int count) {
        List<ProposedCloudlet> list = new ArrayList<ProposedCloudlet>();
        long length = 40000;
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        org.cloudbus.cloudsim.UtilizationModel utilizationModel = new org.cloudbus.cloudsim.UtilizationModelFull();

        for (int i = 0; i < count; i++) {
            ProposedCloudlet.CloudletType type;
            if (i % 3 == 0)
                type = ProposedCloudlet.CloudletType.TEXT;
            else if (i % 3 == 1)
                type = ProposedCloudlet.CloudletType.IMAGE;
            else
                type = ProposedCloudlet.CloudletType.REEL;

            // Adjust length based on type
            switch (type) {
                case TEXT:
                    length = 5000;
                    break;
                case IMAGE:
                    length = 50000;
                    break;
                case REEL:
                    length = 200000;
                    break;
            }

            ProposedCloudlet cloudlet = new ProposedCloudlet(
                    i, length, pesNumber, fileSize, outputSize,
                    utilizationModel, utilizationModel, utilizationModel, type);
            cloudlet.setUserId(brokerId);
            list.add(cloudlet);
        }

        return list;
    }
}
