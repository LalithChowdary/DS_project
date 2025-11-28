package org.cloudbus.cloudsim.examples.ds.proposed.evaluation;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedBroker;
import org.cloudbus.cloudsim.examples.ds.proposed.ProposedCloudlet;
import org.cloudbus.cloudsim.examples.ds.proposed.evaluation.Helper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Experiment4_WorkStealing {

    public static void main(String[] args) {
        Log.printLine("Starting Experiment 4: Work Stealing...");

        try {
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);

            // 1. Create Datacenters
            Datacenter dc1 = Helper.createDatacenter("Datacenter_1");
            Datacenter dc2 = Helper.createDatacenter("Datacenter_2");

            // 2. Create Brokers (LB1 and LB2)
            ProposedBroker lb1 = new ProposedBroker("LB1", 1);
            ProposedBroker lb2 = new ProposedBroker("LB2", 2);

            // 3. Link Brokers
            lb1.setOtherBroker(lb2);
            lb2.setOtherBroker(lb1);

            // 4. Create VMs
            // LB1: Under-provisioned (5 VMs)
            List<Vm> vms1 = Helper.createVmList(lb1.getId(), 5);
            lb1.submitGuestList(vms1);

            // LB2: Over-provisioned (20 VMs)
            List<Vm> vms2 = Helper.createVmList(lb2.getId(), 20);
            lb2.submitGuestList(vms2);

            // 5. Create Cloudlets (Imbalanced Workload)
            // LB1: Overloaded (200 Tasks)
            List<ProposedCloudlet> cloudlets1 = Helper.createCloudletList(lb1.getId(), 200);
            lb1.submitCloudletList(cloudlets1);

            // LB2: Idle (0 Tasks)
            List<ProposedCloudlet> cloudlets2 = new ArrayList<>();
            lb2.submitCloudletList(cloudlets2);

            // 6. Start Simulation
            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            Log.printLine("Experiment 4 Finished.");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }
}
