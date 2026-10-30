package com.whitecloud233.modid.herobrine_companion.entity.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 组合入口显式提供有序后端，首个匹配者负责整次操作，不按类名自动排序。 */
public final class CookingBackendRegistry {
    private final List<CookingBackend> backends;

    public CookingBackendRegistry(List<CookingBackend> backends) {
        this.backends = List.copyOf(backends);
        Set<String> ids = new HashSet<>();
        for (CookingBackend backend : this.backends) {
            if (backend.id() == null || backend.id().isBlank() || !ids.add(backend.id())) {
                throw new IllegalArgumentException("Invalid or duplicate cooking backend id");
            }
        }
    }

    public Optional<CookingBackend> find(Level level, BlockPos pos) {
        if (level == null || pos == null) return Optional.empty();
        return firstMatching(backend -> backend.supports(level, pos));
    }

    Optional<CookingBackend> firstMatching(java.util.function.Predicate<CookingBackend> supports) {
        return backends.stream().filter(supports).findFirst();
    }

    public Optional<CookingBackend> byId(String id) {
        return backends.stream().filter(backend -> backend.id().equals(id)).findFirst();
    }

    public List<CookingBackend> all() { return backends; }
}
