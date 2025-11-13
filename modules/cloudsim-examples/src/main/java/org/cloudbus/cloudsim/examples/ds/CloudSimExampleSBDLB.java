package org.cloudbus.cloudsim.examples.ds;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerSpaceShared;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

/**
 * Enhanced CloudSim example implementing SBDLB from the paper:
 * "A Dynamic Approach to Load Balancing in Cloud Infrastructure"
 *
 * CRITICAL FIXES:
 * - Custom broker that integrates SBDLB properly
 * - Dynamic resource calculation (available, not total)
 * - Resource release on task completion
 * - Correct task length units (MI)
 *
 * This example creates:
 * - 8 datacenters (scaled from Meta's 24 data centers by factor of 3)
 * - Heterogeneous VMs (low-spec and high-spec as per Table III)
 * - Heterogeneous hosts (Type 1 and Type 2 as per Table II)
 * - Three types of cloudlets: Reels (60%), Images (30%), Text (10%)
 * - Comparison between SBDLB and Throttled load balancing
 */
public class CloudSimExampleSBDLB {

    // Task type distribution (from paper Section V-A)
    private static final double REEL_PERCENTAGE = 0.60;
    private static final double IMAGE_PERCENTAGE = 0.30;
    private static final double TEXT_PERCENTAGE = 0.10;

    // Task size ranges (in MI - Million Instructions) - FIXED UNITS
    private static final long REEL_MIN_MI = 10000000L; // 10 MB * 1000 CI
    private static final long REEL_MAX_MI = 1000000000L; // 1 GB * 1000 CI
    private static final long IMAGE_MIN_MI = 500000L; // 1 MB * 500 CI
    private static final long IMAGE_MAX_MI = 30000000L; // 30 MB * 1000 CI
    private static final long TEXT_MIN_MI = 1000L; // 10 KB * 100 CI
    private static final long TEXT_MAX_MI = 10000L; // 100 KB * 100 CI

    private static Random random = new Random(42); // Fixed seed for reproducibility

    public static void main(String[] args) throws Exception {
        Log.println("=============================================================");
        Log.println("Starting CloudSimExampleSBDLB with SBDLB algorithm...");
        Log.println("=============================================================\n");

        // Initialize CloudSim
        int numUsers = 1;
        Calendar calendar = Calendar.getInstance();
        boolean traceFlag = false;
        CloudSim.init(numUsers, calendar, traceFlag);

        // Create 8 datacenters (as per paper - scaled from Meta's 24)
        List<Datacenter> datacenters = createDatacenters(8);
        Log.println("✓ Created " + datacenters.size() + " datacenters");

        // ========== SBDLB SIMULATION ==========
        Log.println("\n--- Running SBDLB Simulation ---");
        runSimulation("SBDLB", true, 60, 8000);

        // ========== THROTTLED SIMULATION ==========
        Log.println("\n--- Running Throttled Simulation ---");
        // Reset CloudSim for new simulation
        CloudSim.init(numUsers, calendar, traceFlag);
        datacenters = createDatacenters(8);
        runSimulation("Throttled", false, 60, 8000);

        Log.println("\n=============================================================");
        Log.println("Finished CloudSimExampleSBDLB simulation!");
        Log.println("=============================================================");
    }

