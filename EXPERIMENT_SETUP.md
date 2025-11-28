# Experiment Configuration Details

This document outlines the exact configuration used in the `RunSingleExperiment.java` simulation.

## 1. Infrastructure Setup

### Datacenters
*   **Count:** 4 Datacenters (`Datacenter_0` to `Datacenter_3`)
*   **Architecture:** x86
*   **OS:** Linux
*   **VMM:** Xen

### Physical Machines (Hosts)
*   **Count per Datacenter:** 4 Hosts
*   **Total Hosts:** 16 Hosts
*   **Specs per Host:**
    *   **Cores:** 8 PEs (Processing Elements)
    *   **MIPS per Core:** 10,000 MIPS
    *   **RAM:** 16 GB (16,384 MB)
    *   **Bandwidth:** 10 Gbps
    *   **Storage:** 1 TB

### VM Allocation Policy
*   **Policy:** `VmAllocationPolicySimple` (Default CloudSim policy, typically Worst Fit or First Fit).
*   **Note:** VMs are allocated dynamically to hosts based on available resources. There is **no hardcoded mapping** of specific VMs to specific Hosts (e.g., "Host 1 gets 3 Type 2 VMs").

## 2. Virtual Machines (VMs)

### VM Types
| Type | Name | Cores (PEs) | MIPS | RAM | Count per DC | Total Count (4 DCs) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Type 1** | Small | 1 | 2,500 | 2 GB | 10 | 40 |
| **Type 2** | Medium | 2 | 5,000 | 4 GB | 6 | 24 |
| **Type 3** | Large | 4 | 10,000 | 8 GB | 2 | 8 |
| **Total** | | | | | **18** | **72** |

*   **Total VMs:** 72
*   **Creation Order:** Large -> Medium -> Small (to optimize packing).

## 3. Workload Configuration

### Task Types
| Type | Name | Size Range (MI) | Percentage | Count |
| :--- | :--- | :--- | :--- | :--- |
| **Reel** | Video Processing | 50M - 5000M | 60% | 1,200 |
| **Image** | Image Processing | 2.5M - 150M | 30% | 600 |
| **Text** | Text Processing | 5K - 50K | 10% | 200 |

### Task Execution
*   **Total Tasks:** 2,000
*   **Task Threshold:** 3 Concurrent Tasks per VM (defined in `ScoreBasedLoadBalancer.java`).
*   **Core Usage (Dynamic):**
    *   **Reels:** Require **2 Cores** (PEs).
    *   **Images/Text:** Require **1 Core** (PE).

## 4. Performance Metrics

### Metrics Collected
1.  **Average Response Time (ms):** Average time from task submission to completion.
2.  **Throughput (Tasks/sec):** Total successful tasks divided by the simulation makespan.
3.  **Success Count:** Number of tasks that completed successfully.

### Results Location
*   **File:** `separate_results.csv`
*   **Format:** `Algorithm,AvgResponseTime,Throughput,SuccessCount`

## 5. Algorithms Evaluated
1.  **RR:** Round Robin
2.  **WRR:** Weighted Round Robin
3.  **HBF:** Honey Bee Foraging
4.  **SBDLB:** Score-Based Dynamic Load Balancing (Original)
5.  **PROPOSED:** SBDLB + MLFQ (Multi-Level Feedback Queue)

## 6. How to Run

```bash
# Clear previous results
rm separate_results.csv

# Run each algorithm separately
mvn exec:java -pl modules/cloudsim-examples -Dexec.mainClass="org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics.RunSingleExperiment" -Dexec.args="RR separate_results.csv"
mvn exec:java -pl modules/cloudsim-examples -Dexec.mainClass="org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics.RunSingleExperiment" -Dexec.args="WRR separate_results.csv"
mvn exec:java -pl modules/cloudsim-examples -Dexec.mainClass="org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics.RunSingleExperiment" -Dexec.args="HBF separate_results.csv"
mvn exec:java -pl modules/cloudsim-examples -Dexec.mainClass="org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics.RunSingleExperiment" -Dexec.args="SBDLB separate_results.csv"
mvn exec:java -pl modules/cloudsim-examples -Dexec.mainClass="org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics.RunSingleExperiment" -Dexec.args="PROPOSED separate_results.csv"
```
