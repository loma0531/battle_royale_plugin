package com.lamar.battleroyale

import org.bukkit.plugin.java.JavaPlugin

class BattleRoyalePlugin : JavaPlugin() {

    lateinit var gameManager: GameManager
    lateinit var arenaManager: ArenaManager
    lateinit var spectatorManager: SpectatorManager
    lateinit var rollbackManager: RollbackManager

    override fun onEnable() {
        // Create plugin folder if not exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        
        // Save default config if not exists
        saveDefaultConfig()
        
        arenaManager = ArenaManager(this)
        spectatorManager = SpectatorManager(this)
        gameManager = GameManager(this)
        rollbackManager = RollbackManager(this)
        
        val commandManager = CommandManager(this)
        getCommand("battleroyale")?.setExecutor(commandManager)
        getCommand("battleroyale")?.tabCompleter = commandManager
        server.pluginManager.registerEvents(EventListener(this), this)
        
        logger.info("Battle Royale Plugin Enabled!")
    }

    override fun onDisable() {
        if (::gameManager.isInitialized) {
            gameManager.stopAllGames()
        }
        logger.info("Battle Royale Plugin Disabled!")
    }

    fun getMessage(key: String, placeholders: Map<String, String> = emptyMap()): net.kyori.adventure.text.Component {
        val prefixStr = config.getString("prefix", "<gray>[<red>BattleRoyale<gray>] <white>")!!
        
        var msg = config.getString("messages.$key")
        if (msg == null) return net.kyori.adventure.text.Component.empty()
        
        msg = msg!!.replace("%prefix%", prefixStr)
        placeholders.forEach { (k, v) -> msg = msg!!.replace("%$k%", v) }
        
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg!!)
    }
}
