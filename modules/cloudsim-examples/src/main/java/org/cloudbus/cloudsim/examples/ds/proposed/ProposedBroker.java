package org.cloudbus.cloudsim.examples.ds.proposed;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.core.GuestEntity;

import java.util.*;

/**
 * Represents a Load Balancer (LB) in the proposed Dual-LB architecture.
 * Implements MLFQ (Multi-Level Feedback Queue), Aging, SBDLB Scoring, and Work
 * Stealing.
 */
public class ProposedBroker extends DatacenterBroker {

    private int lbId; // 1 or 2
    private String region; // "A" for LB1, "B" for LB2

    // MLFQ Queues
    private List<ProposedCloudlet> highPriorityQueue; // Text
    private List<ProposedCloudlet> mediumPriorityQueue; // Image
    private List<ProposedCloudlet> lowPriorityQueue; // Reel

    // Overflow Queue (Backpressure)
    private List<ProposedCloudlet> overflowQueue;

    // Quarantine Queue (Poison Tasks)
    private List<ProposedCloudlet> quarantineQueue;

    // Redis Connection
    private RedisMock redis;

    // Other Broker for Work Stealing
    private ProposedBroker otherBroker;

    // SBDLB Constants & State
    private static final int TASK_THRESHOLD = 3;
    private static final double AGING_THRESHOLD = 5.0; // 5 seconds
    private Map<Integer, Integer> vmTaskCount;
    private Set<Integer> registeredVms = new HashSet<>(); // Track registered VMs to avoid redundant Redis calls
    private boolean simulationFinished = false;

    // Task type ranges (MI)
    private static final long REEL_MIN_MI = 10000000L;
    private static final long REEL_MAX_MI = 1000000000L;
    private static final long IMAGE_MIN_MI = 500000L;
    private static final long IMAGE_MAX_MI = 30000000L;
    private static final long TEXT_MIN_MI = 1000L;
    private static final long TEXT_MAX_MI = 10000L;

    private double failureTime = -1;

    public ProposedBroker(String name, int lbId) throws Exception {
        super(name);
        this.lbId = lbId;
        this.region = (lbId == 1) ? "A" : "B";

        this.highPriorityQueue = new ArrayList<>();
        this.mediumPriorityQueue = new ArrayList<>();
        this.lowPriorityQueue = new ArrayList<>();
        this.overflowQueue = new ArrayList<>();
        this.quarantineQueue = new ArrayList<>();
        this.vmTaskCount = new HashMap<>();
        this.heartbeatStartedVms = new HashSet<>();

        this.redis = RedisMock.getInstance();
    }

    public int getLbId() {
        return lbId;
    }

    public void setOtherBroker(ProposedBroker otherBroker) {
        this.otherBroker = otherBroker;
    }

    public List<ProposedCloudlet> getOverflowQueue() {
        return overflowQueue;
    }

    public void setFailureTime(double time) {
        this.failureTime = time;
    }

    @Override
    protected void submitCloudlets() {
        List<Cloudlet> list = getCloudletList();
        for (Cloudlet c : list) {
            if (c instanceof ProposedCloudlet) {
                ProposedCloudlet pc = (ProposedCloudlet) c;
                // Set submission time if not set (for aging)
                if (pc.getSubmissionTime() == 0) {
                    pc.setSubmissionTime(CloudSim.clock());
                }
                enqueueTask(pc);
            } else {
                Log.printLine(getName() + ": Error - Cloudlet is not of type ProposedCloudlet");
            }
        }
        getCloudletList().clear();
        scheduleTasks();
    }

    private void enqueueTask(ProposedCloudlet cloudlet) {
        // Register in Level 1 Cache
        redis.hset(1, "Global", "Task_" + cloudlet.getCloudletId(), "status", "QUEUED");
        redis.hset(1, "Global", "Task_" + cloudlet.getCloudletId(), "assigned_lb", "LB" + lbId);

        // Add to appropriate queue
        switch (cloudlet.getType()) {
            case TEXT:
                highPriorityQueue.add(cloudlet);
                break;
            case IMAGE:
                mediumPriorityQueue.add(cloudlet);
                break;
            case REEL:
                lowPriorityQueue.add(cloudlet);
                break;
        }
    }

