package com.lamar.battleroyale

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

class Game(val id: String, private val plugin: BattleRoyalePlugin, val maxPlayers: Int, private val explicitMinPlayers: Int? = null) {

    var state = GameState.WAITING
    val players = mutableSetOf<UUID>()
    val spectators = mutableSetOf<UUID>()
    
    private var countdownTask: BukkitRunnable? = null
    private var actionBarTask: BukkitRunnable? = null
    
    val borderManager = BorderManager(plugin, this)
    
    val minPlayers = explicitMinPlayers ?: if (maxPlayers <= 4) maxPlayers else Math.max(plugin.config.getInt("min-players", 2), maxPlayers / 2)
    private val countdownTime = plugin.config.getInt("countdown-time", 10)

    fun joinPlayer(player: Player) {
        if (state != GameState.WAITING && state != GameState.STARTING) {
            player.sendMessage(plugin.getMessage("game-in-progress"))
            return
        }
        
        if (players.contains(player.uniqueId)) {
            player.sendMessage(plugin.getMessage("already-in-game"))
            return
        }
        
        if (players.size >= maxPlayers) {
             player.sendMessage(plugin.getMessage("game-full", mapOf("max_players" to maxPlayers.toString())))
             return
        }

        // Fix: If they are a forced spectator, remove them from that system before joining
        if (plugin.spectatorManager.isSpectator(player)) {
            plugin.spectatorManager.removeSpectator(player)
        }

        players.add(player.uniqueId)
        
        // Don't set to ADVENTURE yet. Keep current mode until start.
        // player.gameMode = GameMode.ADVENTURE
        player.health = 20.0
        player.foodLevel = 20
        
        val lobby = plugin.arenaManager.getLobby()
        if (lobby != null) {
            player.teleport(lobby)
        }
        
        broadcast(plugin.getMessage("joined", mapOf("player_name" to player.name, "current_players" to players.size.toString(), "min_players" to minPlayers.toString())))
        
        checkStart()
        updateLobbyActionBar()
    }

    fun leavePlayer(player: Player) {
        if (players.remove(player.uniqueId)) {
            
            // broadcast(plugin.getMessage("left", mapOf("player" to player.name)))
            
            if (state == GameState.STARTING && players.size < minPlayers) {
                stopCountdown()
                state = GameState.WAITING
                broadcast(plugin.getMessage("not-enough-players"))
            } else if (state == GameState.PLAYING) {
                checkWinCondition()
            }
            updateLobbyActionBar()
        } else if (spectators.remove(player.uniqueId)) {
             // Just remove from spectator list
        }
    }
    

    fun resetGame(force: Boolean) {
        state = GameState.ENDING
        stopCountdown()
        borderManager.stop()
        actionBarTask?.cancel()
        actionBarTask = null
        
        // Teleport everyone to lobby
        val lobby = plugin.arenaManager.getLobby()
        if (lobby != null) {
            players.forEach { Bukkit.getPlayer(it)?.teleport(lobby) }
            spectators.forEach { Bukkit.getPlayer(it)?.teleport(lobby) }
        }
        
        
        players.clear()
        spectators.clear()
        
        state = GameState.WAITING
        updateLobbyActionBar()
        
        // Restore arena
        plugin.rollbackManager.restoreArena()
    }
    
    private fun checkStart() {
        if (state == GameState.WAITING && players.size >= minPlayers) {
            // Check if any other game is running
            val isAnyRunning = plugin.gameManager.games.values.any { it.id != this.id && (it.state == GameState.STARTING || it.state == GameState.PLAYING) }
            if (isAnyRunning) {
                broadcast(plugin.getMessage("another-game-running"))
                return
            }
            
            startCountdown()
        }
    }

    fun startCountdown() {
        if (state == GameState.STARTING || state == GameState.PLAYING) return
        
        // Double check if any other game is running (for manual start)
        val isAnyRunning = plugin.gameManager.games.values.any { it.id != this.id && (it.state == GameState.STARTING || it.state == GameState.PLAYING) }
        if (isAnyRunning) {
            broadcast(plugin.getMessage("another-game-running"))
            return
        }
        
        state = GameState.STARTING
        var timeLeft = countdownTime
        
        countdownTask = object : BukkitRunnable() {
            override fun run() {
                if (timeLeft <= 0) {
                    startGame()
                    cancel()
                    return
                }
                
                if (timeLeft <= 5 || timeLeft % 10 == 0) {
                    broadcast(plugin.getMessage("game-starting", mapOf("time_remaining" to timeLeft.toString())))
                }
                
                // Update Action Bar for countdown
                val msg = plugin.config.getString("messages.actionbar-lobby-countdown") ?: ""
                val finalMsg = msg.replace("%time_remaining%", timeLeft.toString())
                val component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(finalMsg)
                players.forEach { Bukkit.getPlayer(it)?.sendActionBar(component) }
                
                timeLeft--
            }
        }
        countdownTask?.runTaskTimer(plugin, 0L, 20L)
    }
    
