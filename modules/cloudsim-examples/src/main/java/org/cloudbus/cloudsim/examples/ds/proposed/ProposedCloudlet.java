package org.cloudbus.cloudsim.examples.ds.proposed;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;

/**
 * Extended Cloudlet to support Task Types for MLFQ Scheduling.
 */
public class ProposedCloudlet extends Cloudlet {

    public enum CloudletType {
        TEXT, // High Priority
        IMAGE, // Medium Priority
        REEL // Low Priority
    }

    private CloudletType type;
    private int retryCount;
    private double originalSubmissionTime; // Track first submission time for accurate response time

    public ProposedCloudlet(
            int cloudletId,
            long cloudletLength,
            int pesNumber,
            long cloudletFileSize,
            long cloudletOutputSize,
            UtilizationModel utilizationModelCpu,
            UtilizationModel utilizationModelRam,
            UtilizationModel utilizationModelBw,
            CloudletType type) {

        super(cloudletId, cloudletLength, pesNumber, cloudletFileSize, cloudletOutputSize,
                utilizationModelCpu, utilizationModelRam, utilizationModelBw);

        this.type = type;
        this.retryCount = 0;
        this.originalSubmissionTime = -1; // Will be set on first submission
    }

    public CloudletType getType() {
        return type;
    }

    public void setType(CloudletType type) {
        this.type = type;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public double getOriginalSubmissionTime() {
        return originalSubmissionTime;
    }

    public void setOriginalSubmissionTime(double time) {
        // Only set if not already set (preserve first submission time)
        if (this.originalSubmissionTime < 0) {
            this.originalSubmissionTime = time;
        }
    }

    /**
     * Get the true response time from original submission to final completion
     */
    public double getTrueResponseTime() {
        if (originalSubmissionTime >= 0 && getFinishTime() >= 0) {
            return getFinishTime() - originalSubmissionTime;
        }
        // Fallback to standard calculation
        return getFinishTime() - getSubmissionTime();
    }
}
