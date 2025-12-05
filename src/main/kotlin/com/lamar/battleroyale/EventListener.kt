package com.lamar.battleroyale

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class EventListener(private val plugin: BattleRoyalePlugin) : Listener {

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        handleRegionCheck(event.player, event.to)
    }
    
    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        handleRegionCheck(event.player, event.to)
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        handleRegionCheck(event.player, event.player.location)
    }
    
    private fun handleRegionCheck(player: Player, location: Location) {
        if (!plugin.config.getBoolean("allow-spectators", true)) return
        
        val game = plugin.gameManager.getGame(player)
        
        // If player is IN a game
        if (game != null) {
            // If they are in a game (playing or spectator) and leave the arena, they should leave the game?
            // User said: "some people leave area by warping /home ... and still in SPECTATOR mode"
            // So if they warp OUT of the arena, we should remove them from the game.
            // FIX: Only do this if the game is PLAYING. If WAITING, they might be in lobby (outside arena).
            if (!plugin.arenaManager.isInArena(location)) {
                if (game.state == GameState.PLAYING) {
                    // Check if they are actually warping out or just moving out (border should prevent moving out)
                    // But if they warp /home, they are definitely out.
                    game.leavePlayer(player)
                    player.sendMessage(plugin.getMessage("left-game-area"))
                }
            }
            return
        }
        
        // Check if player is in arena
        if (plugin.arenaManager.isInArena(location)) {
            // STRICT: If in arena and NOT in game -> Spectator
            // This applies to ANYONE entering the arena who is not in a game object.
            if (!plugin.spectatorManager.isSpectator(player)) {
                plugin.spectatorManager.addSpectator(player)
            }
        } else {
            // Player is OUTSIDE arena
            // If they were forced spectator, remove them
            if (plugin.spectatorManager.isSpectator(player)) {
                plugin.spectatorManager.removeSpectator(player)
                player.sendMessage(plugin.getMessage("spectator-leave"))
            }
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val game = plugin.gameManager.getGame(event.player)
        game?.leavePlayer(event.player)
    }
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val game = plugin.gameManager.getGame(event.entity)
        if (game != null && game.state == GameState.PLAYING) {
            event.drops.clear() // Clear drops for BR style? Or keep? User didn't specify.
            // Usually BR clears drops or puts in chest. Let's clear for now as per previous logic.
            game.eliminatePlayer(event.entity)
        }
    }
    
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val game = plugin.gameManager.getGame(event.player)
        // If player was eliminated, they are in spectator list.
        // Teleport them to lobby or spectator point?
        if (game != null && game.spectators.contains(event.player.uniqueId)) {
            val lobby = plugin.arenaManager.getLobby()
            if (lobby != null) {
                event.respawnLocation = lobby
            }
        }
    }

    @EventHandler
    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val game = plugin.gameManager.getGame(player)
        
        if (game != null) {
            val msg = event.message.lowercase()
            val blockedCommands = plugin.config.getStringList("blocked-commands")
            
            // Check if command starts with any blocked command
            // e.g. /spawn -> blocked
            // /spawn something -> blocked
            for (cmd in blockedCommands) {
                if (msg.startsWith(cmd.lowercase())) {
                    event.isCancelled = true
                    player.sendMessage(plugin.getMessage("command-blocked"))
                    return
                }
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val game = plugin.gameManager.getGame(event.player)
        if (game != null && game.state == GameState.PLAYING) {
            if (plugin.arenaManager.isInArena(event.block.location)) {
                plugin.rollbackManager.recordBlock(event.block)
            }
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val game = plugin.gameManager.getGame(event.player)
        if (game != null && game.state == GameState.PLAYING) {
            if (plugin.arenaManager.isInArena(event.block.location)) {
                // Fix: Record the state replaced (what was there BEFORE placement)
                plugin.rollbackManager.recordBlockState(event.blockReplacedState)
            }
        }
    }

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        // We need to check if the explosion is inside an active game arena
        // This is harder because we don't know which game caused it easily, 
        // but we can check if the location is in ANY active game arena.
        // For now, let's just check if it's in the main arena.
        
        // Filter blocks
        val iterator = event.blockList().iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            if (plugin.arenaManager.isInArena(block.location)) {
                // Check if a game is playing?
                // Ideally we only rollback if a game is playing.
                // But explosions might happen from other sources.
                // Let's assume if it's in the arena, we track it if a game is running.
                // But we don't have a quick "is game running in this arena" check without iterating games.
                // Since we only have one arena manager (single arena logic for now based on config),
                // we can check if ANY game is playing.
                val isGamePlaying = plugin.gameManager.games.values.any { it.state == GameState.PLAYING }
                if (isGamePlaying) {
                     plugin.rollbackManager.recordBlock(block)
                }
            }
        }
    }
}