    /**
     * Run a complete simulation with specified load balancer
     */
    private static void runSimulation(String name, boolean useSBDLB, int vmsPerDC, int taskCount)
            throws Exception {

        // Create custom broker with chosen load balancer
        CustomDatacenterBroker broker;
        if (useSBDLB) {
            broker = new CustomDatacenterBroker("Broker-SBDLB", useSBDLB);
        } else {
            broker = new CustomDatacenterBroker("Broker-Throttled", useSBDLB);
        }

        // Create heterogeneous VMs (60 VMs as per paper Scenario 2)
        List<Vm> vmList = createHeterogeneousVMs(broker.getId(), vmsPerDC);
        broker.submitGuestList(vmList);
        Log.println("✓ Created " + vmList.size() + " heterogeneous VMs");

        // Create cloudlets with realistic task distribution
        List<Cloudlet> cloudletList = createRealisticCloudlets(broker.getId(), taskCount);
        broker.submitCloudletList(cloudletList);
        Log.println("✓ Created " + cloudletList.size() + " cloudlets");
        Log.println("  - Reels (60%): " + (int) (taskCount * REEL_PERCENTAGE));
        Log.println("  - Images (30%): " + (int) (taskCount * IMAGE_PERCENTAGE));
        Log.println("  - Text (10%): " + (int) (taskCount * TEXT_PERCENTAGE));

        // Start simulation
        Log.println("\n⏳ Starting simulation...");
        double startTime = System.currentTimeMillis();
        CloudSim.startSimulation();
        double endTime = System.currentTimeMillis();

        // Get results
        List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
        CloudSim.stopSimulation();

        // Print results
        Log.println("\n========== " + name + " SIMULATION RESULTS ==========");
        Log.println("Wall-clock simulation time: " + (endTime - startTime) / 1000.0 + " seconds");

        // Calculate performance metrics (as per paper Section IV-B)
        calculateMetrics(finishedCloudlets, vmList, name);
    }

    /**
     * Create heterogeneous VMs based on Table III specifications
     */
    private static List<Vm> createHeterogeneousVMs(int userId, int totalVms) {
        List<Vm> vmList = new ArrayList<>();

        // Create equal split: 50% low-spec, 50% high-spec
        int lowSpecCount = totalVms / 2;
        @SuppressWarnings("unused")
        int highSpecCount = totalVms - lowSpecCount;

        // Low-spec VMs (Table III)
        for (int i = 0; i < lowSpecCount; i++) {
            Vm vm = new Vm(
                    i, // id
                    userId, // userId
                    500.0, // mips - 500
                    1, // pes - 1 core
                    1024, // ram - 1024 MB
                    1000L, // bw - 1000 MB/s
                    10000L, // size - 10 GB
                    "Xen", // vmm
                    new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }

        // High-spec VMs (Table III)
        for (int i = lowSpecCount; i < totalVms; i++) {
            Vm vm = new Vm(
                    i, // id
                    userId, // userId
                    1000.0, // mips - 1000
                    2, // pes - 2 cores
                    2048, // ram - 2048 MB
                    2000L, // bw - 2000 MB/s
                    20000L, // size - 20 GB
                    "Xen", // vmm
                    new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * Create datacenters with heterogeneous hosts (Table I and II)
     */
    private static List<Datacenter> createDatacenters(int count) throws Exception {
        List<Datacenter> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String name = "Datacenter-" + i;

            // Create heterogeneous hosts (mix of Type 1 and Type 2)
            List<Host> hostList = new ArrayList<>();

            // Create multiple hosts per datacenter for better distribution
            // Type 1 hosts (Table II)
            for (int h = 0; h < 3; h++) {
                hostList.add(createHost(h, 1000.0, 4, 1024, 1000L, 10000L));
            }

            // Type 2 hosts (Table II)
            for (int h = 3; h < 6; h++) {
                hostList.add(createHost(h, 1000.0, 8, 2048, 2000L, 20000L));
            }

            // DatacenterCharacteristics (Table I)
            double timeZone = 0.0;
            double costPerSec = 3.0; // $3/sec CPU usage
            double costPerMem = 0.004; // $0.004/MB memory
            double costPerStorage = 0.0001; // $0.0001/MB storage
            double costPerBw = 0.01; // $0.01/Mbps bandwidth

            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                    "x86", "Linux", "Xen", hostList, timeZone,
                    costPerSec, costPerMem, costPerStorage, costPerBw);

            VmAllocationPolicySimple vmPolicy = new VmAllocationPolicySimple(hostList);
            List<Storage> storageList = new ArrayList<>();
            double schedulingInterval = 0.5;

            Datacenter dc = new Datacenter(name, characteristics, vmPolicy,
                    storageList, schedulingInterval);
            list.add(dc);
        }

        return list;
    }

    /**
     * Helper to create a host with specified resources
     */
    private static Host createHost(int id, double mips, int cores,
            int ram, long bw, long storage) {
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < cores; i++) {
            peList.add(new Pe(i, new PeProvisionerSimple(mips)));
        }

        return new Host(
                id,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerSpaceShared(peList));
    }

    /**
     * Create cloudlets with realistic task distribution:
     * 60% Reels, 30% Images, 10% Text (from paper Section V-A)
     */
    private static List<Cloudlet> createRealisticCloudlets(int userId, int totalTasks) {
        List<Cloudlet> cloudletList = new ArrayList<>();

        int reelCount = (int) (totalTasks * REEL_PERCENTAGE);
        int imageCount = (int) (totalTasks * IMAGE_PERCENTAGE);
        @SuppressWarnings("unused")
        int textCount = totalTasks - reelCount - imageCount;

        // Create Reels (60%)
        for (int i = 0; i < reelCount; i++) {
            long length = randomInRange(REEL_MIN_MI, REEL_MAX_MI);
            cloudletList.add(createCloudlet(i, userId, length, 2));
        }

        // Create Images (30%)
        for (int i = reelCount; i < reelCount + imageCount; i++) {
            long length = randomInRange(IMAGE_MIN_MI, IMAGE_MAX_MI);
            cloudletList.add(createCloudlet(i, userId, length, 1));
        }

        // Create Text (10%)
        for (int i = reelCount + imageCount; i < totalTasks; i++) {
            long length = randomInRange(TEXT_MIN_MI, TEXT_MAX_MI);
            cloudletList.add(createCloudlet(i, userId, length, 1));
        }

        return cloudletList;
    }

    /**
     * Create a single cloudlet
     */
    private static Cloudlet createCloudlet(int id, int userId, long length, int pesNumber) {
        long fileSize = 300L;
        long outputSize = 300L;

        UtilizationModelFull umCpu = new UtilizationModelFull();
        UtilizationModelFull umRam = new UtilizationModelFull();
        UtilizationModelFull umBw = new UtilizationModelFull();

        Cloudlet cloudlet = new Cloudlet(id, length, pesNumber, fileSize, outputSize,
                umCpu, umRam, umBw);
        cloudlet.setUserId(userId);

        return cloudlet;
    }

    /**
     * Generate random value in range [min, max]
     */
    private static long randomInRange(long min, long max) {
        return min + (long) (random.nextDouble() * (max - min));
    }

    /**
     * Calculate and display performance metrics (Section IV-B)
     */
    private static void calculateMetrics(List<Cloudlet> cloudletList, List<Vm> vmList, String method) {
        // 1. Average Response Time
        double totalResponseTime = 0.0;
        int completedTasks = 0;

        for (Cloudlet cloudlet : cloudletList) {
            // Check cloudlet status - use getStatus() and compare with constants
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                double responseTime = cloudlet.getExecFinishTime() - cloudlet.getExecStartTime();
                totalResponseTime += responseTime;
                completedTasks++;
            }
        }

        double avgResponseTime = completedTasks > 0 ? totalResponseTime / completedTasks : 0.0;

        // 2. Data Center Processing Time (simplified - total simulation time)
        double minStartTime = Double.MAX_VALUE;
        double maxFinishTime = 0.0;

        for (Cloudlet cloudlet : cloudletList) {
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                minStartTime = Math.min(minStartTime, cloudlet.getExecStartTime());
                maxFinishTime = Math.max(maxFinishTime, cloudlet.getExecFinishTime());
            }
        }

