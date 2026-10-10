package dev.richasy.worldreroll;

/** One in-memory attempt. Restarting Minecraft never resumes an old deletion request. */
public final class RerollSession {
    public enum Phase { PREPARING, LOADING, DELETING, COMPLETE, CANCELLED }
    private Phase phase = Phase.PREPARING;
    private String target;

    public Phase phase() { return phase; }
    public void loading(String target) {
        if (phase != Phase.PREPARING) throw new IllegalStateException("Already started");
        this.target = target;
        phase = Phase.LOADING;
    }
    public boolean ready(String active, boolean playable) {
        if (phase != Phase.LOADING || !playable || !target.equals(active)) return false;
        phase = Phase.DELETING;
        return true;
    }
    public void cancel() {
        if (phase == Phase.DELETING || phase == Phase.COMPLETE) return;
        phase = Phase.CANCELLED;
    }
    public void complete() {
        if (phase != Phase.DELETING) throw new IllegalStateException("World has not loaded");
        phase = Phase.COMPLETE;
    }
}
