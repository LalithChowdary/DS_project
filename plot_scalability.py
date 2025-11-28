import matplotlib.pyplot as plt
import csv

# Data
configurations = ['2 DCs\n(20 VMs)', '4 DCs\n(40 VMs)', '8 DCs\n(80 VMs)']
response_times = [129.23, 140.96, 138.77]
throughputs = [0.44, 0.66, 1.32]

# 1. Average Response Time Graph
plt.figure(figsize=(8, 6))
bars = plt.bar(configurations, response_times, color=['#3498db', '#2980b9', '#1f618d'], width=0.5)
plt.title('Average Response Time vs. Datacenter Configuration', fontsize=14, pad=20)
plt.xlabel('Configuration', fontsize=12)
plt.ylabel('Average Response Time (seconds)', fontsize=12)
plt.grid(axis='y', linestyle='--', alpha=0.7)
plt.ylim(0, 160)

# Add value labels on top of bars
for bar in bars:
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height + 2,
             f'{height:.2f}s',
             ha='center', va='bottom', fontsize=10, fontweight='bold')

plt.tight_layout()
plt.savefig('scalability_response_time.png', dpi=300)
print("Generated scalability_response_time.png")

# 2. Throughput Graph
plt.figure(figsize=(8, 6))
bars = plt.bar(configurations, throughputs, color=['#2ecc71', '#27ae60', '#1e8449'], width=0.5)
plt.title('System Throughput vs. Datacenter Configuration', fontsize=14, pad=20)
plt.xlabel('Configuration', fontsize=12)
plt.ylabel('Throughput (tasks/second)', fontsize=12)
plt.grid(axis='y', linestyle='--', alpha=0.7)
plt.ylim(0, 1.5)

# Add value labels on top of bars
for bar in bars:
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height + 0.02,
             f'{height:.2f}',
             ha='center', va='bottom', fontsize=10, fontweight='bold')

plt.tight_layout()
plt.savefig('scalability_throughput.png', dpi=300)
print("Generated scalability_throughput.png")
