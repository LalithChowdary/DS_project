# Final System Architecture Plan: Dual-LB with Two-Level Caching

This document outlines the implementation plan for the **Distributed Cloud System** based on the user's final specifications.

## 1. System Overview

The system is a **Hierarchical Distributed Architecture** designed for high scalability and fault tolerance.

*   **Core Components:** 2 Load Balancers (LB), 8 Datacenters (DC), Redis-based Caching Layer.
*   **Workload:** Heterogeneous tasks (Text, Image, Reel).
*   **Scheduling:** Multi-Level Feedback Queue (MLFQ) with Priority.

---

## 2. Component Architecture

### A. Load Balancers (The Brains)
*   **Quantity:** 2 (LB1, LB2).
*   **Responsibility:** Each LB manages **4 Datacenters** exclusively.
    *   LB1 $\rightarrow$ DC 1-4
    *   LB2 $\rightarrow$ DC 5-8
*   **Internal Scheduling:** **MLFQ (Multi-Level Feedback Queue)**
    *   Each LB has 3 internal Priority Queues:
        1.  **High Priority:** Text Tasks
        2.  **Medium Priority:** Image Tasks
        3.  **Low Priority:** Reel Tasks
    *   **Aging Mechanism:** To prevent starvation, "Reel" tasks waiting > 5 seconds are promoted to the Medium queue.
    *   **Isolation:** LB1 and LB2 have completely separate queues. They never block each other.

### B. Infrastructure (The Muscle)
*   **Datacenters:** 8 Total.
*   **Hosts per DC:** 4 Physical Machines.
*   **VMs per DC:** ~18 VMs.
*   **VM Configuration:** 3 Types (Low, Medium, High) mixed across hosts.

---

## 3. Data Architecture: The Two-Level Cache

The system uses **Redis** to manage state, split into two logical levels.

### Level 1 Cache: Global Task Tracker
*   **Scope:** Shared between LB1 and LB2.
*   **Purpose:** The "Entry Point" for all tasks.
*   **Data Model:**
    ```json
    "Task_101": { "status": "QUEUED", "assigned_lb": "LB1", "retries": 0 }
    "Cache_A_Config": { "ip": "10.0.0.5", "port": 6379 }  // NEW: Connection info for L2 Cache
    "Cache_B_Config": { "ip": "10.0.0.6", "port": 6379 }
    ```

### Level 2 Cache: Local Execution State
*   **Scope:** Split.
    *   **Cache A:** Dedicated to LB1 $\leftrightarrow$ DCs 1-4.
    *   **Cache B:** Dedicated to LB2 $\leftrightarrow$ DCs 5-8.
*   **Purpose:** Real-time monitoring of VMs and Task execution.
*   **Data Model (Simplified):**
    ```text
    VM_1 (Key)
     ├── Connection: { "ip": "192.168.1.10", "port": 8080 } // NEW: Socket Info
     ├── Status: "ALIVE" | "DEAD"
     ├── Last_Heartbeat: <Timestamp> (TTL: 2s)
     └── Active_Tasks: ["101", "103", "105"]
    ```

---

## 4. Operational Workflows

### Scenario C: LB Failover (The "Takeover")
*   **Scenario:** LB1 crashes.
*   **Step 1:** LB2 detects LB1 is dead (via Level 1 Heartbeat).
*   **Step 2:** LB2 reads `Cache_A_Config` from **Level 1 Cache**.
*   **Step 3:** LB2 connects to **Cache A**.
*   **Step 4:** LB2 retrieves the **IP/Socket List** of all VMs (VM1-VM72) from Cache A.
*   **Step 5:** LB2 opens sockets to all these VMs.
*   **Result:** LB2 now manages **DC 1-8** (All 8 Datacenters).

---

## 5. Implementation Analysis

### Feasibility in CloudSim
*   **Redis Simulation:** We will create a `RedisMock` class that stores `Map<String, Map<String, String>>` to simulate the hierarchical data.
*   **Event-Driven:** We will use `CloudSim.send()` to send immediate events instead of scheduling future polls.
*   **Heartbeats:** We will simulate heartbeats by having VMs update a `lastUpdated` timestamp variable.
*   **MLFQ:** We will replace the standard `List<Cloudlet>` in the Broker with `List<List<Cloudlet>>` (3 lists).

---

## 6. Advanced Features (Research Level)

1.  **Inter-LB "Work Stealing":**
    *   *Logic:* If LB2 is idle, it checks LB1's **Overflow Queue**.
    *   *Action:* LB2 "steals" a batch of tasks from LB1 and executes them on its own DCs.
    *   *Benefit:* Maximizes global resource utilization.

2.  **Geo-Latency Simulation:**
    *   *Logic:* Assign "Regions" (e.g., LB1 in US-East, LB2 in Asia-Pacific).
    *   *Action:* Add simulated network delay (latency) based on user location.
    *   *Benefit:* Realistic simulation for global apps.

3.  **Circuit Breaker Pattern:**
    *   If a Datacenter fails > 5 tasks in 1 minute, stop sending tasks there for 5 minutes.

4.  **Idempotency Keys:**
    *   Assign a unique UUID to every task execution attempt.

5.  **VM Auto-Replacement (Elasticity):**
    *   If a VM is marked **DEAD**, automatically delete and recreate it.

6.  **Overflow Queue (Backpressure):**
    *   Store excess tasks in a separate queue instead of dropping them.
