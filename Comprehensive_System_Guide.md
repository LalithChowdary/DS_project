# Comprehensive System Guide: Hierarchical Distributed Cloud Architecture (Theoretical Framework)

## 1. System Overview
This document outlines the theoretical implementation of a **Multi-Zone Hierarchical Cloud Architecture**. It details the network topology, data structures, and protocols used to ensure high availability and fault tolerance across Availability Zones (AZs).

### 1.1 Network Topology & Connectivity
The system relies on **TCP/IP Sockets** for reliable, bidirectional communication between components.

*   **Global Dispatcher ↔ Zonal LBs:**
    *   **Protocol:** HTTP/REST or gRPC over TCP.
    *   **Function:** Dispatcher sends task payloads to the exposed public IP of the Zonal LB.
*   **Zonal LB ↔ Redis Caches:**
    *   **Protocol:** RESP (Redis Serialization Protocol) over TCP.
    *   **Connections:**
        *   **L1 Connection:** Persistent TCP connection to the Regional Redis Cluster (Virtual IP).
        *   **L2 Connection:** Persistent TCP connection to the Local Zonal Redis instance.
*   **Zonal LB ↔ VMs:**
    *   **Protocol:** Custom Agent Protocol (TCP Socket) or SSH.
    *   **Function:** LB pushes tasks to VMs; VMs push heartbeats/status updates to Redis (not directly to LB to reduce LB load).
*   **LB ↔ LB (Peer Communication):**
    *   **Protocol:** TCP Socket (Heartbeat/Signal) or Indirect via Redis L1.
    *   **Failover:** In case of failure, the Survivor LB establishes a **Direct TCP Socket** connection to the Victim Zone's Redis L2 and Infrastructure Manager.

### 1.2 Service Registry (Metadata Store)
To manage connectivity, a **Service Registry** (implemented as a reserved keyspace in Redis L1 or a separate service like Consul) stores static infrastructure metadata.

*   **Storage Location:** Redis L1 (Key: `System_Registry`)
*   **Data Stored:**
    *   `Zone_1_Config`: `{ "LB_IP": "10.0.1.1", "Redis_L2_IP": "10.0.1.5", "Subnet": "10.0.1.0/24" }`
    *   `Zone_2_Config`: `{ "LB_IP": "10.0.2.1", "Redis_L2_IP": "10.0.2.5", "Subnet": "10.0.2.0/24" }`
    *   `VM_Registry`: `{ "VM_1": "10.0.1.10", "VM_2": "10.0.1.11", ... }`
32: 

### 1.3 Dynamic VM Registration (Self-Discovery)
The system uses a **Push-Based Registration** model for dynamic scalability:

1.  **Bootstrapping:** When a new VM boots (Auto-Scaling or Recovery), its **Cloud Agent** starts automatically.
2.  **Self-Introspection:** The Agent detects its own resources (Available MIPS, RAM, BW) and IP address.
3.  **Registration (The "Hello" Signal):** The VM's software agent connects to the **Level 2 Redis Cache** and creates its own entry:
    *   `HMSET VM_{ID} IP "{IP}" Status "ALIVE" Last_Heartbeat "{Timestamp}"`
    *   *Note: This acts as a "I am here" signal. Real-time resources are NOT stored here.*
4.  **Discovery:** The Zonal LB's background thread scans Redis L2 keys (`VM_*`). When it sees a new key (e.g., `VM_105`), it automatically adds this VM to its **Local VM Registry**, enabling it to start querying that VM for scores.

---

## 2. Redis Data Structures (Memory Layout)

### 2.1 Level 1: Regional Cache (Global State)
**Type:** Redis Cluster (High Availability)
**Access:** All Zonal LBs

| Key | Type | Field | Value | Description |
| :--- | :--- | :--- | :--- | :--- |
| `LB_Status` | **Hash** | `LB_{ID}_Heartbeat` | `Timestamp (long)` | Last keep-alive signal from LB. |
| | | `LB_{ID}_Status` | `"ALIVE" / "DEAD"` | Current operational status. |
| `Task_Queue` | **List** | N/A | `JSON Task Object` | Global queue for unscheduled tasks. |
| `Task_Meta_{ID}` | **Hash** | `Assigned_Zone` | `"Zone_1"` | Zone currently responsible for the task. |
| | | `Status` | `"QUEUED"` | Global status tracking. |

