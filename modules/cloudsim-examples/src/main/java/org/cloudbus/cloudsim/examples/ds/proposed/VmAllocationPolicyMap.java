package org.cloudbus.cloudsim.examples.ds.proposed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.HostEntity;

/**
 * VmAllocationPolicyMap allows defining a static mapping between VMs and Hosts.
 * If a mapping exists, the VM is allocated to the specified Host.
 * Otherwise, it falls back to a simple First Fit strategy.
 */
public class VmAllocationPolicyMap extends VmAllocationPolicy {

    private Map<Integer, Integer> vmToHostMap;

    public VmAllocationPolicyMap(List<? extends Host> list) {
        super(list);
        this.vmToHostMap = new HashMap<>();
    }

    public void mapVmToHost(int vmId, int hostId) {
        vmToHostMap.put(vmId, hostId);
    }

    @Override
    public HostEntity findHostForGuest(GuestEntity guest) {
        int requiredHostId = vmToHostMap.getOrDefault(guest.getId(), -1);

        if (requiredHostId != -1) {
            // Try to allocate to the specific host
            for (HostEntity host : getHostList()) {
                if (host.getId() == requiredHostId) {
                    // Check suitability if possible, or just return it and let allocation fail if
                    // full
                    // But to be safe, we should check.
                    if (host instanceof Host && guest instanceof Vm) {
                        if (((Host) host).isSuitableForVm((Vm) guest)) {
                            Log.printLine("VmAllocationPolicyMap: Found Pre-defined Host #" + host.getId() + " for VM #"
                                    + guest.getId());
                            return host;
                        } else {
                            Log.printLine("VmAllocationPolicyMap: WARNING - Pre-defined Host #" + host.getId()
                                    + " is NOT SUITABLE for VM #" + guest.getId());
                        }
                    } else {
                        return host; // Just return it if we can't check
                    }
                }
            }
            Log.printLine("VmAllocationPolicyMap: WARNING - Host #" + requiredHostId
                    + " not found or not suitable for VM #" + guest.getId());
            return null; // Strict: Fail if mapped host is not suitable
        }

        // Strict: If no mapping exists, do NOT allocate (prevent stealing by other DCs)
        return null;
    }
}
