package com.whitecloud233.herobrine_companion.client.service;

import java.nio.file.Path;

/** 会话存储只需要会话标识、目录与默认标题，不依赖游戏界面实例。 */
interface ConversationEnvironment {
    String sessionKey();
    Path sessionFile(String sessionKey);
    Path exportDirectory();
    String defaultTitle();
}