### 2.2 Level 2: Zonal Cache (Local State)
**Type:** Standalone Redis Instance (Per Zone)
**Access:** Local LB (Primary), Peer LB (during Failover)

| Key | Type | Field | Value | Description |
| :--- | :--- | :--- | :--- | :--- |
| `VM_{ID}` | **Hash** | `Status` | `"ALIVE" / "DEAD"` | VM health status. |
| | | `Last_Heartbeat` | `Timestamp (long)` | Last signal from VM Agent. |
| | | `IP_Address` | `"10.0.x.x"` | Private IP of the VM. |
| | | `Task_{ID}` | `"RUNNING" / "FINISHED"` | Status of tasks on this VM. |
| `Zone_Metrics` | **Hash** | `Total_CPU` | `Double` | Aggregated CPU usage of the Zone. |

### 2.2.1 Fault Tolerance
*   **VM Failure:** Detected via Zonal Heartbeats (Redis L2). Auto-replacement triggers.
*   **Zone Failover:** Detected via Zonal Heartbeats (Redis L1). Peer LB takes over the failed Zone.

### 2.3 Health Monitoring Frequency
*   **Monitor Interval:** `3.0 seconds` (LB checks for failures every 3s).
*   **VM Heartbeat Interval:** `5.0 seconds` (VMs send "I am alive" signal).
*   **VM Heartbeat TTL (Timeout):** `10.0 seconds` (VM declared DEAD if no heartbeat for 10s).
*   **LB Heartbeat TTL (Timeout):** `10.0 seconds` (LB declared DEAD if no heartbeat for 10s).
*   **Mechanism:** Periodic background thread in LB queries Redis for `Last_Heartbeat` timestamps.

---

## 3. Detailed Scenarios & Workflows

### Scenario A: Normal Task Execution
**Flow:** Dispatcher -> LB -> VM

1.  **Submission:** Global Dispatcher sends `POST /submit` to **LB1 (IP: 10.0.1.1)**.
2.  **L1 Update:** LB1 opens TCP conn to **Redis L1** and runs:
    *   `HSET Task_Meta_101 Assigned_Zone Zone_1 Status QUEUED`
3.  **Scheduling (SBDLB):**
    *   LB1 retrieves the list of active VMs from its **Local VM Registry** (IPs discovered via Redis).
    *   **Direct Query:** LB1 opens a TCP connection to each VM (e.g., `GET /stats` to 10.0.1.15).
    *   **Real-Time Data:** VM_5 responds with current `{ "MIPS": 500, "RAM": 2048, "BW": 1000 }`.
    *   **Scoring:** LB1 calculates the score using this fresh data.
    *   Result: **VM_5** is selected.
4.  **Execution:** LB1 opens TCP Socket to **VM_5:8080** and sends task payload.
5.  **L2 Update:** LB1 updates **Redis L2 (Zone 1)**:
    *   `HSET VM_5 Task_101 RUNNING`
6.  **Completion:** VM_5 finishes, updates Redis L2 `HSET VM_5 Task_101 FINISHED`, and notifies LB1 via callback or polling.

### Scenario B: VM Failure & Auto-Recovery
**Flow:** VM Dies -> LB Detects -> LB Restarts

**Heartbeat Configuration:**
*   VMs send heartbeats to L2 Cache every **5 seconds**
*   VM declared DEAD if no heartbeat for **10 seconds**
*   LB checks VM health every **3 seconds**

1.  **Failure:** **VM_5** crashes. Its background agent stops sending heartbeats.
2.  **Detection:** **LB1** runs a periodic background thread (every **3s**).
    *   Query: `HGET VM_5 Last_Heartbeat` from **Redis L2**.
    *   Logic: `Current_Time - Last_Heartbeat > 10s`.
    *   Result: **Timeout Detected**.
3.  **Marking Dead:** LB1 updates Redis L2:
    *   `HSET VM_5 Status DEAD`
4.  **Task Rescue:**
    *   LB1 scans Redis L2 for tasks on VM_5: `HGETALL VM_5`.
    *   Retrieved: `Task_101: RUNNING`.
    *   Action: Task 101 is moved to LB1's **High Priority Queue** in memory.
5.  **Restart (Soft Recovery):**
    *   LB1 triggers a **Restart** command for **VM_5**.
    *   **Status Update:** LB1 updates Redis L2: `HSET VM_5 Status RESTARTING`.
    *   **Delay:** The restart process takes **30 seconds** (simulating shutdown + boot time).
    *   **Completion:** After 30s, the restart completes and VM_5 begins its boot sequence.
    *   **First Heartbeat:** VM_5 sends its first heartbeat, which updates Redis L2: `HSET VM_5 Status ALIVE`.
    *   **Availability:** VM_5 is now available for new tasks and continues sending heartbeats every 5s.

