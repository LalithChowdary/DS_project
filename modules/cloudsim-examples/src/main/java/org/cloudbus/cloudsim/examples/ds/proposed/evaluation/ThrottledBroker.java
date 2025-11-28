package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.CloudActionTags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThrottledBroker extends DatacenterBroker {

    private Map<Integer, Boolean> vmAvailability; // True = Available, False = Busy
    private List<Cloudlet> throttledQueue;

    public ThrottledBroker(String name) throws Exception {
        super(name);
        vmAvailability = new HashMap<>();
        throttledQueue = new ArrayList<>();
    }

    @Override
    public void processEvent(SimEvent ev) {
        super.processEvent(ev);
    }

    @Override
    protected void processVmCreateAck(SimEvent ev) {
        super.processVmCreateAck(ev);
        // Mark all created VMs as Available
        for (Object obj : getGuestList()) {
            Vm vm = (Vm) obj;
            vmAvailability.put(vm.getId(), true);
        }
    }

    @Override
    protected void submitCloudlets() {
        List<Cloudlet> list = getCloudletList();
        throttledQueue.addAll(list);
        getCloudletList().clear();
        scheduleThrottled();
    }

    private void scheduleThrottled() {
        List<Cloudlet> toRemove = new ArrayList<>();

        for (Cloudlet cloudlet : throttledQueue) {
            Vm vm = findAvailableVm();
            if (vm != null) {
                // Assign
                vmAvailability.put(vm.getId(), false); // Mark Busy
                cloudlet.setVmId(vm.getId());
                sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
                cloudletsSubmitted++;
                getCloudletSubmittedList().add(cloudlet);
                toRemove.add(cloudlet);
            } else {
                // No VM available, stop scheduling for now
                break;
            }
        }
        throttledQueue.removeAll(toRemove);
    }

    private Vm findAvailableVm() {
        for (Object obj : getGuestList()) {
            Vm vm = (Vm) obj;
            if (vmAvailability.getOrDefault(vm.getId(), false)) {
                return vm;
            }
        }
        return null;
    }

    @Override
    public void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        getCloudletReceivedList().add(cloudlet);
        cloudletsSubmitted--;

        // Mark VM as Available
        vmAvailability.put(cloudlet.getVmId(), true);

        // Try to schedule pending tasks
        scheduleThrottled();

        // Check if all finished
        if (getCloudletList().size() == 0 && cloudletsSubmitted == 0 && throttledQueue.isEmpty()) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": All Cloudlets executed. Finishing...");
            clearDatacenters();
            finishExecution();
        }
    }
}
