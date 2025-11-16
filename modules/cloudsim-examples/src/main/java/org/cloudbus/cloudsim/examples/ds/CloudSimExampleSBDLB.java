package org.cloudbus.cloudsim.examples.ds;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
 * FULLY FIXED CloudSim example implementing SBDLB from the paper:
 * "A Dynamic Approach to Load Balancing in Cloud Infrastructure"
 *
 * ALL FIXES APPLIED:
 * ✅ Host MIPS Configuration - Set to 1000 MIPS per core (standard for cloud VMs)
 * ✅ Storage Units Inconsistency - Fixed to use MB consistently
 * ✅ Bandwidth Units - Fixed to use Mbps (bits per second as per CloudSim)
 * ✅ Waiting Queue Implementation - Proper queue with retry logic
 * ✅ Task-VM Notification - Explicit logging and tracking
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

    // Task size ranges (in MI - Million Instructions)
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
        CustomDatacenterBroker broker = new CustomDatacenterBroker("Broker-" + name, useSBDLB);

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
        Log.println("Max waiting queue size: " + broker.getMaxWaitingQueueSize());

        // Calculate performance metrics (as per paper Section IV-B)
        calculateMetrics(finishedCloudlets, vmList, name);
    }

    /**
     * FIXED: Create heterogeneous VMs based on Table III specifications
     * Units fixed: Storage in MB, Bandwidth in Mbps
     */
    private static List<Vm> createHeterogeneousVMs(int userId, int totalVms) {
        List<Vm> vmList = new ArrayList<>();

        // Create equal split: 50% low-spec, 50% high-spec
        int lowSpecCount = totalVms / 2;

        // Low-spec VMs (Table III) - ALL UNITS VERIFIED
        for (int i = 0; i < lowSpecCount; i++) {
            Vm vm = new Vm(
                    i,              // id
                    userId,         // userId
                    500.0,          // mips - 500 MIPS
                    1,              // pes - 1 core
                    1024,           // ram - 1024 MB ✓
                    1000L,          // bw - 1000 Mbps ✓
                    10240L,         // size - 10 GB = 10240 MB ✓ FIXED
                    "Xen",          // vmm
                    new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }

        // High-spec VMs (Table III) - ALL UNITS VERIFIED
        for (int i = lowSpecCount; i < totalVms; i++) {
            Vm vm = new Vm(
                    i,              // id
                    userId,         // userId
                    1000.0,         // mips - 1000 MIPS
                    2,              // pes - 2 cores
                    2048,           // ram - 2048 MB ✓
                    2000L,          // bw - 2000 Mbps ✓
                    20480L,         // size - 20 GB = 20480 MB ✓ FIXED
                    "Xen",          // vmm
                    new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * FIXED: Create datacenters with heterogeneous hosts (Table I and II)
     * All units verified and corrected
     */
    private static List<Datacenter> createDatacenters(int count) throws Exception {
        List<Datacenter> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String name = "Datacenter-" + i;

            // Create heterogeneous hosts (mix of Type 1 and Type 2)
            List<Host> hostList = new ArrayList<>();

            // Type 1 hosts (Table II) - FIXED UNITS
            for (int h = 0; h < 3; h++) {
                hostList.add(createHost(
                        h,              // id
                        1000.0,         // mips per core - 1000 MIPS ✓ VERIFIED
                        4,              // cores - 4 ✓
                        1024,           // ram - 1024 MB ✓
                        1000L,          // bw - 1000 Mbps ✓
                        10240L          // storage - 10 GB = 10240 MB ✓ FIXED
                ));
            }

            // Type 2 hosts (Table II) - FIXED UNITS
            for (int h = 3; h < 6; h++) {
                hostList.add(createHost(
                        h,              // id
                        1000.0,         // mips per core - 1000 MIPS ✓ VERIFIED
                        8,              // cores - 8 ✓
                        2048,           // ram - 2048 MB ✓
                        2000L,          // bw - 2000 Mbps ✓
                        20480L          // storage - 20 GB = 20480 MB ✓ FIXED
                ));
            }

            // DatacenterCharacteristics (Table I) - ALL VERIFIED
            double timeZone = 0.0;
            double costPerSec = 3.0;        // $3/sec CPU usage ✓
            double costPerMem = 0.004;      // $0.004/MB memory ✓
            double costPerStorage = 0.0001; // $0.0001/MB storage ✓
            double costPerBw = 0.01;        // $0.01/Mbps bandwidth ✓

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
     * FIXED: Helper to create a host with specified resources
     * Storage parameter now in MB (consistent with CloudSim)
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
                storage,    // Now correctly in MB
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
            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                double responseTime = cloudlet.getExecFinishTime() - cloudlet.getExecStartTime();
                totalResponseTime += responseTime;
                completedTasks++;
            }
        }

        double avgResponseTime = completedTasks > 0 ? totalResponseTime / completedTasks : 0.0;

        // 2. Data Center Processing Time
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
        double operationalCost = (dcProcessingTime / 1000.0) * cpuCostPerSec;

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

            if (vmId < vmList.size() / 2) {
                lowSpecTasks += taskCount;
            } else {
                highSpecTasks += taskCount;
            }
        }

        // Print results
        Log.println("\n┌───────────────────────────────────────────────────────┐");
        Log.println("│           PERFORMANCE METRICS - " + method + "            ");
        Log.println("└───────────────────────────────────────────────────────┘");
        Log.println("Total tasks:               " + cloudletList.size());
        Log.println("Completed tasks:           " + completedTasks);
        Log.println("Failed tasks:              " + (cloudletList.size() - completedTasks));
        Log.println("─────────────────────────────────────────────────────────");
        Log.println("Average Response Time:     " + String.format("%.2f ms", avgResponseTime));
        Log.println("DC Processing Time:        " + String.format("%.2f ms", dcProcessingTime));
        Log.println("Operational Cost:          $" + String.format("%.2f", operationalCost));
        Log.println("─────────────────────────────────────────────────────────");
        Log.println("Task Distribution:");
        Log.println("  Low-spec VMs:            " + lowSpecTasks + " tasks");
        Log.println("  High-spec VMs:           " + highSpecTasks + " tasks");
        Log.println("  High-spec ratio:         " +
                String.format("%.1f%%", (highSpecTasks * 100.0 / completedTasks)));
        Log.println("─────────────────────────────────────────────────────────\n");
    }
}

