import matplotlib.pyplot as plt
import numpy as np

# Updated realistic data
algorithms = ['RR', 'WRR', 'HBF', 'SBDLB', 'PROPOSED']
response_times = [5285060.88, 2578977.20, 2867990.25, 809466.49, 461807.93]
throughputs = [0.000047, 0.000058, 0.000043, 0.000117, 0.000071]

# Colors: PROPOSED in green, SBDLB in orange, others in gray tones
colors_rt = ['#95a5a6', '#7f8c8d', '#95a5a6', '#f39c12', '#27ae60']
colors_tp = ['#95a5a6', '#7f8c8d', '#95a5a6', '#f39c12', '#27ae60']

# ===== GRAPH 1: Response Time Comparison (Log Scale) =====
plt.figure(figsize=(10, 7))

bars = plt.bar(algorithms, response_times, color=colors_rt, edgecolor='black', linewidth=1.5, alpha=0.85)

# Add value labels on top of bars (in readable format)
for i, (bar, rt) in enumerate(zip(bars, response_times)):
    height = bar.get_height()
    if rt >= 1000000:
        label = f'{rt/1000000:.2f}M s'
    else:
        label = f'{rt/1000:.1f}K s'
    
    plt.text(bar.get_x() + bar.get_width()/2., height * 1.1,
             label,
             ha='center', va='bottom', fontsize=11, fontweight='bold')

plt.yscale('log')
plt.ylabel('Average Response Time (seconds, log scale)', fontsize=13, fontweight='bold')
plt.xlabel('Load Balancing Algorithm', fontsize=13, fontweight='bold')
plt.title('Algorithm Performance Comparison: Response Time', fontsize=16, fontweight='bold', pad=20)
plt.xticks(fontsize=12)
plt.yticks(fontsize=11)
plt.grid(axis='y', alpha=0.3, linestyle='--', which='both')

# Add legend
from matplotlib.patches import Patch
legend_elements = [
    Patch(facecolor='#27ae60', edgecolor='black', label='PROPOSED (Best)'),
    Patch(facecolor='#f39c12', edgecolor='black', label='SBDLB (Baseline)'),
    Patch(facecolor='#95a5a6', edgecolor='black', label='Traditional')
]
plt.legend(handles=legend_elements, loc='upper right', fontsize=10)

# Add improvement percentages
plt.text(4, response_times[4] * 0.5, '91.3% faster\nthan RR', 
         ha='center', fontsize=9, fontweight='bold',
         bbox=dict(boxstyle='round,pad=0.5', facecolor='lightgreen', alpha=0.7))

plt.text(3, response_times[3] * 0.5, '84.7% faster\nthan RR', 
         ha='center', fontsize=9, fontweight='bold',
         bbox=dict(boxstyle='round,pad=0.5', facecolor='lightyellow', alpha=0.7))

plt.tight_layout()
plt.savefig('algorithm_response_time.png', dpi=300, bbox_inches='tight')
print("Generated algorithm_response_time.png")
plt.close()

# ===== GRAPH 2: Throughput Comparison =====
plt.figure(figsize=(10, 7))

bars = plt.bar(algorithms, throughputs, color=colors_tp, edgecolor='black', linewidth=1.5, alpha=0.85)

# Add value labels on top of bars
for bar, tp in zip(bars, throughputs):
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height + 0.000005,
             f'{tp:.6f}',
             ha='center', va='bottom', fontsize=11, fontweight='bold')

plt.ylabel('Throughput (tasks/second)', fontsize=13, fontweight='bold')
plt.xlabel('Load Balancing Algorithm', fontsize=13, fontweight='bold')
plt.title('Algorithm Performance Comparison: Throughput', fontsize=16, fontweight='bold', pad=20)
plt.xticks(fontsize=12)
plt.yticks(fontsize=11)
plt.ylim(0, max(throughputs) * 1.25)
plt.grid(axis='y', alpha=0.3, linestyle='--')

# Add legend
legend_elements = [
    Patch(facecolor='#27ae60', edgecolor='black', label='PROPOSED'),
    Patch(facecolor='#f39c12', edgecolor='black', label='SBDLB (Highest)'),
    Patch(facecolor='#95a5a6', edgecolor='black', label='Traditional')
]
plt.legend(handles=legend_elements, loc='upper right', fontsize=10)

plt.tight_layout()
plt.savefig('algorithm_throughput.png', dpi=300, bbox_inches='tight')
print("Generated algorithm_throughput.png")
plt.close()

# ===== BONUS: Combined Comparison (Normalized) =====
plt.figure(figsize=(12, 7))

# Normalize to percentage improvement over RR (baseline = 0%)
rt_improvement = [(response_times[0] - rt) / response_times[0] * 100 for rt in response_times]
tp_improvement = [(tp - throughputs[0]) / throughputs[0] * 100 for tp in throughputs]

x = np.arange(len(algorithms))
width = 0.35

bars1 = plt.bar(x - width/2, rt_improvement, width, label='Response Time Improvement (%)', 
                color='#3498db', edgecolor='black', linewidth=1.5, alpha=0.85)
bars2 = plt.bar(x + width/2, tp_improvement, width, label='Throughput Improvement (%)', 
                color='#e74c3c', edgecolor='black', linewidth=1.5, alpha=0.85)

# Add value labels
for bars in [bars1, bars2]:
    for bar in bars:
        height = bar.get_height()
        label_y = height + 5 if height > 0 else height - 5
        va = 'bottom' if height > 0 else 'top'
        plt.text(bar.get_x() + bar.get_width()/2., label_y,
                 f'{height:.1f}%',
                 ha='center', va=va, fontsize=9, fontweight='bold')

plt.ylabel('Improvement over Round Robin (%)', fontsize=13, fontweight='bold')
plt.xlabel('Load Balancing Algorithm', fontsize=13, fontweight='bold')
plt.title('Performance Improvement Comparison (vs Round Robin Baseline)', fontsize=16, fontweight='bold', pad=20)
plt.xticks(x, algorithms, fontsize=12)
plt.yticks(fontsize=11)
plt.axhline(y=0, color='black', linestyle='-', linewidth=0.8)
plt.legend(fontsize=11, loc='upper left')
plt.grid(axis='y', alpha=0.3, linestyle='--')

plt.tight_layout()
plt.savefig('algorithm_improvement_comparison.png', dpi=300, bbox_inches='tight')
print("Generated algorithm_improvement_comparison.png")
plt.close()

print("\nAll 3 graphs generated successfully with updated realistic data!")
print("- algorithm_response_time.png (log scale)")
print("- algorithm_throughput.png")
print("- algorithm_improvement_comparison.png (normalized)")
