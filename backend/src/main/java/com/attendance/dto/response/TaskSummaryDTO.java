package com.attendance.dto.response;

/**
 * Authoritative task counts per status for list UIs (mini-program, PC).
 */
public class TaskSummaryDTO {

    private long processing;
    /** DB status {@code processed} — awaiting human review */
    private long review;
    private long confirmed;
    private long failed;
    private long cancelled;
    private long total;
    /** True when current user is admin and counts include all users' tasks */
    private boolean allUsersScope;

    public long getProcessing() {
        return processing;
    }

    public void setProcessing(long processing) {
        this.processing = processing;
    }

    public long getReview() {
        return review;
    }

    public void setReview(long review) {
        this.review = review;
    }

    public long getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(long confirmed) {
        this.confirmed = confirmed;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getCancelled() {
        return cancelled;
    }

    public void setCancelled(long cancelled) {
        this.cancelled = cancelled;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public boolean isAllUsersScope() {
        return allUsersScope;
    }

    public void setAllUsersScope(boolean allUsersScope) {
        this.allUsersScope = allUsersScope;
    }
}
