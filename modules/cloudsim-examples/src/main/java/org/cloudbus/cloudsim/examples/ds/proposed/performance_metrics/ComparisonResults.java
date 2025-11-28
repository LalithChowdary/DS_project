package org.cloudbus.cloudsim.examples.ds.proposed.performance_metrics;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to store and print comparison results.
 */
public class ComparisonResults {

    private static final DecimalFormat dft = new DecimalFormat("###.##");
    private List<AlgorithmResult> results;

    public ComparisonResults() {
        this.results = new ArrayList<>();
    }

    public void addResult(String algorithmName, List<Cloudlet> list) {
        double totalTime = 0;
        double totalLength = 0;
        double maxFinishTime = 0;
        int successCount = 0;

        for (Cloudlet c : list) {
            if (c.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                successCount++;
                double time = c.getFinishTime() - c.getSubmissionTime();
                totalTime += time;
                totalLength += c.getCloudletLength();
                if (c.getFinishTime() > maxFinishTime) {
                    maxFinishTime = c.getFinishTime();
                }
            }
        }

        double avgResponseTime = (successCount > 0) ? totalTime / successCount : 0;
        double throughput = (maxFinishTime > 0) ? successCount / maxFinishTime : 0; // Tasks per second (simulation
                                                                                    // time)

        results.add(new AlgorithmResult(algorithmName, avgResponseTime, throughput, successCount));
    }

    public void printComparison() {
        Log.printLine("\n==========================================================");
        Log.printLine("           Load Balancing Algorithm Comparison Results");
        Log.printLine("==========================================================");
        Log.printLine(
                String.format("%-25s | %-15s | %-15s | %-10s", "Algorithm", "Avg Resp Time", "Throughput", "Success"));
        Log.printLine("----------------------------------------------------------");

        for (AlgorithmResult r : results) {
            Log.printLine(String.format("%-25s | %-15s | %-15s | %-10d",
                    r.name,
                    dft.format(r.avgResponseTime),
                    dft.format(r.throughput),
                    r.successCount));
        }
        Log.printLine("==========================================================\n");
    }

    private static class AlgorithmResult {
        String name;
        double avgResponseTime;
        double throughput;
        int successCount;

        public AlgorithmResult(String name, double avgResponseTime, double throughput, int successCount) {
            this.name = name;
            this.avgResponseTime = avgResponseTime;
            this.throughput = throughput;
            this.successCount = successCount;
        }
    }

    public double getAvgResponseTime() {
        if (results.isEmpty())
            return 0;
        return results.get(results.size() - 1).avgResponseTime;
    }

    public double getThroughput() {
        if (results.isEmpty())
            return 0;
        return results.get(results.size() - 1).throughput;
    }

    public int getSuccessCount() {
        if (results.isEmpty())
            return 0;
        return results.get(results.size() - 1).successCount;
    }

    public void calculateMetrics(String algorithmName, List<Cloudlet> list) {
        addResult(algorithmName, list);
    }
}
