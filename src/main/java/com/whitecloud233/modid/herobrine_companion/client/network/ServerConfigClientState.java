package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.config.ServerConfigSnapshot;

/** 只在客户端主线程使用；连接身份校验由外层完成。 */
public final class ServerConfigClientState {
    public static final long REQUEST_TIMEOUT_NANOS = 10_000_000_000L;
    public enum Status { EMPTY, PENDING, CONFIRMED, READ_ONLY, REJECTED, TIMED_OUT }

    private ServerConfigSnapshot snapshot;
    private boolean canEdit;
    private int pendingId;
    private long pendingSince;
    private Status status = Status.EMPTY;
    private long revision;

    public ServerConfigSnapshot snapshot() { return snapshot; }
    public boolean editable() { return snapshot != null && canEdit && pendingId == 0; }
    public Status status() { return pendingId != 0 ? Status.PENDING : status; }
    public long revision() { return revision; }

    public void request(int requestId, long now) {
        if (requestId <= 0) throw new IllegalArgumentException("Request id 0 is reserved for broadcasts");
        pendingId = requestId;
        pendingSince = now;
        revision++;
    }

    public boolean accept(int requestId, ServerConfigSnapshot value, boolean permitted, boolean accepted) {
        if (requestId != 0 && requestId != pendingId) return false;
        snapshot = java.util.Objects.requireNonNull(value);
        canEdit = permitted;
        if (requestId == pendingId) pendingId = 0;
        status = accepted ? (canEdit ? Status.CONFIRMED : Status.READ_ONLY) : Status.REJECTED;
        revision++;
        return true;
    }

    public void expire(long now) {
        if (pendingId != 0 && now - pendingSince >= REQUEST_TIMEOUT_NANOS) {
            pendingId = 0;
            status = Status.TIMED_OUT;
            revision++;
        }
    }

    public void clear() {
        snapshot = null;
        canEdit = false;
        pendingId = 0;
        status = Status.EMPTY;
        revision++;
    }
}