        double dcProcessingTime = maxFinishTime - minStartTime;

        // 3. Operational Cost (CPU cost * processing time)
        double cpuCostPerSec = 3.0; // From Table I
        double operationalCost = (dcProcessingTime / 1000.0) * cpuCostPerSec; // Convert ms to sec

        // 4. Task distribution by VM type
        Map<Integer, Integer> vmTaskDistribution = new HashMap<>();
        for (Cloudlet cloudlet : cloudletList) {
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                int vmId = cloudlet.getGuestId();
                vmTaskDistribution.put(vmId, vmTaskDistribution.getOrDefault(vmId, 0) + 1);
            }
        }

        // Count tasks on high-spec vs low-spec VMs
        int lowSpecTasks = 0;
        int highSpecTasks = 0;
        for (Map.Entry<Integer, Integer> entry : vmTaskDistribution.entrySet()) {
            int vmId = entry.getKey();
            int taskCount = entry.getValue();

            // VMs 0 to 29 are low-spec, 30-59 are high-spec (based on creation order)
            if (vmId < vmList.size() / 2) {
                lowSpecTasks += taskCount;
            } else {
                highSpecTasks += taskCount;
            }
        }

        // Print results
        Log.println("\n┌─────────────────────────────────────────────────────────┐");
        Log.println("│           PERFORMANCE METRICS - " + method + "            ");
        Log.println("└─────────────────────────────────────────────────────────┘");
        Log.println("Total tasks:               " + cloudletList.size());
        Log.println("Completed tasks:           " + completedTasks);
        Log.println("Failed tasks:              " + (cloudletList.size() - completedTasks));
        Log.println("───────────────────────────────────────────────────────────");
        Log.println("Average Response Time:     " + String.format("%.2f ms", avgResponseTime));
        Log.println("DC Processing Time:        " + String.format("%.2f ms", dcProcessingTime));
        Log.println("Operational Cost:          $" + String.format("%.2f", operationalCost));
        Log.println("───────────────────────────────────────────────────────────");
        Log.println("Task Distribution:");
        Log.println("  Low-spec VMs:            " + lowSpecTasks + " tasks");
        Log.println("  High-spec VMs:           " + highSpecTasks + " tasks");
        Log.println("  High-spec ratio:         " +
                String.format("%.1f%%", (highSpecTasks * 100.0 / completedTasks)));
        Log.println("───────────────────────────────────────────────────────────\n");
    }
}