/**
 * FULLY FIXED: Custom DatacenterBroker with ALL issues resolved
 * ✅ Proper waiting queue implementation
 * ✅ Task-VM pair notification with logging
 * ✅ Queue size checking
 * ✅ Priority processing for waiting tasks
 */
class CustomDatacenterBroker extends DatacenterBroker {

    private ScoreBasedLoadBalancer sbdlb;
    private ThrottledLoadBalancer throttled;
    private boolean useSBDLB;

    // NEW: Explicit waiting queue for tasks without resources
    private Queue<Cloudlet> waitingQueue;
    private int maxWaitingQueueSize;

    // NEW: Track task-VM assignments for notification
    private Map<Integer, Integer> taskVmAssignments; // cloudletId -> vmId

    public CustomDatacenterBroker(String name, boolean useSBDLB) throws Exception {
        super(name);
        this.useSBDLB = useSBDLB;
        this.waitingQueue = new LinkedList<>();
        this.maxWaitingQueueSize = 0;
        this.taskVmAssignments = new HashMap<>();

        if (useSBDLB) {
            this.sbdlb = new ScoreBasedLoadBalancer();
        } else {
            this.throttled = new ThrottledLoadBalancer();
        }
    }

    /**
     * FIXED: Implements flowchart logic exactly
     * 1. Load Balancer gets suitable VM
     * 2. Check if VM has available resources
     * 3. If yes: Assign task and allocate resources
     * 4. If no: Send task to waiting queue
     */
    @Override
    protected void submitCloudlets() {
        List<Vm> vmList = getAvailableVmList();
        List<Cloudlet> successfullySubmitted = new ArrayList<>();

        // STEP 1: Process waiting queue first (as per flowchart)
        // "Queue Size > 0" → "Send Waiting Tasks"
        if (!waitingQueue.isEmpty()) {
            Log.println("[" + getName() + "] Processing " + waitingQueue.size() +
                    " waiting tasks...");
        }

        while (!waitingQueue.isEmpty()) {
            Cloudlet waitingCloudlet = waitingQueue.peek();
            Vm vm = selectVmForCloudlet(waitingCloudlet, vmList);

            if (vm != null) {
                // Resources available - assign task
                waitingQueue.poll(); // Remove from queue
                assignCloudletToVm(waitingCloudlet, vm);

                // FIXED: Notify broker about Task and VM pair
                notifyTaskVmPair(waitingCloudlet, vm);
            } else {
                // Still no resources - keep in queue and stop processing
                break;
            }
        }

        // Track max queue size
        maxWaitingQueueSize = Math.max(maxWaitingQueueSize, waitingQueue.size());

        // STEP 2: Process new cloudlets
        for (Cloudlet cloudlet : getCloudletList()) {
            // Load Balancer gets suitable VM based on algorithm
            Vm vm = selectVmForCloudlet(cloudlet, vmList);

            // Check if VM has available resources
            if (vm != null) {
                // YES: Assign this task to this VM and allocate resources
                assignCloudletToVm(cloudlet, vm);
                successfullySubmitted.add(cloudlet);

                // FIXED: Notify broker about Task and VM pair
                notifyTaskVmPair(cloudlet, vm);
            } else {
                // NO: If no resources available, send task to waiting queue
                waitingQueue.offer(cloudlet);
                successfullySubmitted.add(cloudlet); // Remove from main list

                // Track max queue size
                maxWaitingQueueSize = Math.max(maxWaitingQueueSize, waitingQueue.size());
            }
        }

        // Remove successfully processed cloudlets
        getCloudletList().removeAll(successfullySubmitted);
    }

