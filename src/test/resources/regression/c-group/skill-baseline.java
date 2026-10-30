// Frozen before C refactoring on 2026-10-22. Test oracle, not production code.
class SkillBaseline {
    public static final int PHASE_1 = 1;

    public static final int PHASE_2 = 2;

    public static final int PHASE_3 = 3;

    public static final int PHASE_4 = 4;

    public static final int SKILL_NONE = 0;

    public static final int SKILL_WORLD_REND = 1;

    public static final int SKILL_APOCALYPSE_CRACK = 2;

    public static final int SKILL_WORLD_PEEL = 3;

    public static final int SKILL_WORLD_COLLAPSE = 4;

    public static final int SKILL_SCYTHE_ICHIMONJI = 5;

    public static final int SKILL_SCYTHE_REVERSE_MOON = 6;

    public static final int SKILL_SCYTHE_EXECUTION = 7;

    public static final int SKILL_DESTRUCTION_LIGHTNING = 8;

    public static final int SKILL_DESTRUCTION_GOD_ORB = 9;

    public static final int SKILL_THUNDER_SKYNET = 10;

    public static final int SKILL_SCYTHE_FAULT_SPLIT = 11;

    public static final int GLOBAL_SKILL_INTERVAL_TICKS = 28;

    private int worldRendCooldown;

    private int apocalypseCrackCooldown;

    private int worldPeelCooldown;

    private int worldCollapseCooldown;

    private int scytheIchimonjiCooldown;

    private int scytheReverseMoonCooldown;

    private int scytheExecutionCooldown;

    private int scytheFaultSplitCooldown;

    private int destructionLightningCooldown;

    private int destructionGodOrbCooldown;

    private int thunderSkyNetCooldown;

    private int globalSkillIntervalTicks;

    private void tickCooldowns() {
        if (this.worldRendCooldown > 0) this.worldRendCooldown--;
        if (this.apocalypseCrackCooldown > 0) this.apocalypseCrackCooldown--;
        if (this.worldPeelCooldown > 0) this.worldPeelCooldown--;
        if (this.worldCollapseCooldown > 0) this.worldCollapseCooldown--;
        if (this.scytheIchimonjiCooldown > 0) this.scytheIchimonjiCooldown--;
        if (this.scytheReverseMoonCooldown > 0) this.scytheReverseMoonCooldown--;
        if (this.scytheExecutionCooldown > 0) this.scytheExecutionCooldown--;
        if (this.scytheFaultSplitCooldown > 0) this.scytheFaultSplitCooldown--;
        if (this.destructionLightningCooldown > 0) this.destructionLightningCooldown--;
        if (this.destructionGodOrbCooldown > 0) this.destructionGodOrbCooldown--;
        if (this.thunderSkyNetCooldown > 0) this.thunderSkyNetCooldown--;
        if (this.globalSkillIntervalTicks > 0) this.globalSkillIntervalTicks--;
    }

    public boolean hasGlobalSkillInterval() {
        return this.globalSkillIntervalTicks > 0;
    }

    public int getSkillDuration() {
        if (this.getSkillState() == SKILL_THUNDER_SKYNET) {
            return getThunderSkyNetDuration(this.getBossPhase());
        }
        return getSkillDuration(this.getSkillState());
    }

    public int getSkillTriggerTick() {
        if (this.getSkillState() == SKILL_THUNDER_SKYNET) {
            return 36;
        }
        if (this.getSkillState() == SKILL_DESTRUCTION_GOD_ORB) {
            return 28;
        }
        return switch (this.getSkillState()) {
            case SKILL_SCYTHE_ICHIMONJI -> 14;
            case SKILL_SCYTHE_REVERSE_MOON -> 16;
            case SKILL_SCYTHE_EXECUTION -> 24;
            case SKILL_DESTRUCTION_LIGHTNING -> 22;
            case SKILL_SCYTHE_FAULT_SPLIT -> 120;
            default -> 0;
        };
    }

    public static int getSkillDuration(int skill) {
        return switch (skill) {
            case SKILL_WORLD_REND -> 58;
            case SKILL_APOCALYPSE_CRACK -> 40;
            case SKILL_WORLD_PEEL -> 46;
            case SKILL_WORLD_COLLAPSE -> 84;
            case SKILL_SCYTHE_ICHIMONJI -> 34;
            case SKILL_SCYTHE_REVERSE_MOON -> 38;
            case SKILL_SCYTHE_EXECUTION -> 52;
            case SKILL_DESTRUCTION_LIGHTNING -> 68;
            case SKILL_DESTRUCTION_GOD_ORB -> 320;
            case SKILL_THUNDER_SKYNET -> 200; // Handled dynamically in getSkillDuration()
            case SKILL_SCYTHE_FAULT_SPLIT -> 280;
            default -> 0;
        };
    }

