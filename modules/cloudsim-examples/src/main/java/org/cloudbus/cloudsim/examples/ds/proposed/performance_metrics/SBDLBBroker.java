package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.examples.ds.ScoreBasedLoadBalancer;

/**
 * Broker that implements the Score-Based Dynamic Load Balancer (SBDLB).
 * Adapted from CloudSimExampleSBDLB.java for comparison purposes.
 */
public class SBDLBBroker extends DatacenterBroker {

    private ScoreBasedLoadBalancer sbdlb;
    private Queue<Cloudlet> waitingQueue;
    private int maxWaitingQueueSize;
    private Map<Integer, Integer> taskVmAssignments;

    public SBDLBBroker(String name) throws Exception {
        super(name);
        this.sbdlb = new ScoreBasedLoadBalancer();
        this.waitingQueue = new LinkedList<>();
        this.maxWaitingQueueSize = 0;
        this.taskVmAssignments = new HashMap<>();
    }

    @Override
    protected void submitCloudlets() {
        List<Cloudlet> list = getCloudletList();
        List<Vm> vmList = new ArrayList<>();
        for (GuestEntity guest : getGuestsCreatedList()) {
            if (guest instanceof Vm) {
                vmList.add((Vm) guest);
            }
        }

        if (vmList.isEmpty()) {
            Log.printLine(getName() + ": Error - No VMs created. Cannot submit cloudlets.");
            return;
        }

        List<Cloudlet> successfullySubmitted = new ArrayList<>();

        // STEP 1: Process waiting queue first
        if (!waitingQueue.isEmpty()) {
            Log.printLine(
                    CloudSim.clock() + ": " + getName() + " Processing " + waitingQueue.size() + " waiting tasks...");
        }

        int initialWaitingSize = waitingQueue.size();
        for (int i = 0; i < initialWaitingSize; i++) {
            Cloudlet waitingCloudlet = waitingQueue.peek();
            if (waitingCloudlet == null)
                break;

            Vm vm = sbdlb.pickVm(vmList, waitingCloudlet);

            if (vm != null) {
                waitingQueue.poll(); // Remove from queue
                assignCloudletToVm(waitingCloudlet, vm);
            } else {
                // Still no resources - keep in queue and stop processing waiting queue
                break;
            }
        }

        // Track max queue size
        maxWaitingQueueSize = Math.max(maxWaitingQueueSize, waitingQueue.size());

        // STEP 2: Process new cloudlets
        for (Cloudlet cloudlet : list) {
            Vm vm = sbdlb.pickVm(vmList, cloudlet);

            if (vm != null) {
                assignCloudletToVm(cloudlet, vm);
                successfullySubmitted.add(cloudlet);
            } else {
                // No resources available, send to waiting queue
                waitingQueue.offer(cloudlet);
                successfullySubmitted.add(cloudlet); // Remove from main list to avoid reprocessing
                Log.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + cloudlet.getCloudletId()
                        + " queued (No suitable VM)");
            }
        }

        // Track max queue size
        maxWaitingQueueSize = Math.max(maxWaitingQueueSize, waitingQueue.size());

        // Remove successfully processed/queued cloudlets from the main list
        getCloudletList().removeAll(successfullySubmitted);
    }

    private void assignCloudletToVm(Cloudlet cloudlet, Vm vm) {
        cloudlet.setGuestId(vm.getId());
        taskVmAssignments.put(cloudlet.getCloudletId(), vm.getId());

        Log.printLine(CloudSim.clock() + ": " + getName() + ": Assigning Cloudlet " + cloudlet.getCloudletId()
                + " to VM #" + vm.getId() + " (SBDLB)");

        sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
        cloudletsSubmitted++;
        getCloudletSubmittedList().add(cloudlet);
    }

    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();

        // Find the VM that completed this task
        // We can't easily get the VM object from ID if we don't have the list handy,
        // but we can iterate through created guests.
        Vm vm = null;
        for (GuestEntity guest : getGuestsCreatedList()) {
            if (guest.getId() == cloudlet.getGuestId()) {
                vm = (Vm) guest;
                break;
            }
        }

        // Release resources from assigned VM
        if (vm != null) {
            sbdlb.releaseVm(vm);
        }

        // Call parent implementation
        super.processCloudletReturn(ev);

        // Check if there are waiting tasks
        if (!waitingQueue.isEmpty() || !getCloudletList().isEmpty()) {
            submitCloudlets();
        }
    }
}
