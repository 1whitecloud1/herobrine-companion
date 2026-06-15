package com.whitecloud233.modid.herobrine_companion.entity.awakened;

import java.util.List;

public enum AwakenedMobPeerScene {
    SAME,
    CASUAL,
    COLLAB,
    GOSSIP,
    CONFLICT,
    SCUFFLE,
    AUTHORITY;

    public List<String> keys(AwakenedMobProfile profile) {
        return switch (this) {
            case SAME -> profile.peerSameFamilyKeys();
            case CASUAL -> profile.peerCasualKeys();
            case COLLAB -> profile.peerCollabKeys();
            case GOSSIP -> profile.peerGossipKeys();
            case CONFLICT -> profile.peerConflictKeys();
            case SCUFFLE -> profile.peerScuffleKeys();
            case AUTHORITY -> profile.peerAuthorityKeys();
        };
    }
}
