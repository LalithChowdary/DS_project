import matplotlib.pyplot as plt
import numpy as np

# Data
failure_times = ['Baseline\n(No Failure)', 'T=1000s\n(850 queued)', 'T=2000s\n(520 queued)', 'T=3000s\n(280 queued)', 'T=4000s\n(120 queued)']
response_times = [461808, 509929, 491365, 477972, 468734]
overhead_pct = [0, 10.42, 6.40, 3.50, 1.50]
tasks_queued = [0, 850, 520, 280, 120]

# ===== GRAPH 1: Response Time Impact =====
fig = plt.figure(figsize=(12, 8))

# Create bar chart for response time
x_pos = np.arange(len(failure_times))
colors = ['#27ae60', '#e74c3c', '#e67e22', '#f39c12', '#f1c40f']
bars = plt.bar(x_pos, response_times, color=colors, edgecolor='black', linewidth=2, alpha=0.9, width=0.6)

# Add value labels on bars
for i, (bar, rt, overhead) in enumerate(zip(bars, response_times, overhead_pct)):
    height = bar.get_height()
    # Response time label
    plt.text(bar.get_x() + bar.get_width()/2., height + 5000,
             f'{rt/1000:.1f}K s',
             ha='center', va='bottom', fontsize=12, fontweight='bold')
    
    # Overhead percentage label
    if overhead > 0:
        plt.text(bar.get_x() + bar.get_width()/2., height/2,
                 f'+{overhead:.2f}%',
                 ha='center', va='center', fontsize=13, fontweight='bold',
                 color='white',
                 bbox=dict(boxstyle='round,pad=0.5', facecolor='black', alpha=0.7))

# Formatting
plt.ylabel('Average Response Time (seconds)', fontsize=14, fontweight='bold', labelpad=10)
plt.xlabel('LB2 Failure Scenario', fontsize=14, fontweight='bold', labelpad=10)
plt.title('Response Time Impact of Load Balancer Failure\n(2000 Tasks Total)', 
          fontsize=16, fontweight='bold', pad=20)

plt.xticks(x_pos, failure_times, fontsize=11, fontweight='bold')
plt.yticks(fontsize=12)
plt.ylim(450000, 520000)

# Add grid
plt.grid(axis='y', alpha=0.25, linestyle='--', linewidth=1)

# Styling
ax = plt.gca()
ax.set_facecolor('#f9f9f9')
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.spines['left'].set_linewidth(1.5)
ax.spines['bottom'].set_linewidth(1.5)

# Add annotation
plt.annotate('Baseline\nNo Failure', 
             xy=(0, response_times[0]), 
             xytext=(0.5, response_times[0] - 8000),
             ha='center',
             fontsize=10,
             fontweight='bold',
             color='#27ae60',
             bbox=dict(boxstyle='round,pad=0.6', facecolor='#d5f4e6', edgecolor='#27ae60', linewidth=2),
             arrowprops=dict(arrowstyle='->', color='#27ae60', lw=2))

plt.annotate('Worst Case\n+10.42% overhead', 
             xy=(1, response_times[1]), 
             xytext=(1.5, response_times[1] + 3000),
             ha='center',
             fontsize=10,
             fontweight='bold',
             color='#e74c3c',
             bbox=dict(boxstyle='round,pad=0.6', facecolor='#fadbd8', edgecolor='#e74c3c', linewidth=2),
             arrowprops=dict(arrowstyle='->', color='#e74c3c', lw=2))

plt.tight_layout()
plt.savefig('lb_failure_response_time.png', dpi=300, bbox_inches='tight', facecolor='white')
print("✓ Generated lb_failure_response_time.png")
plt.close()

# ===== GRAPH 2: Overhead vs Queued Tasks =====
plt.figure(figsize=(11, 8))

# Create scatter plot with connecting line
plt.plot(tasks_queued[1:], overhead_pct[1:], 'o-', color='#e74c3c', markersize=15, 
         linewidth=3, markeredgecolor='black', markeredgewidth=2, label='Overhead')

# Add value labels
for tq, oh in zip(tasks_queued[1:], overhead_pct[1:]):
    plt.annotate(f'{oh:.2f}%', 
                 xy=(tq, oh), 
                 xytext=(0, 15),
                 textcoords='offset points',
                 ha='center',
                 fontsize=12,
                 fontweight='bold',
                 bbox=dict(boxstyle='round,pad=0.5', facecolor='white', edgecolor='gray', linewidth=1.5))

# Formatting
plt.xlabel('Tasks in LB2 Queue at Failure Time', fontsize=14, fontweight='bold', labelpad=10)
plt.ylabel('Response Time Overhead (%)', fontsize=14, fontweight='bold', labelpad=10)
plt.title('Response Time Overhead vs Queued Tasks\n(LB2 Failure Impact)', 
          fontsize=16, fontweight='bold', pad=20)

plt.xticks(fontsize=12)
plt.yticks(fontsize=12)
plt.xlim(0, 900)
plt.ylim(0, 12)

# Add grid
plt.grid(True, alpha=0.25, linestyle='--', linewidth=1)

# Styling
ax = plt.gca()
ax.set_facecolor('#f9f9f9')
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.spines['left'].set_linewidth(1.5)
ax.spines['bottom'].set_linewidth(1.5)

# Add trend annotation
plt.text(650, 9, 'Linear Relationship:\nMore queued tasks\n→ Higher overhead', 
         ha='center',
         fontsize=11,
         fontweight='bold',
         bbox=dict(boxstyle='round,pad=0.7', facecolor='lightyellow', edgecolor='orange', linewidth=2))

plt.tight_layout()
plt.savefig('lb_failure_overhead_vs_queue.png', dpi=300, bbox_inches='tight', facecolor='white')
print("✓ Generated lb_failure_overhead_vs_queue.png")
plt.close()

print("\n✅ Both LB failure graphs generated successfully!")
