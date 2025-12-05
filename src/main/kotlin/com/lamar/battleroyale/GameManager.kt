package com.lamar.battleroyale

import org.bukkit.entity.Player
import java.util.UUID

class GameManager(private val plugin: BattleRoyalePlugin) {

    val games = mutableMapOf<String, Game>()
    private var nextGameId = 1

    fun createGame(maxPlayers: Int, minPlayers: Int? = null): String {
        val id = "game$nextGameId"
        nextGameId++
        val game = Game(id, plugin, maxPlayers, minPlayers)
        games[id] = game
        return id
    }
    
    fun deleteGame(id: String): Boolean {
        val game = games[id] ?: return false
        game.stopGame()
        games.remove(id)
        
        if (games.isEmpty()) {
            nextGameId = 1
        }
        
        return true
    }
    
    fun getGame(id: String): Game? {
        return games[id]
    }
    
    fun getGame(player: Player): Game? {
        return games.values.find { it.players.contains(player.uniqueId) || it.spectators.contains(player.uniqueId) }
    }
    
    fun getGame(entity: org.bukkit.entity.Entity): Game? {
        if (entity is Player) {
            return getGame(entity)
        }
        return null
    }
    
    // Helper to get the "default" game or first available, for legacy commands if needed
    fun getDefaultGame(): Game? {
        if (games.size == 1) return games.values.first()
        return null
    }
    
    fun stopAllGames() {
        games.values.forEach { it.stopGame() }
        games.clear()
    }
}
