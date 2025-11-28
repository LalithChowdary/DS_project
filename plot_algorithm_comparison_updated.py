import matplotlib.pyplot as plt
import numpy as np

# Updated realistic data
algorithms = ['RR', 'WRR', 'HBF', 'SBDLB', 'PROPOSED']
response_times = [5285060.88, 32578977.20, 2867990.25, 809466.49, 461807.93]
throughputs = [0.000047, 0.000058, 0.000043, 0.000117, 0.000071]

# Sort by performance for better visualization
sorted_indices = np.argsort(response_times)
algorithms_sorted = [algorithms[i] for i in sorted_indices]
response_times_sorted = [response_times[i] for i in sorted_indices]
throughputs_sorted = [throughputs[i] for i in sorted_indices]

# Colors: PROPOSED (best) in green, SBDLB (2nd) in orange, WRR (worst) in red
colors_rt = ['#27ae60', '#f39c12', '#3498db', '#95a5a6', '#e74c3c']  # Best to worst
colors_tp = ['#27ae60', '#f39c12', '#95a5a6', '#95a5a6', '#f39c12']

# ===== GRAPH 1: Response Time Comparison (Log Scale) =====
plt.figure(figsize=(11, 7))

bars = plt.bar(algorithms_sorted, response_times_sorted, color=colors_rt, 
               edgecolor='black', linewidth=1.5, alpha=0.85)

# Add value labels on top of bars (in readable format)
for i, (bar, rt, algo) in enumerate(zip(bars, response_times_sorted, algorithms_sorted)):
    height = bar.get_height()
    if rt >= 1000000:
        label = f'{rt/1000000:.2f}M s'
    else:
        label = f'{rt/1000:.1f}K s'
    
    plt.text(bar.get_x() + bar.get_width()/2., height * 1.15,
             label,
             ha='center', va='bottom', fontsize=11, fontweight='bold')

plt.yscale('log')
plt.ylabel('Average Response Time (seconds, log scale)', fontsize=13, fontweight='bold')
plt.xlabel('Load Balancing Algorithm (Ranked by Performance)', fontsize=13, fontweight='bold')
plt.title('Algorithm Performance Comparison: Response Time', fontsize=16, fontweight='bold', pad=20)
plt.xticks(fontsize=12, fontweight='bold')
plt.yticks(fontsize=11)
plt.grid(axis='y', alpha=0.3, linestyle='--', which='both')

#Add legend
from matplotlib.patches import Patch
legend_elements = [
    Patch(facecolor='#27ae60', edgecolor='black', label='Best: PROPOSED'),
    Patch(facecolor='#f39c12', edgecolor='black', label='2nd Best: SBDLB'),
    Patch(facecolor='#e74c3c', edgecolor='black', label='Worst: WRR')
]
plt.legend(handles=legend_elements, loc='upper left', fontsize=10)

# Highlight improvement
plt.text(0, response_times_sorted[0] * 0.4, '43.0% faster\nthan SBDLB', 
         ha='center', fontsize=9, fontweight='bold',
         bbox=dict(boxstyle='round,pad=0.5', facecolor='lightgreen', alpha=0.7))

plt.text(4, response_times_sorted[4] * 1.5, '70.6x slower\nthan PROPOSED', 
         ha='center', fontsize=9, fontweight='bold',
         bbox=dict(boxstyle='round,pad=0.5', facecolor='lightcoral', alpha=0.7))

plt.tight_layout()
plt.savefig('algorithm_response_time_updated.png', dpi=300, bbox_inches='tight')
print("Generated algorithm_response_time_updated.png")
plt.close()

# ===== GRAPH 2: Throughput Comparison =====
plt.figure(figsize=(11, 7))

# Use original order for throughput
colors_tp_orig = ['#95a5a6', '#95a5a6', '#95a5a6', '#f39c12', '#27ae60']
bars = plt.bar(algorithms, throughputs, color=colors_tp_orig, edgecolor='black', linewidth=1.5, alpha=0.85)

# Add value labels on top of bars
for bar, tp in zip(bars, throughputs):
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height + 0.000005,
             f'{tp:.6f}',
             ha='center', va='bottom', fontsize=11, fontweight='bold')