    public static int getThunderSkyNetDuration(int phase) {
        int normalizedPhase = Mth.clamp(phase, PHASE_1, PHASE_4);
        int netDuration = 162 + normalizedPhase * 14;
        int pillarLeadTicks = 22;
        return getSkillTriggerTick(SKILL_THUNDER_SKYNET) + pillarLeadTicks + netDuration + 8;
    }

    public static int getSkillTriggerTick(int skill) {
        return switch (skill) {
            case SKILL_WORLD_REND -> 30;
            case SKILL_APOCALYPSE_CRACK -> 18;
            case SKILL_WORLD_PEEL -> 22;
            case SKILL_WORLD_COLLAPSE -> 30;
            case SKILL_SCYTHE_ICHIMONJI -> 11;
            case SKILL_SCYTHE_REVERSE_MOON -> 14;
            case SKILL_SCYTHE_EXECUTION -> 28;
            case SKILL_SCYTHE_FAULT_SPLIT -> 120;
            case SKILL_DESTRUCTION_LIGHTNING -> 20;
            case SKILL_DESTRUCTION_GOD_ORB -> 42;
            case SKILL_THUNDER_SKYNET -> 28;
            default -> 0;
        };
    }

    public boolean isSkillReady(int skill) {
        if (this.hasGlobalSkillInterval()) {
            return false;
        }
        return switch (skill) {
            case SKILL_WORLD_REND -> this.worldRendCooldown <= 0;
            case SKILL_APOCALYPSE_CRACK -> this.apocalypseCrackCooldown <= 0;
            case SKILL_WORLD_PEEL -> this.worldPeelCooldown <= 0;
            case SKILL_WORLD_COLLAPSE -> this.worldCollapseCooldown <= 0;
            case SKILL_SCYTHE_ICHIMONJI -> this.scytheIchimonjiCooldown <= 0;
            case SKILL_SCYTHE_REVERSE_MOON -> this.scytheReverseMoonCooldown <= 0;
            case SKILL_SCYTHE_EXECUTION -> this.scytheExecutionCooldown <= 0;
            case SKILL_SCYTHE_FAULT_SPLIT -> this.scytheFaultSplitCooldown <= 0;
            case SKILL_DESTRUCTION_LIGHTNING -> this.destructionLightningCooldown <= 0;
            case SKILL_DESTRUCTION_GOD_ORB -> this.destructionGodOrbCooldown <= 0;
            case SKILL_THUNDER_SKYNET -> this.thunderSkyNetCooldown <= 0;
            default -> false;
        };
    }

    public void setSkillCooldown(int skill, int value) {
        switch (skill) {
            case SKILL_WORLD_REND -> this.worldRendCooldown = value;
            case SKILL_APOCALYPSE_CRACK -> this.apocalypseCrackCooldown = value;
            case SKILL_WORLD_PEEL -> this.worldPeelCooldown = value;
            case SKILL_WORLD_COLLAPSE -> this.worldCollapseCooldown = value;
            case SKILL_SCYTHE_ICHIMONJI -> this.scytheIchimonjiCooldown = value;
            case SKILL_SCYTHE_REVERSE_MOON -> this.scytheReverseMoonCooldown = value;
            case SKILL_SCYTHE_EXECUTION -> this.scytheExecutionCooldown = value;
            case SKILL_SCYTHE_FAULT_SPLIT -> this.scytheFaultSplitCooldown = value;
            case SKILL_DESTRUCTION_LIGHTNING -> this.destructionLightningCooldown = value;
            case SKILL_DESTRUCTION_GOD_ORB -> this.destructionGodOrbCooldown = value;
            case SKILL_THUNDER_SKYNET -> this.thunderSkyNetCooldown = value;
            default -> {
            }
        }
    }

    private int getBaseCooldown(int skill) {
        return switch (skill) {
            case SKILL_WORLD_REND -> 150;
            case SKILL_APOCALYPSE_CRACK -> 90;
            case SKILL_WORLD_PEEL -> 120;
            case SKILL_WORLD_COLLAPSE -> 220;
            case SKILL_SCYTHE_ICHIMONJI -> 26;
            case SKILL_SCYTHE_REVERSE_MOON -> 44;
            case SKILL_SCYTHE_EXECUTION -> 110;
            case SKILL_SCYTHE_FAULT_SPLIT -> 150;
            case SKILL_DESTRUCTION_LIGHTNING -> 150;
            case SKILL_DESTRUCTION_GOD_ORB -> 150;
            case SKILL_THUNDER_SKYNET -> 150;
            default -> 20;
        };
    }

    public boolean isScytheSkill(int skill) {
        return skill == SKILL_SCYTHE_ICHIMONJI
                || skill == SKILL_SCYTHE_REVERSE_MOON
                || skill == SKILL_SCYTHE_EXECUTION
                || skill == SKILL_SCYTHE_FAULT_SPLIT;
    }
}