    private void scheduleTasks() {
        // 1. Aging Check
        processAging();

        // 2. Work Stealing (if idle)
        if (highPriorityQueue.isEmpty() && mediumPriorityQueue.isEmpty() && lowPriorityQueue.isEmpty()) {
            stealWork();
        }

        // 3. Process Queues in Priority Order
        processQueue(highPriorityQueue);
        processQueue(mediumPriorityQueue);
        processQueue(lowPriorityQueue);
    }

    private void processAging() {
        double currentTime = CloudSim.clock();
        Iterator<ProposedCloudlet> it = lowPriorityQueue.iterator();
        while (it.hasNext()) {
            ProposedCloudlet c = it.next();
            if ((currentTime - c.getSubmissionTime()) > AGING_THRESHOLD) {
                it.remove();
                c.setType(ProposedCloudlet.CloudletType.IMAGE); // Upgrade to Medium
                mediumPriorityQueue.add(c);
                Log.printLine(getName() + ": Task " + c.getCloudletId() + " aged from Low to Medium Priority.");
            }
        }
    }

    private void stealWork() {
        if (otherBroker == null)
            return;

        List<ProposedCloudlet> stolen = new ArrayList<>();
        int limit = 5;

        // 1. Try Overflow Queue
        stealFromQueue(otherBroker.getOverflowQueue(), stolen, limit);

        // 2. Try Low Priority Queue (if we still need tasks)
        if (stolen.size() < limit) {
            stealFromQueue(otherBroker.getLowPriorityQueue(), stolen, limit - stolen.size());
        }

        // 3. Try Medium Priority Queue
        if (stolen.size() < limit) {
            stealFromQueue(otherBroker.getMediumPriorityQueue(), stolen, limit - stolen.size());
        }

        if (!stolen.isEmpty()) {
            Log.printLine(getName() + ": Stole " + stolen.size() + " tasks from " + otherBroker.getName());
            for (ProposedCloudlet c : stolen) {
                enqueueTask(c);
            }
        }
    }

    private void stealFromQueue(List<ProposedCloudlet> source, List<ProposedCloudlet> dest, int limit) {
        Iterator<ProposedCloudlet> it = source.iterator();
        int count = 0;
        while (it.hasNext() && count < limit) {
            ProposedCloudlet c = it.next();
            // Only steal if task is waiting (not running) - In this sim, queue holds
            // waiting tasks
            dest.add(c);
            it.remove();
            count++;
        }
    }

    private void processQueue(List<ProposedCloudlet> queue) {
        if (queue.isEmpty())
            return;

        List<ProposedCloudlet> toRemove = new ArrayList<>();

        for (ProposedCloudlet cloudlet : queue) {
            Vm bestVm = findBestVm(cloudlet);

            if (bestVm != null) {
                submitTaskToVm(cloudlet, bestVm);
                toRemove.add(cloudlet);
            } else {
                // If no VM found, check if we should move to Overflow
                // For now, we just keep it in the queue (Head-of-Line blocking simulation)
                // Or we could skip to next task, but MLFQ usually blocks.
                // Let's try to move to Overflow if queue is too big?
                // For simplicity, we leave it here to retry next tick.
            }
        }

        queue.removeAll(toRemove);
    }