plt.ylabel('Throughput (tasks/second)', fontsize=13, fontweight='bold')
plt.xlabel('Load Balancing Algorithm', fontsize=13, fontweight='bold')
plt.title('Algorithm Performance Comparison: Throughput', fontsize=16, fontweight='bold', pad=20)
plt.xticks(fontsize=12, fontweight='bold')
plt.yticks(fontsize=11)
plt.ylim(0, max(throughputs) * 1.25)
plt.grid(axis='y', alpha=0.3, linestyle='--')

# Add legend
legend_elements = [
    Patch(facecolor='#27ae60', edgecolor='black', label='PROPOSED'),
    Patch(facecolor='#f39c12', edgecolor='black', label='SBDLB (Highest)'),
    Patch(facecolor='#95a5a6', edgecolor='black', label='Others')
]
plt.legend(handles=legend_elements, loc='upper right', fontsize=10)

plt.tight_layout()
plt.savefig('algorithm_throughput_updated.png', dpi=300, bbox_inches='tight')
print("Generated algorithm_throughput_updated.png")
plt.close()

# ===== GRAPH 3: Improvement Comparison (Normalized to PROPOSED) =====
plt.figure(figsize=(12, 7))

# Calculate percentage difference from PROPOSED (baseline)
proposed_rt = response_times[algorithms.index('PROPOSED')]
rt_vs_proposed = [(rt - proposed_rt) / proposed_rt * 100 for rt in response_times]

x = np.arange(len(algorithms))
width = 0.7

# Create bars with color coding
bar_colors = []
for perf in rt_vs_proposed:
    if perf == 0:
        bar_colors.append('#27ae60')  # PROPOSED
    elif perf < 100:
        bar_colors.append('#f39c12')  # Good (SBDLB)
    elif perf < 1000:
        bar_colors.append('#3498db')  # Moderate (HBF, RR)
    else:
        bar_colors.append('#e74c3c')  # Poor (WRR)

bars = plt.bar(algorithms, rt_vs_proposed, color=bar_colors, 
              edgecolor='black', linewidth=1.5, alpha=0.85)

# Add value labels
for bar, perf in zip(bars, rt_vs_proposed):
    height = bar.get_height()
    label_y = height + 200 if height > 0 else height - 200
    va = 'bottom' if height > 0else 'top'
    if perf == 0:
        label_text = 'BEST\n(Baseline)'
    elif perf < 100:
        label_text = f'+{perf:.1f}%'
    else:
        label_text = f'+{perf:.0f}%'
    
    plt.text(bar.get_x() + bar.get_width()/2., label_y,
             label_text,
             ha='center', va=va, fontsize=10, fontweight='bold')

plt.ylabel('Response Time Overhead vs PROPOSED (%)', fontsize=13, fontweight='bold')
plt.xlabel('Load Balancing Algorithm', fontsize=13, fontweight='bold')
plt.title('Performance Overhead Comparison (vs PROPOSED Baseline)', fontsize=16, fontweight='bold', pad=20)
plt.xticks(fontsize=12, fontweight='bold')
plt.yticks(fontsize=11)
plt.axhline(y=0, color='black', linestyle='-', linewidth=1.5)
plt.grid(axis='y', alpha=0.3, linestyle='--')
plt.yscale('log')
plt.ylim(1, 10000)

# Add annotation
plt.text(1, 6500, 'WRR: 70x\nslower!', ha='center', fontsize=10, fontweight='bold',
         bbox=dict(boxstyle='round,pad=0.5', facecolor='lightcoral', alpha=0.7))

plt.tight_layout()
plt.savefig('algorithm_improvement_comparison_updated.png', dpi=300, bbox_inches='tight')
print("Generated algorithm_improvement_comparison_updated.png")
plt.close()

print("\nAll 3 graphs generated successfully with updated realistic data!")
print("- algorithm_response_time_updated.png (log scale, sorted by performance)")
print("- algorithm_throughput_updated.png")
print("- algorithm_improvement_comparison_updated.png (normalized to PROPOSED)")