/**
 * CRITICAL FIX: Custom DatacenterBroker that integrates SBDLB or Throttled
 * This is the missing piece that makes load balancing actually work!
 */
class CustomDatacenterBroker extends DatacenterBroker {

    private ScoreBasedLoadBalancer sbdlb;
    private ThrottledLoadBalancer throttled;
    private boolean useSBDLB;

    public CustomDatacenterBroker(String name, boolean useSBDLB) throws Exception {
        super(name);
        this.useSBDLB = useSBDLB;
        if (useSBDLB) {
            this.sbdlb = new ScoreBasedLoadBalancer();
        } else {
            this.throttled = new ThrottledLoadBalancer();
        }
    }

    /**
     * Override VM selection to use our custom load balancer
     */
    @Override
    protected void submitCloudlets() {
        List<Cloudlet> successfullySubmitted = new ArrayList<>();

        for (Cloudlet cloudlet : getCloudletList()) {
            Vm vm;

            // Use appropriate load balancer - need to get VM list properly
            List<Vm> vmList = new ArrayList<>();
            for (Object obj : getGuestList()) {
                if (obj instanceof Vm) {
                    vmList.add((Vm) obj);
                }
            }

            // Use appropriate load balancer
            if (useSBDLB) {
                vm = sbdlb.pickVm(vmList, cloudlet);
            } else {
                vm = throttled.pickVm(vmList, cloudlet);
            }

            if (vm != null) {
                cloudlet.setGuestId(vm.getId());
                // Check if VM is actually created in a datacenter
                Integer datacenterId = getVmsToDatacentersMap().get(vm.getId());
                if (datacenterId != null) {
                    sendNow(datacenterId, CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
                    cloudletsSubmitted++;
                    successfullySubmitted.add(cloudlet);
                }
            }
        }

        // Remove successfully submitted cloudlets
        getCloudletList().removeAll(successfullySubmitted);
    }

    /**
     * Handle cloudlet completion - CRITICAL for dynamic load balancing
     */
    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();

        // Find the VM that completed this task
        Vm vm = getVmById(cloudlet.getGuestId());

        // Release VM slot in load balancer (only if VM exists)
        if (vm != null) {
            if (useSBDLB && sbdlb != null) {
                sbdlb.releaseVm(vm);
            } else if (!useSBDLB && throttled != null) {
                throttled.releaseVm(vm);
            }
        }

        // Call parent implementation
        super.processCloudletReturn(ev);

        // Try to submit waiting cloudlets
        submitCloudlets();
    }

    /**
     * Helper to find VM by ID
     */
    private Vm getVmById(int vmId) {
        for (Object obj : getGuestList()) {
            if (obj instanceof Vm) {
                Vm vm = (Vm) obj;
                if (vm.getId() == vmId) {
                    return vm;
                }
            }
        }
        return null;
    }
}