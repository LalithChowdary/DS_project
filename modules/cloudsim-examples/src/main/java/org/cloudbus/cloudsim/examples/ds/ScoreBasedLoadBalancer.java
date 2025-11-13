package org.cloudbus.cloudsim.examples.ds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;

/**
 * Score-Based Dynamic Load Balancer (SBDLB) implementation
 * Based on the paper: "A Dynamic Approach to Load Balancing in Cloud
 * Infrastructure"
 *
 * Key features:
 * - Evaluates VMs based on AVAILABLE (not total) MIPS, RAM, and Bandwidth
 * - Uses task threshold (max 3 concurrent tasks per VM)
 * - Normalizes task requirements using min-max scaling
 * - Assigns tasks to VM with highest suitability score
 */
public class ScoreBasedLoadBalancer {

    private static final int TASK_THRESHOLD = 3;

    // Task type ranges in MI (Million Instructions) - from paper Section V-B
    private static final long REEL_MIN_MI = 10000000L; // 10 MB * 1000 CI
    private static final long REEL_MAX_MI = 1000000000L; // 1 GB * 1000 CI
    private static final long IMAGE_MIN_MI = 500000L; // 1 MB * 500 CI
    private static final long IMAGE_MAX_MI = 30000000L; // 30 MB * 1000 CI
    private static final long TEXT_MIN_MI = 1000L; // 10 KB * 100 CI
    private static final long TEXT_MAX_MI = 10000L; // 100 KB * 100 CI

    // Track current task count per VM
    private Map<Integer, Integer> vmTaskCount;

    // Track which cloudlets are assigned to which VM (for resource calculation)
    private Map<Integer, List<Cloudlet>> vmCloudletMap;

    public ScoreBasedLoadBalancer() {
        this.vmTaskCount = new HashMap<>();
        this.vmCloudletMap = new HashMap<>();
    }

    /**
     * Picks the best VM for a cloudlet based on SBDLB algorithm
     * Returns null if no suitable VM is available (task should be queued)
     */
    public Vm pickVm(List<Vm> vms, Cloudlet cloudlet) {
        if (vms == null || vms.isEmpty()) {
            return null;
        }

        Vm bestVm = null;
        double bestScore = -1.0;

        for (Vm vm : vms) {
            // Step 1: Check task threshold (max 3 concurrent tasks)
            int currentTasks = vmTaskCount.getOrDefault(vm.getId(), 0);
            if (currentTasks >= TASK_THRESHOLD) {
                continue; // Skip VMs that exceed threshold
            }

            // Step 2: Get AVAILABLE resources (CRITICAL: not total resources)
            double availableMips = getAvailableMips(vm);
            double availableRam = getAvailableRam(vm);
            double availableBw = getAvailableBw(vm);

            // Step 3: Normalize task requirements
            // First, get the task's proportional resource need (0 to 1)
            long taskLength = cloudlet.getCloudletLength();
            double taskProportion = normalizeTaskRequirement(
                    taskLength,
                    getTaskLengthMin(cloudlet),
                    getTaskLengthMax(cloudlet),
                    0.0,
                    1.0);

            // Apply proportion to available resources
            double requiredMips = availableMips * taskProportion;
            double requiredRam = availableRam * taskProportion;
            double requiredBw = availableBw * taskProportion;

            // Step 4: Check if VM has sufficient resources
            if (availableMips < requiredMips ||
                    availableRam < requiredRam ||
                    availableBw < requiredBw) {
                continue; // Insufficient resources, assign score of -1 (skip this VM)
            }

            // Step 5: Compute suitability score (sum of available resources)
            // As per paper Section IV-A: Score = availableMIPS + availableRAM + availableBW
            double score = availableMips + availableRam + availableBw;

            // Step 6: Track best VM
            if (score > bestScore) {
                bestScore = score;
                bestVm = vm;
            }
        }

        // If a VM is selected, increment its task count
        if (bestVm != null) {
            int currentTasks = vmTaskCount.getOrDefault(bestVm.getId(), 0);
            vmTaskCount.put(bestVm.getId(), currentTasks + 1);
        }

        return bestVm; // Returns null if no suitable VM found (task should be queued)
    }

