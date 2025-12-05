package com.lamar.battleroyale

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class CommandManager(private val plugin: BattleRoyalePlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (plugin.config.getBoolean("use-permissions", true)) {
            // Granular permissions check
            val subCommand = if (args.isNotEmpty()) args[0].lowercase() else "help"
            
            val perm = when (subCommand) {
                "join", "j" -> "battleroyale.join"
                "leave", "l" -> "battleroyale.leave"
                "list" -> "battleroyale.list"
                "create" -> "battleroyale.create"
                "delete" -> "battleroyale.delete"
                "start" -> "battleroyale.start"
                "reset" -> "battleroyale.reset"
                "setarena" -> "battleroyale.setarena"
                "setlobby" -> "battleroyale.setlobby"
                "arena" -> "battleroyale.arena"
                "reload" -> "battleroyale.reload"
                "help" -> "battleroyale.help"
                else -> null
            }
            
            if (perm != null && !sender.hasPermission(perm) && !sender.hasPermission("battleroyale.admin")) {
                sender.sendMessage(plugin.getMessage("no-permission"))
                return true
            }
            
            // Special case for create: check allow-player-create-game
            if (subCommand == "create" && !sender.hasPermission("battleroyale.admin")) {
                 if (!plugin.config.getBoolean("allow-player-create-game", false)) {
                     sender.sendMessage(plugin.getMessage("no-permission"))
                     return true
                 }
            }
        }

        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage"))
            return true
        }

        when (args[0].lowercase()) {
            "join", "j" -> {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("only-players"))
                    return true
                }
                
                var game: Game? = null
                if (args.size > 1) {
                    game = plugin.gameManager.getGame(args[1])
                    if (game == null) {
                        sender.sendMessage(plugin.getMessage("game-not-found"))
                        return true
                    }
                } else {
                    game = plugin.gameManager.getDefaultGame()
                    if (game == null) {
                         // If multiple games, ask to specify. If 0 games, say no games.
                         if (plugin.gameManager.games.isEmpty()) {
                             sender.sendMessage(plugin.getMessage("no-games-available"))
                         } else {
                             sender.sendMessage(plugin.getMessage("specify-game"))
                         }
                         return true
                    }
                }
                
                game?.joinPlayer(sender)
            }
            "leave", "l" -> {
                handleLeave(sender)
            }
            "list" -> {
                handleList(sender)
            }
            "create" -> {
                // /br create <min> [max]
                
                var maxPlayers = plugin.config.getInt("max-players", 100)
                var minPlayers: Int? = null
                
                if (args.size > 1) {
                    minPlayers = args[1].toIntOrNull()
                }
                
                if (args.size > 2) {
                    maxPlayers = args[2].toIntOrNull() ?: maxPlayers
                }
                
                val id = plugin.gameManager.createGame(maxPlayers, minPlayers)
                val game = plugin.gameManager.getGame(id)
                val min = game?.minPlayers ?: 0
                
                sender.sendMessage(plugin.getMessage("game-created", mapOf(
                    "game_id" to id,
                    "current_players" to "0",
                    "min_players" to min.toString(),
                    "max_players" to maxPlayers.toString()
                )))
            }
            "delete" -> {
                if (args.size < 2) {
                    sender.sendMessage(plugin.getMessage("usage-delete"))
                    return true
                }
                val id = args[1]
                if (plugin.gameManager.deleteGame(id)) {
                    sender.sendMessage(plugin.getMessage("game-deleted", mapOf("game_id" to id)))
                } else {
                    sender.sendMessage(plugin.getMessage("game-not-found"))
                }
            }
            "setarena" -> {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("only-players"))
                    return true
                }
                // /br setarena {pos|radius} [args]
                if (args.size < 2) {
                    sender.sendMessage(plugin.getMessage("usage-setarena"))
                    return true
                }
                
                val type = args[1].lowercase()
                if (type == "radius" || type == "r") {
                    if (args.size < 3) {
                         sender.sendMessage(plugin.getMessage("usage-setarena-radius"))
                         return true
                    }
                    val size = args[2].toDoubleOrNull()
                    if (size == null) {
                        sender.sendMessage(plugin.getMessage("invalid-number"))
                        return true
                    }
                    plugin.arenaManager.setArenaCenter(sender.location, size)
                    sender.sendMessage(plugin.getMessage("arena-set", mapOf("arena_size" to size.toString())))
                    
                } else if (type == "pos" || type == "p") {
                    if (args.size < 3) {
                        sender.sendMessage(plugin.getMessage("usage-setarena-pos"))
                        return true
                    }
                    val pos = args[2].toIntOrNull()
                    if (pos == 1) {
                        plugin.arenaManager.setPos1(sender.location)
                        sender.sendMessage(plugin.getMessage("pos1-set"))
                    } else if (pos == 2) {
                        plugin.arenaManager.setPos2(sender.location)
                        sender.sendMessage(plugin.getMessage("pos2-set"))
                    } else {
                        sender.sendMessage(plugin.getMessage("usage-setarena-pos"))
                    }
                } else {
                     // Fallback for old command /br setarena <size>
                     val size = args[1].toDoubleOrNull()
                      if (size != null) {
                          plugin.arenaManager.setArenaCenter(sender.location, size)
                          sender.sendMessage(plugin.getMessage("arena-set", mapOf("arena_size" to size.toString())))
                      } else {
                         sender.sendMessage(plugin.getMessage("usage-setarena"))
                     }
                }
            }
            "setlobby" -> {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("only-players"))
                    return true
                }
                plugin.arenaManager.setLobby(sender.location)
                sender.sendMessage(plugin.getMessage("lobby-set"))
            }
            "arena" -> {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("only-players"))
                    return true
                }
                val center = plugin.arenaManager.getCenter()
                if (center != null) {
                    sender.teleport(center)
                    sender.sendMessage(plugin.getMessage("teleported-arena"))
                } else {
                    sender.sendMessage(plugin.getMessage("arena-not-set"))
                }
            }
            "start" -> {
                var game: Game? = null
                if (args.size > 1) {
                    game = plugin.gameManager.getGame(args[1])
                } else {
                    game = plugin.gameManager.getDefaultGame()
                }
                
                if (game == null) {
                    sender.sendMessage(plugin.getMessage("game-not-found"))
                    return true
                }
                
                game.startCountdown()
                sender.sendMessage(plugin.getMessage("countdown-started"))
            }
            "reset" -> {
                // Reset specific game or all?
                if (args.size > 1) {
                    val game = plugin.gameManager.getGame(args[1])
                    if (game != null) {
                        game.resetGame(true)
                        sender.sendMessage(plugin.getMessage("game-reset-id", mapOf("game_id" to args[1])))
                    } else {
                        sender.sendMessage(plugin.getMessage("game-not-found"))
                    }
                } else {
                    plugin.gameManager.stopAllGames()
                    sender.sendMessage(plugin.getMessage("all-games-reset"))
                }
            }
            "reload" -> {
                plugin.reloadConfig()
                // Border config is reloaded when a game starts
                sender.sendMessage(plugin.getMessage("config-reloaded"))
            }
            "help" -> {
                sender.sendMessage(plugin.getMessage("help-header"))
                sender.sendMessage(plugin.getMessage("help-join"))
                sender.sendMessage(plugin.getMessage("help-leave"))
                sender.sendMessage(plugin.getMessage("help-create"))
                sender.sendMessage(plugin.getMessage("help-delete"))
                sender.sendMessage(plugin.getMessage("help-setarena"))
                sender.sendMessage(plugin.getMessage("help-setlobby"))
                sender.sendMessage(plugin.getMessage("help-start"))
                sender.sendMessage(plugin.getMessage("help-reset"))
                sender.sendMessage(plugin.getMessage("help-reload"))
            }
            else -> sender.sendMessage(plugin.getMessage("unknown-command"))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        val completions = mutableListOf<String>()
        
        if (args.size == 1) {
            val commands = listOf("join", "leave", "create", "delete", "setarena", "setlobby", "arena", "start", "reset", "reload", "help", "list")
            val input = args[0].lowercase()
            for (cmd in commands) {
                if (cmd.startsWith(input)) {
                    completions.add(cmd)
                }
            }
            return completions
        }
        
        if (args.size == 2) {
            val input = args[1].lowercase()
            when (args[0].lowercase()) {
                "join", "delete", "start", "reset" -> {
                    for (id in plugin.gameManager.games.keys) {
                        if (id.lowercase().startsWith(input)) {
                            completions.add(id)
                        }
                    }
                }
                "setarena" -> {
                    val types = listOf("radius", "pos")
                    for (type in types) {
                        if (type.startsWith(input)) {
                            completions.add(type)
                        }
                    }
                }
                "create" -> {
                    val opts = listOf("2", "5", "10")
                    for (opt in opts) {
                        if (opt.startsWith(input)) completions.add(opt)
                    }
                }
            }
        }
        
        if (args.size == 3) {
            if (args[0].equals("setarena", true)) {
                val input = args[2].lowercase()
                if (args[1].equals("pos", true)) {
                    val opts = listOf("1", "2")
                    for (opt in opts) {
                        if (opt.startsWith(input)) completions.add(opt)
                    }
                }
                if (args[1].equals("radius", true)) {
                    val opts = listOf("100", "200", "500")
                    for (opt in opts) {
                        if (opt.startsWith(input)) completions.add(opt)
                    }
                }
            }
            if (args[0].equals("create", true)) {
                val input = args[2].lowercase()
                val opts = listOf("10", "20", "50", "100")
                for (opt in opts) {
                    if (opt.startsWith(input)) completions.add(opt)
                }
            }
        }
        return completions
    }
    private fun handleLeave(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(plugin.getMessage("only-players"))
            return
        }
        
        if (!sender.hasPermission("battleroyale.leave")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }
        
        val game = plugin.gameManager.getGame(sender)
        if (game == null) {
            sender.sendMessage(plugin.getMessage("not-in-game"))
            return
        }
        
        game.leavePlayer(sender)
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("battleroyale.list")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        sender.sendMessage(plugin.getMessage("list-header"))
        
        if (plugin.gameManager.games.isEmpty()) {
            sender.sendMessage(plugin.getMessage("no-games-available"))
            return
        }

        for (game in plugin.gameManager.games.values) {
            val msg = plugin.config.getString("messages.list-format") ?: "%game_id%: %game_state% (%current_players%/%max_players%)"
            val formatted = msg.replace("%game_id%", game.id)
                               .replace("%game_state%", game.state.name)
                               .replace("%current_players%", game.players.size.toString())
                               .replace("%max_players%", game.maxPlayers.toString())
            
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(formatted))
        }
    }
}
