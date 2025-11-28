import matplotlib.pyplot as plt
import numpy as np

# Data
algorithms = ['RR', 'WRR', 'HBF', 'SBDLB', 'PROPOSED']
response_times = [5285060.88, 2578977.20, 2867990.25, 809466.49, 461807.93]
throughputs = [0.000047, 0.000058, 0.000043, 0.000117, 0.000071]

# ===== GRAPH 1: Response Time - Clean Bar Chart =====
plt.figure(figsize=(11, 8))

# Define colors with better contrast
colors = ['#7f8c8d', '#95a5a6', '#bdc3c7', '#f39c12', '#27ae60']
bars = plt.bar(algorithms, response_times, color=colors, edgecolor='black', linewidth=2, alpha=0.9, width=0.6)

# Add value labels above bars
for i, (bar, rt) in enumerate(zip(bars, response_times)):
    height = bar.get_height()
    # Convert to millions or thousands
    if rt >= 1000000:
        label = f'{rt/1000000:.2f}M'
    else:
        label = f'{rt/1000:.0f}K'
    
    # Position label above bar
    y_pos = height * 1.05
    plt.text(bar.get_x() + bar.get_width()/2., y_pos,
             label,
             ha='center', va='bottom', fontsize=13, fontweight='bold')

# Formatting
plt.ylabel('Average Response Time (seconds)', fontsize=14, fontweight='bold', labelpad=10)
plt.xlabel('Load Balancing Algorithm', fontsize=14, fontweight='bold', labelpad=10)
plt.title('Response Time Comparison Across Load Balancing Algorithms', 
          fontsize=16, fontweight='bold', pad=20)

# Use log scale for better visualization
plt.yscale('log')
plt.ylim(bottom=100000, top=10000000)

# Customize ticks
plt.xticks(fontsize=13, fontweight='bold')
plt.yticks(fontsize=12)

# Add grid
plt.grid(axis='y', alpha=0.25, linestyle='--', linewidth=1, which='both')

# Add subtle background
ax = plt.gca()
ax.set_facecolor('#f9f9f9')
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.spines['left'].set_linewidth(1.5)
ax.spines['bottom'].set_linewidth(1.5)

# Add annotation for best performer
plt.annotate('BEST\n91.3% improvement', 
             xy=(4, response_times[4]), 
             xytext=(4, response_times[4] * 3),
             ha='center',
             fontsize=10,
             fontweight='bold',
             color='#27ae60',
             bbox=dict(boxstyle='round,pad=0.6', facecolor='#d5f4e6', edgecolor='#27ae60', linewidth=2),
             arrowprops=dict(arrowstyle='->', color='#27ae60', lw=2))

plt.tight_layout()
plt.savefig('algorithm_response_time.png', dpi=300, bbox_inches='tight', facecolor='white')
print("✓ Generated algorithm_response_time.png")
plt.close()

# ===== GRAPH 2: Throughput - Clean Bar Chart =====
plt.figure(figsize=(11, 8))

bars = plt.bar(algorithms, throughputs, color=colors, edgecolor='black', linewidth=2, alpha=0.9, width=0.6)

# Add value labels above bars
for bar, tp in zip(bars, throughputs):
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height + 0.000006,
             f'{tp:.6f}',
             ha='center', va='bottom', fontsize=12, fontweight='bold')

# Formatting
plt.ylabel('Throughput (tasks/second)', fontsize=14, fontweight='bold', labelpad=10)
plt.xlabel('Load Balancing Algorithm', fontsize=14, fontweight='bold', labelpad=10)
plt.title('Throughput Comparison Across Load Balancing Algorithms', 
          fontsize=16, fontweight='bold', pad=20)

# Set appropriate y-axis limits
plt.ylim(0, max(throughputs) * 1.3)

# Customize ticks
plt.xticks(fontsize=13, fontweight='bold')
plt.yticks(fontsize=12)

# Add grid
plt.grid(axis='y', alpha=0.25, linestyle='--', linewidth=1)

# Add subtle background
ax = plt.gca()
ax.set_facecolor('#f9f9f9')
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.spines['left'].set_linewidth(1.5)
ax.spines['bottom'].set_linewidth(1.5)

# Add annotation for highest throughput
plt.annotate('HIGHEST\n0.000117 tasks/s', 
             xy=(3, throughputs[3]), 
             xytext=(3, throughputs[3] * 0.65),
             ha='center',
             fontsize=10,
             fontweight='bold',
             color='#f39c12',
             bbox=dict(boxstyle='round,pad=0.6', facecolor='#fef5e7', edgecolor='#f39c12', linewidth=2),
             arrowprops=dict(arrowstyle='->', color='#f39c12', lw=2))

plt.tight_layout()
plt.savefig('algorithm_throughput.png', dpi=300, bbox_inches='tight', facecolor='white')
print("✓ Generated algorithm_throughput.png")
plt.close()

print("\n✅ Both graphs regenerated with improved formatting!")
