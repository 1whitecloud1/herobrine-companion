package com.whitecloud233.herobrine_companion.config;

import java.util.Objects;

public final class ServerConfigService {
    private ServerConfigService() {
    }

    public record Result(ServerConfigSnapshot effective, boolean authorized, boolean changed) { }

    public interface Store {
        ServerConfigSnapshot read();
        void save(ServerConfigSnapshot snapshot);
    }

    public static boolean canEdit(boolean singleplayerOwner, int permissionLevel) {
        return singleplayerOwner || permissionLevel >= 2;
    }

    public static Result update(Store store, ServerConfigUpdate update, boolean singleplayerOwner, int permissionLevel) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(update, "update");
        ServerConfigSnapshot current = store.read();
        if (!canEdit(singleplayerOwner, permissionLevel)) return new Result(current, false, false);
        ServerConfigSnapshot desired = update.applyTo(current);
        boolean changed = !desired.equals(current);
        if (changed) store.save(desired);
        return new Result(store.read(), true, changed);
    }
}