    /**
     * Call this when a task completes to release the VM slot
     * CRITICAL: This must be called to maintain dynamic behavior
     */
    public void releaseVm(Vm vm) {
        if (vm != null) {
            int currentTasks = vmTaskCount.getOrDefault(vm.getId(), 0);
            if (currentTasks > 0) {
                vmTaskCount.put(vm.getId(), currentTasks - 1);
            }
        }
    }

    /**
     * Min-max normalization formula from paper (Equation 1)
     * y = ((x - xmin) / (xmax - xmin)) * (ymax - ymin) + ymin
     */
    private double normalizeTaskRequirement(long taskLength, long minLength, long maxLength,
            double minResource, double maxResource) {
        if (maxLength == minLength) {
            return minResource; // Avoid division by zero
        }

        double normalized = ((double) (taskLength - minLength) / (maxLength - minLength))
                * (maxResource - minResource) + minResource;

        return Math.max(0, Math.min(maxResource, normalized)); // Clamp to valid range
    }

    /**
     * Get AVAILABLE MIPS for a VM (CRITICAL FIX)
     * This calculates remaining MIPS, not total capacity
     */
    private double getAvailableMips(Vm vm) {
        double totalMips = vm.getMips() * vm.getNumberOfPes();

        // Calculate currently used MIPS based on task count
        // Simple model: each task uses proportional MIPS
        int currentTasks = vmTaskCount.getOrDefault(vm.getId(), 0);

        if (currentTasks == 0) {
            return totalMips;
        }

        // Estimate used MIPS (simple model: divide equally among tasks)
        double usedMips = (totalMips * currentTasks) / (double) TASK_THRESHOLD;

        return Math.max(0, totalMips - usedMips);
    }

    /**
     * Get AVAILABLE RAM for a VM (CRITICAL FIX)
     * Returns remaining RAM, not total
     */
    private double getAvailableRam(Vm vm) {
        int totalRam = vm.getRam();

        // Calculate used RAM based on current task count
        // Each task uses proportional RAM
        int currentTasks = vmTaskCount.getOrDefault(vm.getId(), 0);

        if (currentTasks == 0) {
            return totalRam;
        }

        // Estimate used RAM (simple model: divide equally among tasks)
        double usedRam = (totalRam * currentTasks) / (double) TASK_THRESHOLD;

        return Math.max(0, totalRam - usedRam);
    }

    /**
     * Get AVAILABLE Bandwidth for a VM (CRITICAL FIX)
     * Returns remaining bandwidth, not total
     */
    private double getAvailableBw(Vm vm) {
        long totalBw = vm.getBw();

        // Calculate used BW based on current task count
        int currentTasks = vmTaskCount.getOrDefault(vm.getId(), 0);

        if (currentTasks == 0) {
            return totalBw;
        }

        // Estimate used BW (simple model: divide equally among tasks)
        double usedBw = (totalBw * currentTasks) / (double) TASK_THRESHOLD;

        return Math.max(0, totalBw - usedBw);
    }

    /**
     * Determine task length range based on task type
     * Uses MI (Million Instructions) ranges from paper
     */
    private long getTaskLengthMin(Cloudlet cloudlet) {
        long length = cloudlet.getCloudletLength();

        // Classify based on length ranges from paper (in MI)
        if (length >= REEL_MIN_MI) {
            return REEL_MIN_MI;
        } else if (length >= IMAGE_MIN_MI) {
            return IMAGE_MIN_MI;
        } else {
            return TEXT_MIN_MI;
        }
    }

    private long getTaskLengthMax(Cloudlet cloudlet) {
        long length = cloudlet.getCloudletLength();

        // Classify based on length ranges from paper (in MI)
        if (length >= REEL_MIN_MI) {
            return REEL_MAX_MI;
        } else if (length >= IMAGE_MIN_MI) {
            return IMAGE_MAX_MI;
        } else {
            return TEXT_MAX_MI;
        }
    }

    /**
     * Get current task count for a VM (useful for monitoring)
     */
    public int getVmTaskCount(Vm vm) {
        return vmTaskCount.getOrDefault(vm.getId(), 0);
    }

    /**
     * Reset task counts (useful for new simulation runs)
     */
    public void reset() {
        vmTaskCount.clear();
        vmCloudletMap.clear();
    }
}