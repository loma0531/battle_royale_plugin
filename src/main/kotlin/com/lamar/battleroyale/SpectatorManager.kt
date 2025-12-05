package com.lamar.battleroyale

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class SpectatorManager(private val plugin: BattleRoyalePlugin) {

    private val forcedSpectators = mutableSetOf<UUID>()
    private val previousGameModes = mutableMapOf<UUID, GameMode>()

    fun addSpectator(player: Player) {
        if (forcedSpectators.contains(player.uniqueId)) return
        
        // Save current GameMode before switching
        previousGameModes[player.uniqueId] = player.gameMode
        forcedSpectators.add(player.uniqueId)
        
        val spectatorModeStr = plugin.config.getString("spectator-mode", "SPECTATOR")
        val spectatorMode = GameMode.valueOf(spectatorModeStr!!)
        player.gameMode = spectatorMode
        
        player.sendMessage(plugin.getMessage("spectator-enter"))
    }
    
    fun removeSpectator(player: Player) {
        if (!forcedSpectators.contains(player.uniqueId)) return
        
        forcedSpectators.remove(player.uniqueId)
        
        // Restore to previous GameMode or default to SURVIVAL
        val prevMode = previousGameModes.remove(player.uniqueId) ?: GameMode.SURVIVAL
        player.gameMode = prevMode
    }
    
    fun isSpectator(player: Player): Boolean {
        return forcedSpectators.contains(player.uniqueId)
    }
}
