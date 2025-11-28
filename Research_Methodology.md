# 3. Methodology

This section details the proposed **Multi-Zone Hierarchical Cloud Architecture**, designed to optimize resource utilization and ensure high availability through intelligent load balancing and intra-region fault tolerance.

## 3.1 System Architecture Overview

The system is organized into a single **Geographic Region** subdivided into multiple **Availability Zones (AZs)**.

*   **Global Dispatcher:** The entry point for all incoming tasks. It distributes workload randomly across available Zones to ensure a baseline load distribution.
*   **Zonal Load Balancers (LBs):** Each Availability Zone is managed by a dedicated Load Balancer (e.g., LB1 for Zone 1, LB2 for Zone 2). These LBs are the core decision-making entities, responsible for:
    *   **Task Scheduling:** Using a Multi-Level Feedback Queue (MLFQ).
    *   **VM Selection:** Applying the Score-Based Dynamic Load Balancing (SBDLB) algorithm.
    *   **Fault Tolerance:** Monitoring both VM and Peer LB health within the region.
*   **Datacenters:** Each Zone contains multiple Datacenters (4 per Zone), which house the physical infrastructure (Hosts) and Virtual Machines (VMs).

## 3.2 Infrastructure Specifications

The experimental environment is modeled with a heterogeneous mix of resources to simulate realistic cloud conditions.

### 3.2.1 Physical Infrastructure (Hosts)
Each Datacenter contains **4 Physical Hosts** with the following uniform specifications:
*   **Processing Power:** 10,000 MIPS (Million Instructions Per Second) per Core.
*   **Cores:** 8 Cores per Host.
*   **RAM:** 16 GB.
*   **Bandwidth:** 10 Gbps.
*   **Storage:** 1 TB.

### 3.2.2 Virtual Machines (VMs)
To support diverse workload requirements, we provision three distinct types of VMs. Each Datacenter hosts **18 VMs** with the following distribution:

| VM Type | Count | Cores | MIPS | RAM | Bandwidth | Use Case |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Type 1 (Small)** | 10 | 1 | 2,500 | 2 GB | 1 Gbps | Lightweight tasks (Text Processing) |
| **Type 2 (Medium)** | 6 | 2 | 5,000 | 4 GB | 1 Gbps | Moderate tasks (Image Processing) |
| **Type 3 (Large)** | 2 | 4 | 10,000 | 8 GB | 1 Gbps | Compute-intensive tasks (Video Processing) |

## 3.3 Two-Level Caching Strategy (Redis)

To ensure low-latency state management and efficient failure recovery, the system employs a **Two-Level Redis Cache Architecture**:

1.  **Level 1 (Regional Cache):**
    *   **Scope:** Accessible by all Zonal LBs within the Region.
    *   **Data:** Stores global task states (`QUEUED`, `ASSIGNED`), LB Heartbeats, and Zone Status.
    *   **Role:** Acts as the "Source of Truth" for intra-region coordination and Zone Failover.

2.  **Level 2 (Zonal Cache):**
    *   **Scope:** Private to each Availability Zone (e.g., Zone 1 Cache, Zone 2 Cache).
    *   **Data:** Stores granular VM states (`ALIVE`, `DEAD`), VM Heartbeats, and Task-to-VM mappings (`RUNNING`).
    *   **Role:** Enables rapid local decision-making and VM Auto-Replacement without querying the regional layer.

## 3.4 Proposed Load Balancing Algorithm (SBDLB)

The **Score-Based Dynamic Load Balancing (SBDLB)** algorithm optimizes task placement by dynamically calculating a "Suitability Score" for each VM.

### Step 1: Task Normalization
Incoming tasks are heterogeneous (Text, Image, Video). We normalize their length ($L$) to a $[0, 1]$ scale relative to the minimum ($L_{min}$) and maximum ($L_{max}$) lengths for their type:
$$
P_{norm} = \frac{L - L_{min}}{L_{max} - L_{min}}
$$

### Step 2: Resource Suitability Check
Before scoring, a VM is strictly filtered. It must have sufficient **Available Resources** ($R_{avail}$) to meet the task's **Required Resources** ($R_{req}$), calculated as a proportion of the VM's total capacity ($R_{total}$):
$$
R_{req} = R_{total} \times P_{norm}
$$
**Condition:** A VM is candidate if and only if:
$$
(MIPS_{avail} \ge MIPS_{req}) \land (RAM_{avail} \ge RAM_{req}) \land (BW_{avail} \ge BW_{req})
$$

### Step 3: Scoring
Candidate VMs are scored based on their remaining capacity. The VM with the highest score is selected:
$$
Score = MIPS_{avail} + RAM_{avail} + BW_{avail}
$$

## 3.5 Fault Tolerance & Resilience
The system implements a multi-layered fault tolerance mechanism:

1.  **VM Failure Detection & Recovery:**
### 4.2. Health Monitoring Frequency
- **Load Balancer Monitoring Interval:** **3.0 seconds** (Checks peer LB and VM health).
- **VM Heartbeat Interval:** **5.0 seconds** (VMs send "I am alive" signal).
- **VM Heartbeat TTL:** **10.0 seconds** (Time-To-Live for VM heartbeat before declared DEAD).
- **LB Heartbeat Interval:** **5.0 seconds** (LBs send "I am alive" signal).
- **LB Heartbeat TTL:** **10.0 seconds** (Time-To-Live for LB heartbeat).

### 4.3. Recovery Strategy
- **VM Failure:**
    - **Detection:** Redis Heartbeat Timeout (> 10s).
    - **Action:** **Restart VM** (Soft Recovery).
        - The VM status is reset to ALIVE in Redis.
        - The heartbeat loop is restarted.
        - Failed tasks are re-queued to the Load Balancer.
- **LB Failure:**
    - **Detection:** Redis Heartbeat Timeout (> 5s).
    - **Action:** **Takeover** (Active-Passive Failover).
        - Peer LB detects failure via L1 Cache.
        - Peer LB claims ownership of failed LB's tasks (L1 Cache) and VMs (L2 Cache).  *   **Timeout:** A Zone is declared failed if its LB heartbeat is older than **5.0 seconds**.
    *   **Action:** The surviving LB initiates a takeover, connecting to the failed Zone's L2 cache and adopting its infrastructure.
