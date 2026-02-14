HerobrineCompanionEvents.registerTrades(event => {
    console.info('HerobrineCompanion: Registering custom trades via KubeJS...')

    // 移除所有默认交易 (可选，测试时可以先注释掉)
    // event.removeAll()

    // 添加简单交易: 5个苹果换1个钻石
    event.add('5x minecraft:apple', 'minecraft:diamond')

    // 添加双输入交易: 5个苹果 + 1个金锭 换 1个钻石
    event.add('5x minecraft:apple', 'minecraft:gold_ingot', 'minecraft:diamond')

    // 根据信任等级添加交易
    // 注意：trustLevel 是通过 event.getTrustLevel() 获取的，但在 JS 中可以直接访问属性（如果定义了 getTrustLevel）
    // 或者直接调用方法 event.getTrustLevel()
    if (event.getTrustLevel() >= 50) {
        event.add('minecraft:dirt', 'minecraft:nether_star')
    }
})
