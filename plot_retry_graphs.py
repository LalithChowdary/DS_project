import matplotlib.pyplot as plt
import numpy as np

# Data from the table
scenarios = ['Baseline', '1 VM Failure', '4 VM Failures', '8 VM Failures', '12 VM Failures', '16 VM Failures']
retries = [0, 3, 31, 37, 43, 26]
retry_rates = [0, 0.15, 1.55, 1.85, 2.15, 1.30]
response_times = [461808, 464120, 478550, 485670, 492380, 473920]
impact_pct = [0, 0.5, 3.6, 5.2, 6.6, 2.6]

# ===== GRAPH 1: Response Time vs Tasks Retried =====
plt.figure(figsize=(10, 7))

# Create bar chart
colors = ['#3498db' if i != 4 else '#e74c3c' for i in range(len(retries))]
bars = plt.bar(range(len(retries)), response_times, color=colors, alpha=0.8, edgecolor='black', linewidth=1.5)

# Add value labels on top of bars
for i, (bar, rt) in enumerate(zip(bars, response_times)):
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height + 1500,
             f'{rt:,}s',
             ha='center', va='bottom', fontsize=11, fontweight='bold')

# Customize plot
plt.xlabel('Failure Scenario (Number of Tasks Retried)', fontsize=13, fontweight='bold')
plt.ylabel('Response Time (seconds)', fontsize=13, fontweight='bold')
plt.title('Response Time vs Retry Count', fontsize=16, fontweight='bold', pad=20)
plt.xticks(range(len(retries)), [f'{s}\n({r} retries)' for s, r in zip(scenarios, retries)], 
           fontsize=10, rotation=0)
plt.yticks(fontsize=11)
plt.ylim(455000, 500000)
plt.grid(axis='y', alpha=0.3, linestyle='--')

# Add legend explaining colors
from matplotlib.patches import Patch
legend_elements = [
    Patch(facecolor='#3498db', edgecolor='black', label='Normal'),
    Patch(facecolor='#e74c3c', edgecolor='black', label='Highest Retry Count')
]
plt.legend(handles=legend_elements, loc='upper left', fontsize=10)

plt.tight_layout()
plt.savefig('retry_response_time.png', dpi=300, bbox_inches='tight')
print("Generated retry_response_time.png")
plt.close()

# ===== GRAPH 2: Response Time Overhead vs Retry Rate =====
plt.figure(figsize=(10, 7))

# Create line plot with markers
plt.plot(retry_rates, impact_pct, 'o-', color='#2ecc71', markersize=12, 
         linewidth=2.5, markeredgecolor='black', markeredgewidth=1.5, label='Actual Overhead')

# Highlight the maximum point
max_idx = impact_pct.index(max(impact_pct))
plt.plot(retry_rates[max_idx], impact_pct[max_idx], 'o', color='#e74c3c', 
         markersize=15, markeredgecolor='black', markeredgewidth=2, label='Maximum (12 VM Failures)', zorder=3)

# Add value labels for each point
for x, y, scenario in zip(retry_rates, impact_pct, scenarios):
    offset = 0.35 if y != max(impact_pct) else -0.5
    plt.annotate(f'{y}%\n{scenario}', (x, y), 
                textcoords="offset points", xytext=(0, offset*50), 
                ha='center', fontsize=9, fontweight='bold',
                bbox=dict(boxstyle='round,pad=0.4', facecolor='white', edgecolor='gray', alpha=0.8))

# Add trend line
z = np.polyfit(retry_rates, impact_pct, 1)
p = np.poly1d(z)
x_trend = np.linspace(0, max(retry_rates)*1.1, 100)
plt.plot(x_trend, p(x_trend), '--', color='gray', alpha=0.5, linewidth=2, 
         label=f'Linear Trend (slope ≈ {z[0]:.1f})')

# Customize plot
plt.xlabel('Retry Rate (%)', fontsize=13, fontweight='bold')
plt.ylabel('Response Time Overhead (%)', fontsize=13, fontweight='bold')
plt.title('Response Time Overhead vs Retry Rate', fontsize=16, fontweight='bold', pad=20)
plt.xticks(fontsize=11)
plt.yticks(fontsize=11)
plt.xlim(-0.1, max(retry_rates)*1.15)
plt.ylim(-0.5, max(impact_pct)*1.25)
plt.grid(True, alpha=0.3, linestyle='--')
plt.legend(loc='upper left', fontsize=10)

plt.tight_layout()
plt.savefig('retry_overhead_percentage.png', dpi=300, bbox_inches='tight')
print("Generated retry_overhead_percentage.png")
plt.close()

print("\nBoth graphs generated successfully!")
