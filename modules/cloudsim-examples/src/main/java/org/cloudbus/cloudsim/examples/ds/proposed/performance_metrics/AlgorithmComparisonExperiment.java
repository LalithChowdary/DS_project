package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * Main Experiment Class to Compare Load Balancing Algorithms.
 * 1. Round Robin
 * 2. Weighted Round Robin
 * 3. Honey Bee Foraging
 * 4. Proposed Dual-LB Architecture
 */
public class AlgorithmComparisonExperiment {

    private static final String[] ALGORITHMS = {
            "Round Robin",
            "Weighted Round Robin",
            "Honey Bee Foraging",
            "SBDLB",
            "Proposed Dual-LB"
    };
    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmList;
    private static int numCloudlets = 100;
    private static int numVms = 5;

    public static void main(String[] args) {
        Log.printLine("Starting Algorithm Comparison Experiment...");

        ComparisonResults results = new ComparisonResults();

        // 1. Run Round Robin
        runExperiment("Round Robin", new BrokerFactory() {
            @Override
            public DatacenterBroker create(String name) throws Exception {
                return new RoundRobinBroker(name);
            }
        }, results);

        // 2. Run Weighted Round Robin
        runExperiment("Weighted Round Robin", new BrokerFactory() {
            @Override
            public DatacenterBroker create(String name) throws Exception {
                return new WeightedRoundRobinBroker(name);
            }
        }, results);

        // 3. Run Honey Bee Foraging
        runExperiment("Honey Bee Foraging", new BrokerFactory() {
            @Override
            public DatacenterBroker create(String name) throws Exception {
                return new HoneyBeeBroker(name);
            }
        }, results);

        // 4. Run SBDLB
        runExperiment("SBDLB", new BrokerFactory() {
            @Override
            public DatacenterBroker create(String name) throws Exception {
                return new SBDLBBroker(name);
            }
        }, results);

        // 5. Run Proposed Algorithm
        runExperiment("Proposed Dual-LB", new BrokerFactory() {
            @Override
            public DatacenterBroker create(String name) throws Exception {
                // ProposedBroker requires lbId
                return new ProposedBroker(name, 1);
            }
        }, results);

        // Print Final Comparison
        results.printComparison();
    }

    private interface BrokerFactory {
        DatacenterBroker create(String name) throws Exception;
    }

    private static void runExperiment(String algoName, BrokerFactory factory, ComparisonResults results) {
        Log.printLine("--------------------------------------------------");
        Log.printLine("Running Experiment: " + algoName);
        Log.printLine("--------------------------------------------------");

        try {
            // Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // Create Datacenter
            Datacenter datacenter0 = createDatacenter("Datacenter_0");

            // Create Broker
            DatacenterBroker broker = factory.create("Broker");
            int brokerId = broker.getId();

            // Create VMs
            vmList = createVms(brokerId);
            broker.submitGuestList(vmList);

            // Create Cloudlets
            cloudletList = createCloudlets(brokerId);
            broker.submitCloudletList(cloudletList);

            // Start Simulation
            CloudSim.startSimulation();

            // Stop Simulation
            CloudSim.stopSimulation();

            // Collect Results
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            results.addResult(algoName, newList);

            Log.printLine(algoName + " finished!");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<Host>();

        List<Pe> peList = new ArrayList<Pe>();
        int mips = 10000; // High MIPS for host
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList.add(new Pe(1, new PeProvisionerSimple(mips)));
        peList.add(new Pe(2, new PeProvisionerSimple(mips)));
        peList.add(new Pe(3, new PeProvisionerSimple(mips)));

        int hostId = 0;
        int ram = 204800; // host memory (MB)
        long storage = 1000000; // host storage
        int bw = 100000;

        hostList.add(
                new Host(
                        hostId,
                        new RamProvisionerSimple(ram),
                        new BwProvisionerSimple(bw),
                        storage,
                        peList,
                        new VmSchedulerTimeShared(peList)));

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
                arch, os, vmm, hostList, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    private static List<Vm> createVms(int brokerId) {
        List<Vm> vms = new ArrayList<Vm>();

        // Create VMs with different capacities to test Weighted RR and Load Balancing
        int mips[] = { 1000, 2000, 500, 1000, 2500 }; // Different MIPS
        long size = 10000; // image size (MB)
        int ram = 512; // vm memory (MB)
        long bw = 1000;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < numVms; i++) {
            vms.add(new Vm(i, brokerId, mips[i % mips.length], pesNumber, ram, bw, size, vmm,
                    new CloudletSchedulerTimeShared()));
        }

        return vms;
    }

    private static List<Cloudlet> createCloudlets(int brokerId) {
        List<Cloudlet> list = new ArrayList<Cloudlet>();

        long length = 40000; // Cloudlet length
        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < numCloudlets; i++) {
            // Use ProposedCloudlet if possible, or standard Cloudlet
            // ProposedBroker expects ProposedCloudlet
            ProposedCloudlet cloudlet = new ProposedCloudlet(
                    i,
                    length + (long) (Math.random() * 10000), // Randomize length slightly
                    pesNumber,
                    fileSize,
                    outputSize,
                    utilizationModel,
                    utilizationModel,
                    utilizationModel,
                    ProposedCloudlet.CloudletType.TEXT // Default type
            );

            // Randomly assign types for ProposedBroker logic
            double rand = Math.random();
            if (rand < 0.33)
                cloudlet.setType(ProposedCloudlet.CloudletType.TEXT);
            else if (rand < 0.66)
                cloudlet.setType(ProposedCloudlet.CloudletType.IMAGE);
            else
                cloudlet.setType(ProposedCloudlet.CloudletType.REEL);

            cloudlet.setUserId(brokerId);
            list.add(cloudlet);
        }

        return list;
    }
}
