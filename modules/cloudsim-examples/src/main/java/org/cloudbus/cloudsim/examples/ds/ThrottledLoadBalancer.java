package org.cloudbus.cloudsim.examples.ds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Cloudlet;

/**
 * Throttled Load Balancer implementation
 * Based on traditional throttled approach used as baseline in the paper
 *
 * The throttled algorithm:
 * 1. Maintains an index table of VM states (available/busy)
 * 2. Sequentially scans for the first available VM
 * 3. Assigns the task to that VM and marks it busy
 * 4. When a task completes, marks the VM as available again
 *
 * This is a standard implementation used in CloudAnalyst and similar studies.
 */
public class ThrottledLoadBalancer {

    // Track VM availability: true = available, false = busy
    private Map<Integer, Boolean> vmAvailability;

    // Index for round-robin style scanning
    private int currentIndex;

    public ThrottledLoadBalancer() {
        this.vmAvailability = new HashMap<>();
        this.currentIndex = 0;
    }

    /**
     * Picks the first available VM in a sequential scan
     * Returns null if all VMs are busy (task should be queued)
     */
    public Vm pickVm(List<Vm> vms, Cloudlet cloudlet) {
        if (vms == null || vms.isEmpty()) {
            return null;
        }

        // Initialize VM availability on first call
        for (Vm vm : vms) {
            vmAvailability.putIfAbsent(vm.getId(), true);
        }

        // Sequential scan starting from current index
        int startIndex = currentIndex;
        int vmCount = vms.size();

        for (int i = 0; i < vmCount; i++) {
            int index = (startIndex + i) % vmCount;
            Vm vm = vms.get(index);

            // Check if VM is available
            if (vmAvailability.get(vm.getId())) {
                // Mark VM as busy
                vmAvailability.put(vm.getId(), false);

                // Update index for next allocation
                currentIndex = (index + 1) % vmCount;

                return vm;
            }
        }

        // No available VM found - task should be queued
        return null;
    }

    /**
     * Call this when a task completes to mark VM as available
     */
    public void releaseVm(Vm vm) {
        if (vm != null) {
            vmAvailability.put(vm.getId(), true);
        }
    }

    /**
     * Reset the balancer state (useful for new simulation runs)
     */
    public void reset() {
        vmAvailability.clear();
        currentIndex = 0;
    }

    /**
     * Get current VM availability status (for debugging/monitoring)
     */
    public boolean isVmAvailable(Vm vm) {
        return vmAvailability.getOrDefault(vm.getId(), true);
    }
}