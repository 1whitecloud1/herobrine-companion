package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ai.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class HeroCrossChatManager {
    public static final HeroCrossChatManager INSTANCE = new HeroCrossChatManager();

    private static final long REQUEST_TIMEOUT_MS = 3 * 60 * 1000L;
    private static final long JOB_TIMEOUT_MS = 90 * 1000L;
    private static final int MAX_MESSAGE_LENGTH = 320;
    private static final String HISTORY_KIND_PLAYER = "player";
    private static final String HISTORY_KIND_HB = "hb";

    private final Map<UUID, Map<UUID, PendingRequest>> pendingByTarget = new HashMap<>();
    private final Map<UUID, ActiveSession> sessionsById = new HashMap<>();
    private final Map<UUID, UUID> activeSessionByPlayer = new HashMap<>();
    private final Map<UUID, GenerationJob> jobsById = new HashMap<>();

    private HeroCrossChatManager() {
    }

    public synchronized void requestSession(ServerPlayer requester, ServerPlayer target) {
        pruneExpiredEntries();
        if (requester == null || target == null) {
            return;
        }
        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_self"));
            return;
        }
        if (!hasBoundHero(requester)) {
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_local_hero_requester"));
            return;
        }
        if (!hasBoundHero(target)) {
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.target_no_hero", target.getGameProfile().getName()));
            return;
        }
        if (!allowsIncomingRequests(target)) {
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.target_disallow_incoming", target.getGameProfile().getName()));
            return;
        }
        if (activeSessionByPlayer.containsKey(requester.getUUID())) {
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.requester_already_active"));
            return;
        }
        if (activeSessionByPlayer.containsKey(target.getUUID())) {
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.target_already_active", target.getGameProfile().getName()));
            return;
        }

        Map<UUID, PendingRequest> requests = pendingByTarget.computeIfAbsent(target.getUUID(), ignored -> new HashMap<>());
        PendingRequest existing = requests.get(requester.getUUID());
        if (existing != null && !existing.isExpired()) {
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_already_pending"));
            return;
        }

        PendingRequest request = new PendingRequest(requester.getUUID(), target.getUUID(), System.currentTimeMillis());
        requests.put(requester.getUUID(), request);

        requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_sent_to_target", target.getGameProfile().getName()));
        requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_usage_after_accept"));
        target.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_received", requester.getGameProfile().getName()));
        target.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_accept_deny_hint", requester.getGameProfile().getName(), requester.getGameProfile().getName()));
        PacketHandler.sendToPlayer(new OpenCrossChatInvitePacket(requester.getUUID(), requester.getGameProfile().getName()), target);
    }

    public synchronized void respondToRequest(ServerPlayer target, ServerPlayer requester, boolean accept) {
        pruneExpiredEntries();
        if (target == null || requester == null) {
            return;
        }

        Map<UUID, PendingRequest> requests = pendingByTarget.get(target.getUUID());
        PendingRequest request = requests != null ? requests.remove(requester.getUUID()) : null;
        if (requests != null && requests.isEmpty()) {
            pendingByTarget.remove(target.getUUID());
        }

        if (request == null || request.isExpired()) {
            target.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_pending_request", requester.getGameProfile().getName()));
            return;
        }

        if (!accept) {
            target.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_denied_self", requester.getGameProfile().getName()));
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_denied_other", target.getGameProfile().getName()));
            return;
        }

        if (!hasBoundHero(target)) {
            target.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_local_hero_acceptor"));
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.accept_target_no_hero", target.getGameProfile().getName()));
            return;
        }
        if (!hasBoundHero(requester)) {
            target.sendSystemMessage(system("message.herobrine_companion.cross_chat.accept_requester_no_hero", requester.getGameProfile().getName()));
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_local_hero_acceptor"));
            return;
        }
        if (activeSessionByPlayer.containsKey(target.getUUID()) || activeSessionByPlayer.containsKey(requester.getUUID())) {
            target.sendSystemMessage(system("message.herobrine_companion.cross_chat.session_conflict_self"));
            requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.session_conflict_other"));
            return;
        }

        ActiveSession session = new ActiveSession(UUID.randomUUID(), target.server, target.getUUID(), requester.getUUID(), System.currentTimeMillis());
        sessionsById.put(session.sessionId, session);
        activeSessionByPlayer.put(target.getUUID(), session.sessionId);
        activeSessionByPlayer.put(requester.getUUID(), session.sessionId);

        String targetName = target.getGameProfile().getName();
        String requesterName = requester.getGameProfile().getName();
        sendToParticipants(session, system("message.herobrine_companion.cross_chat.session_established", targetName, requesterName));
        sendToParticipants(session, system("message.herobrine_companion.cross_chat.session_established_hint_buttons"));
        sendToParticipants(session, system("message.herobrine_companion.cross_chat.session_established_hint_commands"));
        syncSessionParticipants(session);
        openSessionHubInternal(session);
    }

    public synchronized void sendPlayerMessage(ServerPlayer sender, String rawMessage) {
        pruneExpiredEntries();
        ActiveSession session = getSessionFor(sender);
        if (session == null) {
            sender.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_active_session_request_first"));
            return;
        }

        String message = sanitize(rawMessage);
        if (message.isEmpty()) {
            sender.sendSystemMessage(system("message.herobrine_companion.cross_chat.message_empty"));
            return;
        }

        ServerPlayer peer = getPeerPlayer(session, sender);
        if (peer == null) {
            closeSessionInternal(session, system("message.herobrine_companion.cross_chat.peer_offline_closed"));
            return;
        }
        if (!hasBoundHero(peer)) {
            closeSessionInternal(session, system("message.herobrine_companion.cross_chat.peer_no_hero_closed"));
            return;
        }

        String senderName = sender.getGameProfile().getName();
        String peerName = peer.getGameProfile().getName();
        String replyLanguageCode = resolvePlayerLanguageCode(sender);
        sendToParticipants(session, chat("message.herobrine_companion.cross_chat.chat.player_to_hb", senderName, peerName, message));
        appendHistoryToParticipants(session, false, senderName, message, HISTORY_KIND_PLAYER);

        GenerationJob job = new GenerationJob(
                UUID.randomUUID(),
                session.sessionId,
                JobKind.PLAYER_TO_REMOTE_HB,
                sender.getUUID(),
                peer.getUUID(),
                sender.getUUID(),
                System.currentTimeMillis(),
                replyLanguageCode
        );
        jobsById.put(job.jobId, job);

        PacketHandler.sendToPlayer(new HeroCrossChatPromptPacket(
                job.jobId,
                session.sessionId,
                HeroCrossChatPromptPacket.KIND_REMOTE_HB_REPLY,
                buildPlayerToHbPrompt(senderName, peerName, message),
                message,
                replyLanguageCode
        ), peer);
    }

    public synchronized void sendHbToHbMessage(ServerPlayer sender, String rawMessage) {
        pruneExpiredEntries();
        ActiveSession session = getSessionFor(sender);
        if (session == null) {
            sender.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_active_session"));
            return;
        }

        String message = sanitize(rawMessage);
        if (message.isEmpty()) {
            sender.sendSystemMessage(system("message.herobrine_companion.cross_chat.content_empty"));
            return;
        }

        ServerPlayer peer = getPeerPlayer(session, sender);
        if (peer == null) {
            closeSessionInternal(session, system("message.herobrine_companion.cross_chat.peer_offline_closed"));
            return;
        }
        if (!hasBoundHero(sender) || !hasBoundHero(peer)) {
            closeSessionInternal(session, system("message.herobrine_companion.cross_chat.any_side_no_hero_closed"));
            return;
        }
        session.armAutoHbConversation(this.resolveAutoHbTurnLimit(sender));
        String openingLanguageCode = resolvePlayerLanguageCode(sender);

        GenerationJob job = new GenerationJob(
                UUID.randomUUID(),
                session.sessionId,
                JobKind.HB_TO_HB_OPENING,
                sender.getUUID(),
                sender.getUUID(),
                peer.getUUID(),
                System.currentTimeMillis(),
                openingLanguageCode
        );
        jobsById.put(job.jobId, job);

        PacketHandler.sendToPlayer(new HeroCrossChatPromptPacket(
                job.jobId,
                session.sessionId,
                HeroCrossChatPromptPacket.KIND_HB_OPENING,
                buildHbOpeningPrompt(sender.getGameProfile().getName(), peer.getGameProfile().getName(), message),
                message,
                openingLanguageCode
        ), sender);

        sender.sendSystemMessage(system("message.herobrine_companion.cross_chat.queued_hb_to_hb", peer.getGameProfile().getName()));
    }

    public synchronized void handleGeneratedReply(ServerPlayer generator, UUID jobId, String rawReply) {
        pruneExpiredEntries();
        if (generator == null || jobId == null) {
            return;
        }

        GenerationJob job = jobsById.remove(jobId);
        if (job == null || job.isExpired()) {
            generator.sendSystemMessage(system("message.herobrine_companion.cross_chat.task_expired"));
            return;
        }
        if (!Objects.equals(job.generatorOwner, generator.getUUID())) {
            generator.sendSystemMessage(system("message.herobrine_companion.cross_chat.unauthorized_reply"));
            return;
        }

        ActiveSession session = sessionsById.get(job.sessionId);
        if (session == null) {
            generator.sendSystemMessage(system("message.herobrine_companion.cross_chat.session_already_closed"));
            return;
        }

        ServerPlayer counterpart = getPlayer(generator.server, job.counterpartOwner);
        if (counterpart == null) {
            closeSessionInternal(session, system("message.herobrine_companion.cross_chat.peer_offline_closed"));
            return;
        }

        String reply = sanitize(rawReply);
        if (reply.isEmpty()) {
            reply = "......";
        }

        switch (job.kind) {
            case PLAYER_TO_REMOTE_HB -> {
                String generatorName = generator.getGameProfile().getName();
                deliverGeneratedHbLineToParticipants(
                        session,
                        false,
                        generatorName,
                        "",
                        reply,
                        job.outputLanguageCode,
                        PresentCrossChatAiLinePacket.TYPE_REMOTE_HB_REPLY
                );
            }
            case HB_TO_HB_OPENING -> {
                String generatorName = generator.getGameProfile().getName();
                String counterpartName = counterpart.getGameProfile().getName();
                deliverGeneratedHbLineToParticipants(
                        session,
                        true,
                        generatorName,
                        counterpartName,
                        reply,
                        job.outputLanguageCode,
                        PresentCrossChatAiLinePacket.TYPE_HB_TO_HB_OPENING
                );

                String followUpLanguageCode = resolvePlayerLanguageCode(counterpart);

                GenerationJob followUp = new GenerationJob(
                        UUID.randomUUID(),
                        session.sessionId,
                        JobKind.HB_TO_HB_REPLY,
                        generator.getUUID(),
                        counterpart.getUUID(),
                        generator.getUUID(),
                        System.currentTimeMillis(),
                        followUpLanguageCode
                );
                jobsById.put(followUp.jobId, followUp);
                PacketHandler.sendToPlayer(new HeroCrossChatPromptPacket(
                        followUp.jobId,
                        session.sessionId,
                        HeroCrossChatPromptPacket.KIND_HB_REPLY,
                        buildHbReplyPrompt(counterpartName, generatorName, reply),
                        reply,
                        followUpLanguageCode
                ), counterpart);
            }
            case HB_TO_HB_REPLY -> {
                String generatorName = generator.getGameProfile().getName();
                deliverGeneratedHbLineToParticipants(
                        session,
                        true,
                        generatorName,
                        "",
                        reply,
                        job.outputLanguageCode,
                        PresentCrossChatAiLinePacket.TYPE_HB_ECHO
                );

                if (session.consumeAutoHbTurn()) {
                    String counterpartName = counterpart.getGameProfile().getName();
                    String followUpLanguageCode = resolvePlayerLanguageCode(counterpart);
                    GenerationJob followUp = new GenerationJob(
                            UUID.randomUUID(),
                            session.sessionId,
                            JobKind.HB_TO_HB_REPLY,
                            generator.getUUID(),
                            counterpart.getUUID(),
                            generator.getUUID(),
                            System.currentTimeMillis(),
                            followUpLanguageCode
                    );
                    jobsById.put(followUp.jobId, followUp);
                    PacketHandler.sendToPlayer(new HeroCrossChatPromptPacket(
                            followUp.jobId,
                            session.sessionId,
                            HeroCrossChatPromptPacket.KIND_HB_REPLY,
                            buildHbReplyPrompt(counterpartName, generatorName, reply),
                            reply,
                            followUpLanguageCode
                    ), counterpart);
                } else if (session.isAutoHbConversationEnabled()) {
                    sendToParticipants(session, system("message.herobrine_companion.cross_chat.auto_limit_reached"));
                }
            }
        }
    }

    public synchronized void setAutoHbConversation(ServerPlayer player, boolean enabled) {
        pruneExpiredEntries();
        ActiveSession session = getSessionFor(player);
        if (session == null) {
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_active_session"));
            return;
        }

        session.setAutoHbConversationEnabled(enabled);
        syncSessionParticipants(session);
        sendToParticipants(session, system("message.herobrine_companion.cross_chat.auto_toggled", toggleText(enabled)));
        if (enabled) {
            sendToParticipants(session, system("message.herobrine_companion.cross_chat.auto_hint"));
        }
    }

    public synchronized void setAutoHbTurnLimit(ServerPlayer player, int turnLimit) {
        if (player == null) {
            return;
        }
        int normalized = normalizeAutoHbTurnLimit(turnLimit);
        HeroWorldData data = getWorldData(player);
        data.setAutoHbTurnLimit(player.getUUID(), normalized);
        syncClientState(player);
        player.sendSystemMessage(system("message.herobrine_companion.cross_chat.auto_turn_limit_set", normalized));
    }

    public synchronized void closeSession(ServerPlayer player) {
        ActiveSession session = getSessionFor(player);
        if (session == null) {
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_active_session"));
            return;
        }
        closeSessionInternal(session, system("message.herobrine_companion.cross_chat.session_closed_by_player", player.getGameProfile().getName()));
    }

    public synchronized void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }

        Map<UUID, PendingRequest> ownRequests = pendingByTarget.remove(player.getUUID());
        if (ownRequests != null) {
            for (UUID requesterId : ownRequests.keySet()) {
                ServerPlayer requester = getPlayer(player.server, requesterId);
                if (requester != null) {
                    requester.sendSystemMessage(system("message.herobrine_companion.cross_chat.request_invalidated_logout", player.getGameProfile().getName()));
                }
            }
        }

        for (Map<UUID, PendingRequest> requests : pendingByTarget.values()) {
            requests.remove(player.getUUID());
        }
        pendingByTarget.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        ActiveSession session = getSessionFor(player);
        if (session != null) {
            closeSessionInternal(session, system("message.herobrine_companion.cross_chat.session_closed_logout", player.getGameProfile().getName()));
        }

        jobsById.entrySet().removeIf(entry -> entry.getValue().involves(player.getUUID()));
    }

    public synchronized void setAllowIncomingRequests(ServerPlayer player, boolean allowIncoming) {
        if (player == null) {
            return;
        }
        HeroWorldData data = getWorldData(player);
        data.setAllowIncomingCrossChat(player.getUUID(), allowIncoming);
        syncClientState(player);
        player.sendSystemMessage(system(allowIncoming
                ? "message.herobrine_companion.cross_chat.allow_incoming_enabled"
                : "message.herobrine_companion.cross_chat.allow_incoming_disabled"));
    }

    public synchronized void updatePlayerLanguage(ServerPlayer player, String languageCode) {
        if (player == null) {
            return;
        }
        getWorldData(player).setClientLanguageCode(player.getUUID(), normalizeLanguageCode(languageCode));
    }

    public synchronized void syncClientState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ActiveSession session = getSessionFor(player);
        String peerName = "";
        if (session != null) {
            peerName = resolvePlayerName(player.server, session.getPeer(player.getUUID()));
        }
        PacketHandler.sendToPlayer(new SyncCrossChatStatePacket(
                allowsIncomingRequests(player),
                session != null,
                peerName,
                session != null && session.isAutoHbConversationEnabled(),
                this.resolveAutoHbTurnLimit(player)
        ), player);
    }

    public synchronized void openPersistentChat(ServerPlayer player) {
        openPersistentChat(player, false);
    }

    public synchronized void openPersistentHbChat(ServerPlayer player) {
        openPersistentChat(player, true);
    }

    private void openPersistentChat(ServerPlayer player, boolean hbInputMode) {
        pruneExpiredEntries();
        ActiveSession session = getSessionFor(player);
        if (session == null) {
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.no_active_session_request_first"));
            return;
        }

        syncClientState(player);
        PacketHandler.sendToPlayer(new OpenHeroChatPacket(hbInputMode), player);
        if (hbInputMode) {
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.enter_hb_chat"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.current_auto_status", toggleText(session.isAutoHbConversationEnabled())));
        } else {
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.enter_player_chat"));
        }
    }

    public synchronized void sendInfo(ServerPlayer player) {
        pruneExpiredEntries();
        ActiveSession session = getSessionFor(player);
        if (session != null) {
            UUID peerId = session.getPeer(player.getUUID());
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_active_header", resolvePlayerName(player.server, peerId)));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_active_1"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_active_2"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_active_3"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_active_4"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_active_5"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_current_auto", toggleText(session.isAutoHbConversationEnabled())));
        } else {
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_inactive_header"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_inactive_request"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_inactive_open_ui"));
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_inactive_command_fallback"));
        }
        player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_allow_incoming", toggleText(allowsIncomingRequests(player))));

        List<PendingRequest> incoming = getIncomingRequests(player.getUUID());
        if (incoming.isEmpty()) {
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_no_pending"));
        } else {
            String joined = incoming.stream()
                    .sorted(Comparator.comparingLong(request -> request.createdAt))
                    .map(request -> resolvePlayerName(player.server, request.requester))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("-");
            player.sendSystemMessage(system("message.herobrine_companion.cross_chat.info_pending_list", joined));
        }
    }

    private List<PendingRequest> getIncomingRequests(UUID targetId) {
        Map<UUID, PendingRequest> requests = pendingByTarget.get(targetId);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<PendingRequest> result = new ArrayList<>();
        for (PendingRequest request : requests.values()) {
            if (!request.isExpired()) {
                result.add(request);
            }
        }
        return result;
    }

    private void closeSessionInternal(ActiveSession session, Component reason) {
        sessionsById.remove(session.sessionId);
        activeSessionByPlayer.remove(session.ownerA);
        activeSessionByPlayer.remove(session.ownerB);
        jobsById.entrySet().removeIf(entry -> Objects.equals(entry.getValue().sessionId, session.sessionId));
        sendToParticipants(session, reason);
        syncSessionParticipants(session);
    }

    private ActiveSession getSessionFor(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        UUID sessionId = activeSessionByPlayer.get(player.getUUID());
        return sessionId == null ? null : sessionsById.get(sessionId);
    }

    private ServerPlayer getPeerPlayer(ActiveSession session, ServerPlayer self) {
        return session == null || self == null ? null : getPlayer(self.server, session.getPeer(self.getUUID()));
    }

    private boolean hasBoundHero(ServerPlayer player) {
        return player != null && HeroSummonItem.findHeroInAnyDimension(player.server, player.getUUID()) != null;
    }

    private boolean allowsIncomingRequests(ServerPlayer player) {
        return player != null && getWorldData(player).isAllowIncomingCrossChat(player.getUUID());
    }

    private HeroWorldData getWorldData(ServerPlayer player) {
        return HeroWorldData.get((ServerLevel) player.level());
    }

    private void pruneExpiredEntries() {
        for (Map<UUID, PendingRequest> requests : pendingByTarget.values()) {
            requests.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired());
        }
        pendingByTarget.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        jobsById.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired());
    }

    private void sendToParticipants(ActiveSession session, Component message) {
        if (session == null || message == null) {
            return;
        }
        for (ServerPlayer player : getParticipants(session)) {
            player.sendSystemMessage(message);
        }
    }

    private void appendHistoryToParticipants(ActiveSession session, boolean hbMode, String speaker, String content, String kind) {
        if (session == null) {
            return;
        }
        String sanitizedContent = sanitize(content);
        if (sanitizedContent.isEmpty()) {
            return;
        }
        String sanitizedSpeaker = sanitize(speaker);
        for (ServerPlayer player : getParticipants(session)) {
            String peerName = resolvePlayerName(session.server, session.getPeer(player.getUUID()));
            if (peerName == null || peerName.isBlank()) {
                continue;
            }
            PacketHandler.sendToPlayer(new AppendCrossChatHistoryPacket(peerName, hbMode, sanitizedSpeaker, sanitizedContent, kind), player);
        }
    }

    private void deliverGeneratedHbLineToParticipants(ActiveSession session,
                                                      boolean hbMode,
                                                      String speaker,
                                                      String secondaryName,
                                                      String content,
                                                      String sourceLanguageCode,
                                                      byte displayType) {
        if (session == null) {
            return;
        }
        String sanitizedSpeaker = sanitize(speaker);
        String sanitizedSecondaryName = sanitize(secondaryName);
        String sanitizedContent = sanitize(content);
        if (sanitizedSpeaker.isEmpty() || sanitizedContent.isEmpty()) {
            return;
        }
        String normalizedSourceLanguageCode = normalizeLanguageCode(sourceLanguageCode);
        for (ServerPlayer player : getParticipants(session)) {
            String peerName = resolvePlayerName(session.server, session.getPeer(player.getUUID()));
            if (peerName == null || peerName.isBlank()) {
                continue;
            }
            String viewerLanguageCode = resolvePlayerLanguageCode(player);
            boolean translateForViewer = !Objects.equals(viewerLanguageCode, normalizedSourceLanguageCode);
            PacketHandler.sendToPlayer(new PresentCrossChatAiLinePacket(
                    peerName,
                    hbMode,
                    sanitizedSpeaker,
                    sanitizedContent,
                    HISTORY_KIND_HB,
                    displayType,
                    sanitizedSpeaker,
                    sanitizedSecondaryName,
                    translateForViewer
            ), player);
        }
    }

    private void syncSessionParticipants(ActiveSession session) {
        if (session == null) {
            return;
        }
        for (ServerPlayer player : getParticipants(session)) {
            syncClientState(player);
        }
    }

    private void openSessionHubInternal(ActiveSession session) {
        if (session == null) {
            return;
        }
        for (ServerPlayer player : getParticipants(session)) {
            var hero = HeroSummonItem.findHeroInAnyDimension(player.server, player.getUUID());
            if (hero != null) {
                PacketHandler.sendToPlayer(new OpenCrossSessionHubPacket(hero.getId()), player);
            }
        }
    }


    private Collection<ServerPlayer> getParticipants(ActiveSession session) {
        List<ServerPlayer> players = new ArrayList<>();
        ServerPlayer a = getPlayer(session.server, session.ownerA);
        ServerPlayer b = getPlayer(session.server, session.ownerB);
        if (a != null) {
            players.add(a);
        }
        if (b != null && b != a) {
            players.add(b);
        }
        return players;
    }

    private ServerPlayer getPlayer(MinecraftServer server, UUID playerId) {
        return server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    private String resolvePlayerName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = getPlayer(server, playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server != null && playerId != null && server.getProfileCache() != null) {
            var cached = server.getProfileCache().get(playerId);
            if (cached.isPresent()) {
                String name = cached.get().getName();
                return name == null || name.isBlank() ? playerId.toString() : name;
            }
        }
        return String.valueOf(playerId);
    }

    private String resolvePlayerLanguageCode(ServerPlayer player) {
        return player == null ? "en_us" : getWorldData(player).getClientLanguageCode(player.getUUID());
    }

    private int resolveAutoHbTurnLimit(ServerPlayer player) {
        return player == null
                ? HeroWorldData.DEFAULT_AUTO_HB_TURN_LIMIT
                : normalizeAutoHbTurnLimit(getWorldData(player).getAutoHbTurnLimit(player.getUUID()));
    }

    private int normalizeAutoHbTurnLimit(int turnLimit) {
        return Math.max(HeroWorldData.MIN_AUTO_HB_TURN_LIMIT, Math.min(HeroWorldData.MAX_AUTO_HB_TURN_LIMIT, turnLimit));
    }

    private String normalizeLanguageCode(String languageCode) {
        if (languageCode == null) {
            return "en_us";
        }
        String normalized = languageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "en_us" : normalized;
    }

    private static String sanitize(String rawMessage) {
        if (rawMessage == null) {
            return "";
        }
        String sanitized = rawMessage
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.length() > MAX_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_MESSAGE_LENGTH).trim();
        }
        return sanitized;
    }

    private static String buildPlayerToHbPrompt(String speakerName, String ownerName, String message) {
        return "You are roleplaying as the Herobrine bound to player " + ownerName + ". "
                + "This is a private cross-player session accepted by both players. "
                + "Do not claim to see private history that was not directly mentioned in this session, and do not reveal other conversations. "
                + "The other player " + speakerName + " just said to you: \"" + message + "\". "
                + "Reply naturally as Herobrine, keep it concise, and output only your line.";
    }
    private static String buildHbOpeningPrompt(String ownerName, String targetOwnerName, String message) {
        return "You are roleplaying as the Herobrine bound to player " + ownerName + ". "
                + "This is a private cross-Herobrine session accepted by both sides. "
                + "Only speak as " + ownerName + "'s Herobrine; do not impersonate " + targetOwnerName + " or the other Herobrine. "
                + "You are speaking to the Herobrine bound to " + targetOwnerName + ". "
                + "The player wants you to express this opening idea: \"" + message + "\". "
                + "Turn it into your own Herobrine voice and output only the dialogue.";
    }
    private static String buildHbReplyPrompt(String ownerName, String peerOwnerName, String hbLine) {
        return "You are roleplaying as the Herobrine bound to player " + ownerName + ". "
                + "This is a private cross-Herobrine session accepted by both sides. "
                + "Only answer as " + ownerName + "'s Herobrine; do not speak for " + peerOwnerName + ", either player, or merge their memories/settings. "
                + "The other Herobrine, bound to " + peerOwnerName + ", just said: \"" + hbLine + "\". "
                + "Respond directly as Herobrine and output only your line.";
    }
    private static Component system(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static Component chat(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static Component toggleText(boolean enabled) {
        return Component.translatable(enabled
                ? "message.herobrine_companion.cross_chat.status_on"
                : "message.herobrine_companion.cross_chat.status_off");
    }

    private enum JobKind {
        PLAYER_TO_REMOTE_HB,
        HB_TO_HB_OPENING,
        HB_TO_HB_REPLY
    }

    private static final class PendingRequest {
        private final UUID requester;
        private final long createdAt;

        private PendingRequest(UUID requester, UUID target, long createdAt) {
            this.requester = requester;
            this.createdAt = createdAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - this.createdAt > REQUEST_TIMEOUT_MS;
        }
    }

    private static final class ActiveSession {
        private final UUID sessionId;
        private final MinecraftServer server;
        private final UUID ownerA;
        private final UUID ownerB;
        private final long createdAt;
        private boolean autoHbConversationEnabled;
        private int autoHbTurnsRemaining;

        private ActiveSession(UUID sessionId, MinecraftServer server, UUID ownerA, UUID ownerB, long createdAt) {
            this.sessionId = sessionId;
            this.server = server;
            this.ownerA = ownerA;
            this.ownerB = ownerB;
            this.createdAt = createdAt;
        }

        private UUID getPeer(UUID playerId) {
            return Objects.equals(this.ownerA, playerId) ? this.ownerB : this.ownerA;
        }

        private boolean isAutoHbConversationEnabled() {
            return this.autoHbConversationEnabled;
        }

        private void setAutoHbConversationEnabled(boolean enabled) {
            this.autoHbConversationEnabled = enabled;
            if (!enabled) {
                this.autoHbTurnsRemaining = 0;
            }
        }

        private void armAutoHbConversation(int turnLimit) {
            this.autoHbTurnsRemaining = this.autoHbConversationEnabled ? Math.max(0, turnLimit) : 0;
        }

        private boolean consumeAutoHbTurn() {
            if (!this.autoHbConversationEnabled || this.autoHbTurnsRemaining <= 0) {
                return false;
            }
            this.autoHbTurnsRemaining--;
            return true;
        }
    }

    private static final class GenerationJob {
        private final UUID jobId;
        private final UUID sessionId;
        private final JobKind kind;
        private final UUID requesterOwner;
        private final UUID generatorOwner;
        private final UUID counterpartOwner;
        private final long createdAt;
        private final String outputLanguageCode;

        private GenerationJob(UUID jobId, UUID sessionId, JobKind kind, UUID requesterOwner, UUID generatorOwner, UUID counterpartOwner, long createdAt, String outputLanguageCode) {
            this.jobId = jobId;
            this.sessionId = sessionId;
            this.kind = kind;
            this.requesterOwner = requesterOwner;
            this.generatorOwner = generatorOwner;
            this.counterpartOwner = counterpartOwner;
            this.createdAt = createdAt;
            this.outputLanguageCode = outputLanguageCode == null || outputLanguageCode.isBlank() ? "en_us" : outputLanguageCode;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - this.createdAt > JOB_TIMEOUT_MS;
        }

        private boolean involves(UUID playerId) {
            return Objects.equals(this.requesterOwner, playerId)
                    || Objects.equals(this.generatorOwner, playerId)
                    || Objects.equals(this.counterpartOwner, playerId);
        }
    }
}