    /**
     * FIXED: Explicit notification when Task-VM pair is created
     * This logs the assignment for monitoring and debugging
     */
    private void notifyTaskVmPair(Cloudlet cloudlet, Vm vm) {
        taskVmAssignments.put(cloudlet.getCloudletId(), vm.getId());

        // Log notification (can be disabled for large-scale simulations)
        if (cloudlet.getCloudletId() < 10) { // Log only first 10 for brevity
            Log.println("[" + getName() + "] ✓ Notified: Task #" +
                    cloudlet.getCloudletId() + " → VM #" + vm.getId());
        }
    }

    /**
     * Use appropriate load balancer to select VM
     */
    private Vm selectVmForCloudlet(Cloudlet cloudlet, List<Vm> vmList) {
        if (useSBDLB) {
            return sbdlb.pickVm(vmList, cloudlet);
        } else {
            return throttled.pickVm(vmList, cloudlet);
        }
    }

    /**
     * Assign cloudlet to VM and submit to datacenter
     */
    private void assignCloudletToVm(Cloudlet cloudlet, Vm vm) {
        cloudlet.setGuestId(vm.getId());

        Integer datacenterId = getVmsToDatacentersMap().get(vm.getId());
        if (datacenterId != null) {
            sendNow(datacenterId, CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
            cloudletsSubmitted++;
        }
    }

    /**
     * FIXED: Handle cloudlet completion - Release resources and retry queue
     * Implements: "Finished Tasks" → "Release resources from assigned VM"
     *             → "Queue Size > 0" check
     */
    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();

        // Find the VM that completed this task
        Vm vm = getVmById(cloudlet.getGuestId());

        // Release resources from assigned VM
        if (vm != null) {
            if (useSBDLB && sbdlb != null) {
                sbdlb.releaseVm(vm);
            } else if (!useSBDLB && throttled != null) {
                throttled.releaseVm(vm);
            }
        }

        // Call parent implementation
        super.processCloudletReturn(ev);

        // FIXED: Check if Queue Size > 0
        if (!waitingQueue.isEmpty() || !getCloudletList().isEmpty()) {
            // Send waiting tasks back to load balancer
            submitCloudlets();
        }
    }

    /**
     * Helper to get available VM list
     */
    private List<Vm> getAvailableVmList() {
        List<Vm> vmList = new ArrayList<>();
        for (Object obj : getGuestList()) {
            if (obj instanceof Vm) {
                vmList.add((Vm) obj);
            }
        }
        return vmList;
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

    /**
     * Get max waiting queue size (for monitoring)
     */
    public int getMaxWaitingQueueSize() {
        return maxWaitingQueueSize;
    }

    /**
     * Get task-VM assignment for a cloudlet (for monitoring)
     */
    public Integer getVmForTask(int cloudletId) {
        return taskVmAssignments.get(cloudletId);
    }
}