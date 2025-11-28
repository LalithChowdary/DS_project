import matplotlib.pyplot as plt
import numpy as np

# Data
scenarios = ['Baseline\n(No Failure)', 'T=1000s\n(850 tasks queued)', 'T=2000s\n(520 tasks queued)', 
             'T=3000s\n(280 tasks queued)', 'T=4000s\n(120 tasks queued)']
response_times = [461808, 509929, 491365, 477972, 468734]
overhead_pct = [0, 10.42, 6.40, 3.50, 1.50]

# Create figure
plt.figure(figsize=(12, 8))

# Define colors - simple gradient
colors = ['#27ae60', '#e74c3c', '#e67e22', '#f39c12', '#f1c40f']

# Create bar chart
x_pos = np.arange(len(scenarios))
bars = plt.bar(x_pos, response_times, color=colors, edgecolor='black', 
               linewidth=2, alpha=0.85, width=0.7)

# Add ONLY response time labels above bars - clean and simple
for i, (bar, rt, oh) in enumerate(zip(bars, response_times, overhead_pct)):
    height = bar.get_height()
    
    # Response time label
    if i == 0:
        label = f'{rt/1000:.1f}K s'
    else:
        label = f'{rt/1000:.1f}K s\n(+{oh:.1f}%)'
    
    plt.text(bar.get_x() + bar.get_width()/2., height + 2000,
             label,
             ha='center', va='bottom', fontsize=12, fontweight='bold')

# Labels and title  
plt.ylabel('Average Response Time (seconds)', fontsize=14, fontweight='bold', labelpad=10)
plt.xlabel('LB2 Failure Scenario', fontsize=14, fontweight='bold', labelpad=10)
plt.title('Response Time Impact of Load Balancer Failure', 
          fontsize=16, fontweight='bold', pad=20)

# X-axis labels
plt.xticks(x_pos, scenarios, fontsize=11)
plt.yticks(fontsize=12)
plt.ylim(450000, 525000)

# Grid
plt.grid(axis='y', alpha=0.3, linestyle='--', linewidth=1)

# Styling
ax = plt.gca()
ax.set_facecolor('#fafafa')
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
ax.spines['left'].set_linewidth(1.5)
ax.spines['bottom'].set_linewidth(1.5)

plt.tight_layout()
plt.savefig('lb_failure_response_time.png', dpi=300, bbox_inches='tight', facecolor='white')
print("✓ Generated clean lb_failure_response_time.png")
