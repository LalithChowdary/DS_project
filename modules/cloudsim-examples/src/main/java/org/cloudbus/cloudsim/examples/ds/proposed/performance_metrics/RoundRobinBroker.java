package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;

import java.util.List;
import java.util.ArrayList;

/**
 * Round Robin Load Balancer Broker.
 * Distributes tasks cyclically across available VMs.
 */
public class RoundRobinBroker extends DatacenterBroker {

    private int currentVmIndex = 0;

    public RoundRobinBroker(String name) throws Exception {
        super(name);
    }

    @Override
    protected void processCloudletReturn(org.cloudbus.cloudsim.core.SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        getCloudletReceivedList().add(cloudlet);
        Log.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + cloudlet.getCloudletId() + " received");
        cloudletsSubmitted--;
        if (getCloudletList().size() == 0 && cloudletsSubmitted == 0) {
            Log.printLine(CloudSim.clock() + ": " + getName() + ": All Cloudlets executed. Finishing...");
            clearDatacenters();
            finishExecution();
        } else {
            if (getCloudletList().size() > 0 && cloudletsSubmitted == 0) {
                clearDatacenters();
                createVmsInDatacenter(0);
            }
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

        for (Cloudlet cloudlet : list) {
            Vm vm = vmList.get(currentVmIndex);
            cloudlet.setGuestId(vm.getId());

            Log.printLine(CloudSim.clock() + ": " + getName() + ": Assigning Cloudlet " + cloudlet.getCloudletId()
                    + " to VM #" + vm.getId() + " (Round Robin)");

            sendNow(getVmsToDatacentersMap().get(vm.getId()),
                    org.cloudbus.cloudsim.core.CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
            cloudletsSubmitted++;
            currentVmIndex = (currentVmIndex + 1) % vmList.size();
            getCloudletSubmittedList().add(cloudlet);
        }
        getCloudletList().clear();
    }
}
