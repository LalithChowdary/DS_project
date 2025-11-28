package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;

import java.util.ArrayList;
import java.util.List;

/**
 * Weighted Round Robin Load Balancer Broker.
 * Distributes tasks based on VM MIPS capacity weights.
 */
public class WeightedRoundRobinBroker extends DatacenterBroker {

    private List<Integer> vmWeights;
    private int currentVmIndex = -1;
    private int currentWeight = 0;
    private int gcdWeight;
    private int maxWeight;

    public WeightedRoundRobinBroker(String name) throws Exception {
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

        // Initialize weights if not done
        if (vmWeights == null) {
            initializeWeights(vmList);
        }

        for (Cloudlet cloudlet : list) {
            int vmIndex = getNextVmIndex();
            Vm vm = vmList.get(vmIndex);
            cloudlet.setGuestId(vm.getId());

            Log.printLine(CloudSim.clock() + ": " + getName() + ": Assigning Cloudlet " + cloudlet.getCloudletId()
                    + " to VM #" + vm.getId() + " (Weighted Round Robin)");

            sendNow(getVmsToDatacentersMap().get(vm.getId()),
                    org.cloudbus.cloudsim.core.CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
            cloudletsSubmitted++;
            getCloudletSubmittedList().add(cloudlet);
        }
        getCloudletList().clear();
    }

    private void initializeWeights(List<Vm> vmList) {
        vmWeights = new ArrayList<>();
        int minMips = Integer.MAX_VALUE;

        // Find base MIPS (smallest) to normalize weights
        for (Vm vm : vmList) {
            if (vm.getMips() < minMips) {
                minMips = (int) vm.getMips();
            }
        }

        // Assign weights: Weight = VM_MIPS / Min_MIPS
        maxWeight = 0;
        for (Vm vm : vmList) {
            int weight = (int) (vm.getMips() / minMips);
            vmWeights.add(weight);
            if (weight > maxWeight) {
                maxWeight = weight;
            }
        }

        // Calculate GCD of weights
        gcdWeight = calculateGCD(vmWeights);
        Log.printLine(getName() + ": VM Weights Initialized: " + vmWeights + " (GCD: " + gcdWeight + ", Max: "
                + maxWeight + ")");
    }

    private int getNextVmIndex() {
        while (true) {
            currentVmIndex = (currentVmIndex + 1) % vmWeights.size();
            if (currentVmIndex == 0) {
                currentWeight = currentWeight - gcdWeight;
                if (currentWeight <= 0) {
                    currentWeight = maxWeight;
                    if (currentWeight == 0)
                        return 0;
                }
            }
            if (vmWeights.get(currentVmIndex) >= currentWeight) {
                return currentVmIndex;
            }
        }
    }

    private int calculateGCD(List<Integer> weights) {
        int result = weights.get(0);
        for (int i = 1; i < weights.size(); i++) {
            result = gcd(result, weights.get(i));
        }
        return result;
    }

    private int gcd(int a, int b) {
        while (b > 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }
}