### Scenario C: Zone Failover (Critical LB Failure)
**Flow:** LB1 Dies -> LB2 Takes Over Zone 1

**Heartbeat Configuration:**
*   LBs send heartbeats to L1 Cache every **5 seconds**
*   LB declared DEAD if no heartbeat for **10 seconds**
*   LBs check peer health every **3 seconds**

1.  **Failure:** **LB1 (Zone 1)** crashes due to hardware failure. It stops updating `LB_1_Heartbeat` in **Redis L1**.
2.  **Detection:** **LB2 (Zone 2)** runs its health check thread (every **3s**).
    *   Query: `HGET LB_Status LB_1_Heartbeat` from **Redis L1**.
    *   Logic: `Current_Time - Last_Heartbeat > 10s`.
    *   Result: **Peer Failure Detected**.
3.  **Registry Lookup:**
    *   LB2 queries **Redis L1 (System_Registry)** for `Zone_1_Config`.
    *   Retrieved: `{ "Redis_L2_IP": "10.0.1.5", ... }`.
4.  **Connection Establishment:**
    *   LB2 establishes a **Remote TCP Connection** to **Zone 1 Redis L2 (10.0.1.5:6379)**.
    *   *Note: This requires network routing/peering between Zone 1 and Zone 2 subnets.*
5.  **State Synchronization (VM Discovery):**
    *   **Source of Truth:** The **Level 2 Cache** persistently stores the *directory* of all VMs (IDs and IPs).
    *   **Retrieval:** LB2 connects to Zone 1's L2 Cache and retrieves the list of active VM IDs and IPs.
    *   **Registry Update:** LB2 populates its local registry with these Zone 1 VMs.
    *   **Scoring Data:** LB2 does *not* get scores from cache. It now has the IPs to query Zone 1 VMs **directly** via TCP for real-time SBDLB scoring.
    *   LB2 queries **Redis L1** for tasks assigned to LB1 that are still `QUEUED`.
6.  **Unified Management (Multi-Zone Control):**
    *   **Registry Merge:** LB2 adds the discovered Zone 1 VMs to its **Local VM Registry**.
    *   **Expanded Scheduling:** When a new task arrives (or a rescued task is rescheduled), LB2's SBDLB algorithm iterates through the *combined* registry.
    *   **Transparent Access:** The scheduler doesn't distinguish between zones; it just sees a larger pool of VMs (Zone 2 + Zone 1).
    *   LB2 can now route tasks to **VM_5 (Zone 1)** or **VM_105 (Zone 2)** based purely on the highest score.

### Scenario D: Work Stealing (Load Balancing)
**Flow:** LB2 Idle -> Steals from LB1

**Overflow Queue Specification:**
*   **Scope:** Separate queue for **each** Load Balancer (Per-LB).
*   **Purpose:** Backpressure buffer for tasks that cannot be immediately scheduled (e.g., when all VMs are busy).
*   **Size:** Dynamic (Unbounded in simulation, configurable in production).
*   **Isolation:** Distinct from the active MLFQ (High/Medium/Low) to prevent priority inversion.

1.  **Trigger:** LB2's local queues are empty.
2.  **Discovery:** LB2 queries **Redis L1** for `Zone_Metrics`.
    *   Finds: `Zone_1_Load: HIGH`.
3.  **Negotiation:**
    *   LB2 sends a `STEAL_REQUEST` via TCP to **LB1**.
    *   **Target:** LB2 specifically requests tasks from LB1's **Overflow Queue** (Backpressure buffer).
    *   *Note: LB2 does NOT steal from LB1's active MLFQ (High/Medium/Low) to avoid disrupting high-priority tasks.*
4.  **Transfer (Atomic Handoff):**
    *   LB1 **atomically removes** `Task_200` from its Overflow Queue.
    *   *Benefit:* This prevents mutual exclusion issues. Since LB1 controls the removal, no other entity can grab this task simultaneously.
    *   LB1 sends the task object to LB2.
    *   Update: `HSET Task_Meta_200 Assigned_Zone Zone_2`.
5.  **Execution:** LB2 executes Task 200 on a local VM in Zone 2.
