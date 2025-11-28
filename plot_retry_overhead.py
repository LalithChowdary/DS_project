import matplotlib.pyplot as plt
import numpy as np

# Data from the table
scenarios = ['Baseline\n(0 VM)', '1 VM', '4 VM', '8 VM', '12 VM', '16 VM']
retries = [0, 3, 31, 37, 43, 26]
retry_rates = [0, 0.15, 1.55, 1.85, 2.15, 1.30]
response_times = [461808, 464120, 478550, 485670, 492380, 473920]
impact_pct = [0, 0.5, 3.6, 5.2, 6.6, 2.6]

# Create figure with two subplots
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))

# Plot 1: Response Time vs Tasks Retried
ax1.scatter(retries, response_times, s=150, c=['#2ecc71', '#3498db', '#f39c12', '#e74c3c', '#c0392b', '#9b59b6'], 
            alpha=0.7, edgecolors='black', linewidths=1.5, zorder=3)
ax1.plot(retries, response_times, 'k--', alpha=0.3, linewidth=1, zorder=1)

# Add labels for each point
for i, (x, y, label) in enumerate(zip(retries, response_times, scenarios)):
    offset = 8000 if i != 4 else -10000  # Adjust 12 VM label position
    ax1.annotate(label.replace('\n', ' '), (x, y), 
                textcoords="offset points", xytext=(0, offset/1000), 
                ha='center', fontsize=9, fontweight='bold')

ax1.set_xlabel('Number of Tasks Retried', fontsize=12, fontweight='bold')
ax1.set_ylabel('TRUE Response Time (seconds)', fontsize=12, fontweight='bold')
ax1.set_title('Response Time vs Retry Count', fontsize=14, fontweight='bold', pad=20)
ax1.grid(True, alpha=0.3, linestyle='--')
ax1.set_xlim(-2, 48)
ax1.set_ylim(455000, 500000)

# Add trend annotation
ax1.text(21, 496000, 'Higher Retries\n→ Higher Response Time', 
         fontsize=10, ha='center', 
         bbox=dict(boxstyle='round,pad=0.5', facecolor='yellow', alpha=0.3))

# Plot 2: Response Time Overhead (%) vs Retry Rate
ax2.scatter(retry_rates, impact_pct, s=150, c=['#2ecc71', '#3498db', '#f39c12', '#e74c3c', '#c0392b', '#9b59b6'], 
            alpha=0.7, edgecolors='black', linewidths=1.5, zorder=3)
ax2.plot(retry_rates, impact_pct, 'k--', alpha=0.3, linewidth=1, zorder=1)

# Add labels for each point
for i, (x, y, label) in enumerate(zip(retry_rates, impact_pct, scenarios)):
    offset = 0.3 if i != 4 else -0.4  # Adjust 12 VM label position
    ax2.annotate(label.replace('\n', ' '), (x, y), 
                textcoords="offset points", xytext=(0, offset*20), 
                ha='center', fontsize=9, fontweight='bold')

ax2.set_xlabel('Retry Rate (%)', fontsize=12, fontweight='bold')
ax2.set_ylabel('Response Time Overhead (%)', fontsize=12, fontweight='bold')
ax2.set_title('Response Time Overhead vs Retry Rate', fontsize=14, fontweight='bold', pad=20)
ax2.grid(True, alpha=0.3, linestyle='--')
ax2.set_xlim(-0.1, 2.5)
ax2.set_ylim(-0.5, 7.5)

# Add trend line
z = np.polyfit(retry_rates, impact_pct, 1)
p = np.poly1d(z)
x_trend = np.linspace(0, 2.2, 100)
ax2.plot(x_trend, p(x_trend), 'r-', alpha=0.4, linewidth=2, label=f'Linear Trend: y={z[0]:.1f}x+{z[1]:.1f}')
ax2.legend(loc='upper left', fontsize=9)

# Add correlation annotation
ax2.text(1.1, 6.5, 'Linear Correlation:\nMore Retries = Higher Overhead', 
         fontsize=10, ha='center', 
         bbox=dict(boxstyle='round,pad=0.5', facecolor='lightcoral', alpha=0.3))

plt.tight_layout()
plt.savefig('retry_overhead_analysis.png', dpi=300, bbox_inches='tight')
print("Generated retry_overhead_analysis.png")