    private Vm findBestVm(ProposedCloudlet cloudlet) {
        Vm bestVm = null;
        double bestScore = -1.0;

        List<Vm> vmList = getGuestsCreatedList();
        for (Vm vm : vmList) {
            // 1. Pre-Filter: Task Threshold
            int currentTasks = vmTaskCount.getOrDefault(vm.getId(), 0);
            if (currentTasks >= TASK_THRESHOLD) {
                // Log.printLine("DEBUG: VM " + vm.getId() + " skipped (Tasks: " + currentTasks
                // + ")");
                continue;
            }

            // 2. Retrieve Available Resources
            double availMips = getAvailableMips(vm);
            double availRam = getAvailableRam(vm);
            double availBw = getAvailableBw(vm);

            // 3. Normalization (Eq 1) & Resource Requirement Calculation
            long taskLen = cloudlet.getCloudletLength();
            // Normalize task length to [0, 1] range relative to Min/Max MI for its type
            double taskProp = normalizeTaskRequirement(taskLen, getMinMi(cloudlet), getMaxMi(cloudlet), 0.0, 1.0);

            // Calculate required resources based on this proportion
            // (Assuming task needs a proportion of the VM's *Total* capacity, or available?
            // Paper says "mapped to the range of available resources".
            // Let's assume it means proportion of *Available* resources is checked against
            // available?
            // No, "If a VM lacks sufficient resources to accommodate the task's normalized
            // demands".
            // This implies we compare Required vs Available.
            // Let's assume Required = Total_VM_Capacity * Prop.

            double reqMips = (vm.getMips() * vm.getNumberOfPes()) * taskProp;
            double reqRam = vm.getRam() * taskProp;
            double reqBw = vm.getBw() * taskProp;

            // 4. Resource Suitability Check
            if (availMips < reqMips || availRam < reqRam || availBw < reqBw) {
                continue; // Score = -1 (Skipped)
            }

            // 5. Scoring (Sum of Available Resources)
            double score = availMips + availRam + availBw;

            if (score > bestScore) {
                bestScore = score;
                bestVm = vm;
            }
        }
        return bestVm;
    }

    private void submitTaskToVm(ProposedCloudlet cloudlet, Vm vm) {
        // Update Local State
        int count = vmTaskCount.getOrDefault(vm.getId(), 0);
        vmTaskCount.put(vm.getId(), count + 1);

        // DEBUG LOG
        // Log.printLine(getName() + ": Submitting Task " + cloudlet.getCloudletId() + "
        // (Len: "
        // + cloudlet.getCloudletLength() + ") to VM " + vm.getId() + " (Tasks: " +
        // (count + 1) + ")");

        // Update Redis (L2 Cache) - User Requirement: Task_ID: RUNNING
        redis.hset(2, region, "VM_" + vm.getId(), "Task_" + cloudlet.getCloudletId(), "RUNNING");

        // OPTIMIZATION: Only register VM presence ONCE to save performance
        if (!registeredVms.contains(vm.getId())) {
            redis.hset(2, region, "VM_" + vm.getId(), "status", "ALIVE");
            redis.hset(2, region, "VM_" + vm.getId(), "Last_Heartbeat", String.valueOf(CloudSim.clock()));
            registeredVms.add(vm.getId());
        }

        // Track original submission time for accurate response time calculation
        if (cloudlet instanceof ProposedCloudlet) {
            ((ProposedCloudlet) cloudlet).setOriginalSubmissionTime(CloudSim.clock());
        }

        // Submit to CloudSim
        cloudlet.setVmId(vm.getId());
        cloudlet.setUserId(getId()); // Ensure Cloudlet has the correct User ID (important for Work Stealing)
        sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudActionTags.CLOUDLET_SUBMIT, cloudlet);

