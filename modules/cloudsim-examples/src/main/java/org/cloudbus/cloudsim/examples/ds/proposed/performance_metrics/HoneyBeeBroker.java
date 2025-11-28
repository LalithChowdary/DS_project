package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Honey Bee Foraging Load Balancer Broker.
 * Mimics the foraging behavior of honey bees.
 * - Scout Bees: Find suitable VMs.
 * - Forager Bees: Exploit the best sources (VMs with lowest load).
 */
public class HoneyBeeBroker extends DatacenterBroker {

    private Map<Integer, Integer> vmTaskCounts; // Tracks active tasks per VM
    private int currentVmId = -1; // The current "food source" (VM) being exploited
    private static final int CUTOFF_THRESHOLD = 2; // Threshold to switch VM (Overload limit)

    public HoneyBeeBroker(String name) throws Exception {
        super(name);
        vmTaskCounts = new HashMap<>();
    }

    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        getCloudletReceivedList().add(cloudlet);

        // Decrement task count for the VM
        int vmId = cloudlet.getVmId();
        int count = vmTaskCounts.getOrDefault(vmId, 0);
        if (count > 0) {
            vmTaskCounts.put(vmId, count - 1);
        }

        Log.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + cloudlet.getCloudletId()
                + " finished on VM #" + vmId);

        cloudletsSubmitted--;
        if (getCloudletList().size() == 0 && cloudletsSubmitted == 0) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": All Cloudlets executed. Finishing...");
            clearDatacenters();
            finishExecution();
        }
    }

    @Override
    protected void submitCloudlets() {
        List<Cloudlet> list = getCloudletList();
        List<Vm> vmList = new ArrayList<>();
        for (org.cloudbus.cloudsim.core.GuestEntity guest : getGuestsCreatedList()) {
            if (guest instanceof Vm) {
                vmList.add((Vm) guest);
            }
        }

        if (vmList.isEmpty()) {
            Log.printLine(getName() + ": Error - No VMs created. Cannot submit cloudlets.");
            return;
        }

        // Initialize VM counts
        for (Vm vm : vmList) {
            vmTaskCounts.putIfAbsent(vm.getId(), 0);
        }

        if (currentVmId == -1) {
            currentVmId = vmList.get(0).getId(); // Start with first VM
        }

        for (Cloudlet cloudlet : list) {
            int vmId = getScoutBee(vmList);
            cloudlet.setGuestId(vmId);

            // Increment task count
            vmTaskCounts.put(vmId, vmTaskCounts.getOrDefault(vmId, 0) + 1);

            Log.printLine(CloudSim.clock() + ": " + getName() + ": Assigning Cloudlet " + cloudlet.getCloudletId()
                    + " to VM #" + vmId + " (Honey Bee)");

            sendNow(getVmsToDatacentersMap().get(vmId), org.cloudbus.cloudsim.core.CloudActionTags.CLOUDLET_SUBMIT,
                    cloudlet);
            cloudletsSubmitted++;
            getCloudletSubmittedList().add(cloudlet);
        }
        getCloudletList().clear();
    }

    /**
     * Determines the next VM to assign a task to.
     * Logic:
     * 1. If current VM load < CUTOFF, keep using it (Exploitation).
     * 2. If current VM load >= CUTOFF, find VM with minimum load
     * (Exploration/Waggle Dance).
     */
    private int getScoutBee(List<Vm> vmList) {
        int currentLoad = vmTaskCounts.getOrDefault(currentVmId, 0);

        if (currentLoad < CUTOFF_THRESHOLD) {
            return currentVmId; // Stay with current source
        } else {
            // Waggle Dance: Find best new source (VM with min load)
            return waggleDance(vmList);
        }
    }

    private int waggleDance(List<Vm> vmList) {
        int bestVmId = -1;
        int minLoad = Integer.MAX_VALUE;

        for (Vm vm : vmList) {
            int load = vmTaskCounts.getOrDefault(vm.getId(), 0);
            if (load < minLoad) {
                minLoad = load;
                bestVmId = vm.getId();
            }
        }

        currentVmId = bestVmId; // Update current source
        return bestVmId;
    }
}