    private fun stopCountdown() {
        countdownTask?.cancel()
        countdownTask = null
        updateLobbyActionBar()
    }

    fun startGame() {
        state = GameState.PLAYING
        broadcast(plugin.getMessage("game-started"))
        
        val gameModeStr = plugin.config.getString("game-mode", "SURVIVAL")
        val gameMode = GameMode.valueOf(gameModeStr!!)
        
        players.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                // Safeguard: If they are somehow still a forced spectator, skip them (shouldn't happen)
                if (plugin.spectatorManager.isSpectator(player)) return@forEach
                
                player.gameMode = gameMode
                
                // Fade animation
                // Fade animation
                val title = net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.empty(),
                    net.kyori.adventure.text.Component.empty(),
                    net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofMillis(1000),
                        java.time.Duration.ofMillis(500)
                    )
                )
                player.showTitle(title)
                // Add blindness for fade effect
                player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 1, false, false))
                
                plugin.arenaManager.teleportRandomly(player)
            }
        }
        
        // Start Border
        val center = plugin.arenaManager.getCenter()
        if (center != null) {
            val size = plugin.arenaManager.getSize()
            borderManager.start(center, size)
        }
        
        startActionBarTask()
    }

    fun stopGame() {
        resetGame(false)
    }
    
    fun checkWinCondition() {
        if (state != GameState.PLAYING) return
        
        if (players.size <= 1) {
            if (players.size == 1) {
                val winnerUUID = players.first()
                val winner = Bukkit.getPlayer(winnerUUID)
                val winnerName = winner?.name ?: "Unknown"
                // Global broadcast
                Bukkit.broadcast(plugin.getMessage("game-won", mapOf("player_name" to winnerName)))
            } else {
                broadcast(plugin.getMessage("game-reset"))
            }
            resetGame(false)
            // Auto-delete game after it ends
            plugin.gameManager.deleteGame(id)
        }
    }
    
    fun eliminatePlayer(player: Player) {
        if (players.remove(player.uniqueId)) {
            player.gameMode = GameMode.SPECTATOR
            spectators.add(player.uniqueId)
            broadcast(plugin.getMessage("eliminated", mapOf("player_name" to player.name)))
            checkWinCondition()
        }
    }
    
    private fun startActionBarTask() {
        actionBarTask = object : BukkitRunnable() {
            override fun run() {
                if (state != GameState.PLAYING) {
                    cancel()
                    return
                }
                
                val borderState = borderManager.state
                val borderTime = borderManager.timeRemaining
                
                var msg = plugin.config.getString("messages.actionbar-game") ?: ""
                msg = msg.replace("%current_players%", players.size.toString())
                         .replace("%border_state%", borderState)
                         .replace("%time_remaining%", borderTime.toString())
                
                val component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg)
                
                players.forEach { Bukkit.getPlayer(it)?.sendActionBar(component) }
                spectators.forEach { Bukkit.getPlayer(it)?.sendActionBar(component) }
            }
        }
        actionBarTask?.runTaskTimer(plugin, 0L, 20L)
    }
    
    private fun updateLobbyActionBar() {
        if (state != GameState.WAITING) return
        
        var msg = plugin.config.getString("messages.actionbar-lobby") ?: ""
        msg = msg.replace("%current_players%", players.size.toString())
                         .replace("%min_players%", minPlayers.toString())
        val component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg)
        
        players.forEach { Bukkit.getPlayer(it)?.sendActionBar(component) }
    }

    fun broadcast(message: net.kyori.adventure.text.Component) {
        players.forEach { Bukkit.getPlayer(it)?.sendMessage(message) }
        spectators.forEach { Bukkit.getPlayer(it)?.sendMessage(message) }
    }
}