        cloudletsSubmitted++;
        getCloudletSubmittedList().add(cloudlet);
    }

    // --- SBDLB Helper Methods ---

    private double getAvailableMips(Vm vm) {
        double total = vm.getMips() * vm.getNumberOfPes();
        int tasks = vmTaskCount.getOrDefault(vm.getId(), 0);
        // Share of MIPS for a new task
        return total / (tasks + 1);
    }

    private double getAvailableRam(Vm vm) {
        int total = vm.getRam();
        int tasks = vmTaskCount.getOrDefault(vm.getId(), 0);
        if (tasks == 0)
            return total;
        double used = (total * tasks) / (double) TASK_THRESHOLD;
        return Math.max(0, total - used);
    }

    private double getAvailableBw(Vm vm) {
        long total = vm.getBw();
        int tasks = vmTaskCount.getOrDefault(vm.getId(), 0);
        if (tasks == 0)
            return total;
        double used = (total * tasks) / (double) TASK_THRESHOLD;
        return Math.max(0, total - used);
    }

    private double normalizeTaskRequirement(long len, long min, long max, double minRes, double maxRes) {
        if (max == min)
            return minRes;
        double norm = ((double) (len - min) / (max - min)) * (maxRes - minRes) + minRes;
        return Math.max(0, Math.min(maxRes, norm));
    }

    private long getMinMi(ProposedCloudlet c) {
        switch (c.getType()) {
            case REEL:
                return REEL_MIN_MI;
            case IMAGE:
                return IMAGE_MIN_MI;
            default:
                return TEXT_MIN_MI;
        }
    }

    private long getMaxMi(ProposedCloudlet c) {
        switch (c.getType()) {
            case REEL:
                return REEL_MAX_MI;
            case IMAGE:
                return IMAGE_MAX_MI;
            default:
                return TEXT_MAX_MI;
        }
    }

    @Override
    public void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        getCloudletReceivedList().add(cloudlet);

        // DEBUG LOG
        // Log.printLine(getName() + ": Task " + cloudlet.getCloudletId() + " FINISHED
        // at " + CloudSim.clock()
        // + " (CPU Time: " + cloudlet.getActualCPUTime() + ")");

        // Release VM Resource
        int vmId = cloudlet.getVmId();
        int count = vmTaskCount.getOrDefault(vmId, 0);
        if (count > 0)
            vmTaskCount.put(vmId, count - 1);

        // Update Redis (Task Done)
        redis.del(2, region, "VM_" + vmId); // Simplified: In real app, we'd remove just the task field
        redis.publish("tasks:complete", "Task " + cloudlet.getCloudletId() + " completed on VM " + vmId);

        cloudletsSubmitted--;

        // Try to schedule more tasks
        scheduleTasks();

        if (getCloudletList().isEmpty() && cloudletsSubmitted == 0) {
            simulationFinished = true;
            Log.printLine(getName() + ": All Cloudlets finished. Stopping Heartbeats.");
        }
    }

    // --- Fault Tolerance Methods ---

    // --- Getters for Failover Access ---
    public List<ProposedCloudlet> getHighPriorityQueue() {
        return highPriorityQueue;
    }

    public List<ProposedCloudlet> getMediumPriorityQueue() {
        return mediumPriorityQueue;
    }

    public List<ProposedCloudlet> getLowPriorityQueue() {
        return lowPriorityQueue;
    }

    // --- Fault Tolerance Methods ---

    public void notifyVmFailure(int vmId) {
        Log.printLine(getName() + ": WARNING - VM #" + vmId + " FAILED! Initiating Recovery...");

        // 1. Mark VM as DEAD in Redis
        redis.hset(2, region, "VM_" + vmId, "status", "DEAD");

        // 2. Find ALL tasks that were assigned to this VM (from CloudSim's internal
        // state)
        // This ensures we catch tasks that might not have been tracked in Redis yet
        List<ProposedCloudlet> failedTasks = new ArrayList<>();

        for (Cloudlet c : getCloudletSubmittedList()) {
            if (c.getVmId() == vmId && c instanceof ProposedCloudlet) {
                // Task was on the failed VM - needs to be rescheduled
                failedTasks.add((ProposedCloudlet) c);
            }
        }

        Log.printLine(getName() + ": Found " + failedTasks.size() + " tasks on failed VM #" + vmId);

        // 3. Remove from Submitted List (they will be resubmitted)
        getCloudletSubmittedList().removeAll(failedTasks);
        cloudletsSubmitted -= failedTasks.size();

        // 4. Retry ALL failed tasks (add to high priority queue for immediate
        // rescheduling)
        for (ProposedCloudlet task : failedTasks) {
            task.setVmId(-1); // Clear VM assignment
            retryTask(task);
        }

        // 5. Update VM Task Count (Clear it)
        vmTaskCount.put(vmId, 0);

        // 6. Clear Redis Entry for Dead VM
        redis.del(2, region, "VM_" + vmId);

        // 7. Auto-Recovery (Restart)
        restartVm(vmId);
    }

    private void restartVm(int vmId) {
        Log.printLine(getName() + ": Auto-Recovery - Initiating Restart for VM #" + vmId);

        // 1. Mark VM as RESTARTING (Simulating Shutdown + Boot Process)
        redis.hset(2, region, "VM_" + vmId, "status", "RESTARTING");

        // 2. Schedule Restart Completion Event (30s delay)
        schedule(getId(), VM_RESTART_DELAY, ProposedTags.VM_RESTART_COMPLETE, vmId);

        Log.printLine(getName() + ": VM #" + vmId + " is RESTARTING (ETA: " + VM_RESTART_DELAY + "s)");
    }

    private void completeVmRestart(int vmId) {
        Log.printLine(getName() + ": VM #" + vmId + " Restart Complete - Waiting for First Heartbeat");

        // 1. DO NOT set status to ALIVE yet - wait for VM to send heartbeat
        // The heartbeat handler will mark it ALIVE when the VM sends its first
        // heartbeat

        // 2. Start Heartbeat Loop (VM will send heartbeat and mark itself ALIVE)
        startVmHeartbeat(vmId);

        Log.printLine(getName() + ": VM #" + vmId + " heartbeat loop initiated. Awaiting confirmation...");
    }

    private void retryTask(ProposedCloudlet task) {
        task.incrementRetryCount();
        Log.printLine(
                getName() + ": Retrying Task " + task.getCloudletId() + " (Attempt " + task.getRetryCount() + ")");

        if (task.getRetryCount() > 3) {
            Log.printLine(getName() + ": Task " + task.getCloudletId() + " moved to QUARANTINE (Too many failures).");
            quarantineQueue.add(task);
            redis.hset(1, "Global", "Task_" + task.getCloudletId(), "status", "QUARANTINED");
        } else {
            task.updateStatus(Cloudlet.CloudletStatus.CREATED);
            highPriorityQueue.add(task);
            redis.hset(1, "Global", "Task_" + task.getCloudletId(), "status", "QUEUED");
        }
    }

    public List<ProposedCloudlet> getQuarantineQueue() {
        return quarantineQueue;
    }

    // --- LB Failover Logic ---

    // Fault Tolerance Constants
    private static final double LB_HEARTBEAT_TTL = 10.0; // Updated to 10s
    private static final double LB_HEARTBEAT_INTERVAL = 5.0; // LB sends heartbeat every 5s
    private static final double VM_HEARTBEAT_TTL = 10.0; // Updated to 10s
    private static final double MONITOR_INTERVAL = 3.0; // Updated to 3s
    private static final double VM_HEARTBEAT_INTERVAL = 5.0; // Send every 5s
    private static final double VM_RESTART_DELAY = 30.0; // VM restart time (30s)

    private Set<Integer> heartbeatStartedVms;

    // ... (Existing methods)

    @Override
    public void startEntity() {
        super.startEntity();
        sendHeartbeat(); // Initial Heartbeat
        schedule(getId(), 0, CloudActionTags.RESOURCE_CHARACTERISTICS_REQUEST);

        // Start Periodic Health Monitor
        schedule(getId(), MONITOR_INTERVAL, ProposedTags.PERIODIC_MONITOR);
        Log.printLine(getName() + " started Periodic Health Monitor (Interval: " + MONITOR_INTERVAL + "s)");

        // Start Periodic LB Heartbeat Loop
        schedule(getId(), LB_HEARTBEAT_INTERVAL, ProposedTags.LB_HEARTBEAT);
        Log.printLine(getName() + " started Periodic LB Heartbeat (Interval: " + LB_HEARTBEAT_INTERVAL + "s)");

        // Schedule Failure if configured
        if (failureTime >= 0) {
            schedule(getId(), failureTime, ProposedTags.INJECT_LB_FAILURE);
            Log.printLine(getName() + ": Scheduled Self-Destruction in " + failureTime + " seconds (Internal).");
        }
    }

    @Override
    protected void processVmCreateAck(SimEvent ev) {
        int[] data = (int[]) ev.getData();
        int datacenterId = data[0];
        int vmId = data[1];
        int result = data[2];

        if (result == CloudSimTags.TRUE) {
            super.processVmCreateAck(ev);
            // Start Heartbeat for the newly created VM
            startVmHeartbeat(vmId);
        } else {
            // VM Creation Failed - Retry in next Datacenter
            Log.printLine(getName() + ": Creation of Vm #" + vmId + " failed in Datacenter #" + datacenterId);

            // Find next Datacenter
            List<Integer> dcList = getDatacenterIdsList();
            int nextDcId = -1;
            for (int i = 0; i < dcList.size(); i++) {
                if (dcList.get(i) == datacenterId) {
                    if (i + 1 < dcList.size()) {
                        nextDcId = dcList.get(i + 1);
                    }
                    break;
                }
            }

            if (nextDcId != -1) {
                Log.printLine(getName() + ": Retrying Vm #" + vmId + " in Datacenter #" + nextDcId);
                Vm vm = VmList.getById(getGuestList(), vmId);
                if (vm != null) {
                    sendNow(nextDcId, CloudActionTags.VM_CREATE_ACK, vm); // Use VM_CREATE_ACK? No, VM_CREATE
                    // Wait, CloudActionTags.VM_CREATE is correct.
                    // But DatacenterBroker uses createVmsInDatacenter which sends VM_CREATE_ACK?
                    // No, createVmsInDatacenter sends VM_CREATE_ACK.
                    // Wait, DatacenterBroker line 336: sendNow(datacenterId,
                    // CloudActionTags.VM_CREATE_ACK, vm);
                    // Why VM_CREATE_ACK? That seems wrong. It should be VM_CREATE.
                    // But if DatacenterBroker does it, I should follow.
                    // Actually, CloudSim 3.0 DatacenterBroker sends VM_CREATE_ACK to Datacenter?
                    // Datacenter processes VM_CREATE_ACK?
                    // Let's check Datacenter.processEvent.
                    // But I will just copy what DatacenterBroker does.
                    sendNow(nextDcId, CloudActionTags.VM_CREATE_ACK, vm);
                }
            } else {
                Log.printLine(getName() + ": Failed to create Vm #" + vmId + " in all Datacenters.");
            }
        }
    }

    private void startVmHeartbeat(int vmId) {
        if (!heartbeatStartedVms.contains(vmId)) {
            heartbeatStartedVms.add(vmId);
            schedule(getId(), VM_HEARTBEAT_INTERVAL, ProposedTags.VM_HEARTBEAT, vmId);
            Log.printLine(getName() + ": Started Heartbeat Loop for VM #" + vmId);
        }
    }

    private boolean failed = false;
    private boolean takeoverDone = false;

    @Override
    public void processEvent(SimEvent ev) {
        // Log.printLine(getName() + ": Received Event Tag: " + ev.getTag());

        if (failed) {
            // If LB is failed, it shouldn't process anything
            return;
        }

        if (ev.getTag() instanceof ProposedTags) {
            if (ev.getTag() == ProposedTags.PERIODIC_MONITOR) {
                // ... (existing code)
                // 1. Check Peer LB Health
                checkPeerHealth();

                // 2. Check VM Health
                checkVmHealth();

                // 3. Reschedule Monitor
                if (!simulationFinished && !failed) {
                    schedule(getId(), MONITOR_INTERVAL, ProposedTags.PERIODIC_MONITOR);
                }
            } else if (ev.getTag() == ProposedTags.LB_HEARTBEAT) {
                // Send LB Heartbeat to L1 Cache
                sendHeartbeat();

                // Reschedule Next Heartbeat
                if (!simulationFinished && !failed) {
                    schedule(getId(), LB_HEARTBEAT_INTERVAL, ProposedTags.LB_HEARTBEAT);
                }
            } else if (ev.getTag() == ProposedTags.VM_HEARTBEAT) {
                // ... (existing code)
                // Handle VM Heartbeat Simulation
                int vmId = (Integer) ev.getData();
                if (heartbeatStartedVms.contains(vmId)) {
                    // Update Redis
                    redis.hset(2, region, "VM_" + vmId, "Last_Heartbeat", String.valueOf(CloudSim.clock()));
                    redis.hset(2, region, "VM_" + vmId, "status", "ALIVE"); // Ensure it's marked ALIVE

                    // Reschedule
                    if (!simulationFinished && !failed) {
                        schedule(getId(), VM_HEARTBEAT_INTERVAL, ProposedTags.VM_HEARTBEAT, vmId);
                    }
                }
            } else if (ev.getTag() == ProposedTags.VM_RESTART_COMPLETE) {
                // Handle VM Restart Completion
                int vmId = (Integer) ev.getData();
                completeVmRestart(vmId);
            } else if (ev.getTag() == ProposedTags.INJECT_VM_FAILURE) {
                // Handle Manual Failure Injection (Silent Failure)
                int vmId = (Integer) ev.getData();
                Log.printLine(getName() + ": Injecting Failure into VM #" + vmId + " (Stopping Heartbeats)");
                heartbeatStartedVms.remove(vmId);
                // We do NOT call notifyVmFailure here. We let checkVmHealth detect it.
            } else if (ev.getTag() == ProposedTags.INJECT_LB_FAILURE) {
                Log.printLine(getName() + ": CRITICAL FAILURE INJECTED! Stopping all operations.");
                failed = true;
            }
        } else if (ev.getTag() == CloudActionTags.BLANK) {
            Log.printLine(getName() + ": CRITICAL FAILURE INJECTED (via BLANK)! Stopping all operations.");
            failed = true;
        } else {
            super.processEvent(ev);
        }
    }

    // ... (Existing methods)

    public void checkVmHealth() {
        // Scan Local Zone (L2 Cache) for VM Heartbeats
        Set<String> vmKeys = redis.scanKeys(2, region, "VM_");
        double currentTime = CloudSim.clock();

        for (String key : vmKeys) {
            String status = redis.hget(2, region, key, "status");
            String lastHeartbeatStr = redis.hget(2, region, key, "Last_Heartbeat");

            if ("ALIVE".equals(status) && lastHeartbeatStr != null) {
                double lastHeartbeat = Double.parseDouble(lastHeartbeatStr);
                if (currentTime - lastHeartbeat > VM_HEARTBEAT_TTL) {
                    int vmId = Integer.parseInt(key.replace("VM_", ""));
                    Log.printLine(getName() + ": Detected VM Failure (VM #" + vmId + ") via Redis Heartbeat Timeout!");
                    notifyVmFailure(vmId);
                }
            }
        }
    }

    public void checkPeerHealth() {
        if (otherBroker != null && !takeoverDone) {
            // Redis-Based Failure Detection
            // Use getLbId() for Redis keys to match sendHeartbeat
            String heartbeatStr = redis.hget(1, "Global", "LB_Status", "LB_" + otherBroker.getLbId() + "_Heartbeat");

            boolean peerAlive = true;
            if (heartbeatStr != null) {
                double lastHeartbeat = Double.parseDouble(heartbeatStr);
                if (CloudSim.clock() - lastHeartbeat > LB_HEARTBEAT_TTL) {
                    peerAlive = false;
                    Log.printLine(getName() + ": Detected Peer Failure (LB" + otherBroker.getLbId()
                            + ") via Redis Heartbeat Timeout!");
                }
            }

            if (!peerAlive) {
                takeOver(otherBroker);
                takeoverDone = true; // Prevent repeated takeovers
            }
        }
    }

    private void sendHeartbeat() {
        // Update Redis L1 (Regional)
        // Use getLbId() (1 or 2) instead of getId() (Entity ID)
        redis.hset(1, "Global", "LB_Status", "LB_" + lbId + "_Heartbeat", String.valueOf(CloudSim.clock()));
        redis.hset(1, "Global", "LB_Status", "LB_" + lbId + "_Status", "ALIVE");
    }

    public void takeOver(ProposedBroker victim) {
        Log.printLine(getName() + ": CRITICAL - Detected " + victim.getName()
                + " FAILURE! Initiating TAKEOVER (Scenario C)...");

        // 1. Scan Level 1 Cache for Victim's Queued Tasks
        Set<String> taskKeys = redis.scanKeys(1, "Global", "Task_");
        List<ProposedCloudlet> rescuedTasks = new ArrayList<>();

        for (String key : taskKeys) {
            String assignedLb = redis.hget(1, "Global", key, "assigned_lb");
            String status = redis.hget(1, "Global", key, "status");

            // Use victim.getLbId()
            if (("LB" + victim.getLbId()).equals(assignedLb) && "QUEUED".equals(status)) {
                int taskId = Integer.parseInt(key.replace("Task_", ""));
                ProposedCloudlet task = findTaskInBroker(victim, taskId);

                if (task != null) {
                    rescuedTasks.add(task);
                    // Update Redis L1: Reassign to ME
                    redis.hset(1, "Global", key, "assigned_lb", "LB" + lbId);
                }
            }
        }

        // Move rescued tasks to my queues
        for (ProposedCloudlet task : rescuedTasks) {
            enqueueTask(task);
            // Remove from victim's queues
            victim.getHighPriorityQueue().remove(task);
            victim.getMediumPriorityQueue().remove(task);
            victim.getLowPriorityQueue().remove(task);
            victim.getOverflowQueue().remove(task);
        }
        Log.printLine(getName() + ": Rescued " + rescuedTasks.size() + " queued tasks from L1 Cache.");

        // 2. Connect to Victim's Level 2 Cache
        // Use victim.getLbId() to determine region
        String victimRegion = (victim.getLbId() == 1) ? "A" : "B";
        Log.printLine(getName() + ": Connecting to Level 2 Cache Region " + victimRegion + "...");

        Set<String> vmKeys = redis.scanKeys(2, victimRegion, "VM_");
        List<Vm> victimVms = new ArrayList<>();

        for (String vmKey : vmKeys) {
            int vmId = Integer.parseInt(vmKey.replace("VM_", ""));
            for (GuestEntity entity : victim.getGuestsCreatedList()) {
                if (entity.getId() == vmId && entity instanceof Vm) {
                    victimVms.add((Vm) entity);
                    break;
                }
            }
        }

        // Add VMs to Own List
        getGuestsCreatedList().addAll(victimVms);
        // CRITICAL FIX: Copy VM-to-Datacenter mapping so we know where to send tasks!
        getVmsToDatacentersMap().putAll(victim.getVmsToDatacentersMap());
        Log.printLine(getName() + ": Connected to " + victimVms.size() + " VMs via L2 Cache.");
    }

    private ProposedCloudlet findTaskInBroker(ProposedBroker broker, int taskId) {
        for (ProposedCloudlet c : broker.getHighPriorityQueue())
            if (c.getCloudletId() == taskId)
                return c;
        for (ProposedCloudlet c : broker.getMediumPriorityQueue())
            if (c.getCloudletId() == taskId)
                return c;
        for (ProposedCloudlet c : broker.getLowPriorityQueue())
            if (c.getCloudletId() == taskId)
                return c;
        for (ProposedCloudlet c : broker.getOverflowQueue())
            if (c.getCloudletId() == taskId)
                return c;
        return null;
    }
}
