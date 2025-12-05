package com.lamar.battleroyale

import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.data.BlockData
import java.util.concurrent.ConcurrentHashMap

class RollbackManager(private val plugin: BattleRoyalePlugin) {

    // Map to store original block data: Location -> BlockData
    // Using ConcurrentHashMap for thread safety if needed, though most Bukkit stuff is main thread.
    private val changedBlocks = ConcurrentHashMap<Location, BlockData>()
    private val changedBlockStates = ConcurrentHashMap<Location, BlockState>()

    fun recordBlock(block: Block) {
        // Only record the FIRST time a block is changed in a session to ensure we rollback to the ORIGINAL state
        if (!changedBlocks.containsKey(block.location)) {
            changedBlocks[block.location] = block.blockData.clone()
            changedBlockStates[block.location] = block.state
        }
    }

    fun recordBlockState(state: BlockState) {
        if (!changedBlocks.containsKey(state.location)) {
            changedBlocks[state.location] = state.blockData.clone()
            changedBlockStates[state.location] = state
        }
    }

    fun restoreArena() {
        if (changedBlocks.isEmpty()) return

        // Restore blocks
        // We iterate through the map and set the blocks back
        // It's often better to restore in reverse order of modification if we tracked order, 
        // but for simple block replacements, just setting them back is usually fine.
        // However, for things like torches attached to walls, order matters.
        // Since we only store the ORIGINAL state, order of restoration might matter if we have dependent blocks.
        // A simple map doesn't preserve order. Let's try simple restoration first.
        
        changedBlocks.forEach { (loc, data) ->
            loc.block.blockData = data
            // If it was a tile entity, we might need to restore state too, but BlockData covers most visuals.
            // For chests etc, we might need BlockState.
            if (changedBlockStates.containsKey(loc)) {
                changedBlockStates[loc]?.update(true, false)
            }
        }

        changedBlocks.clear()
        changedBlockStates.clear()
        
        plugin.logger.info("Arena rolled back.")
    }
    
    fun clearRecords() {
        changedBlocks.clear()
        changedBlockStates.clear()
    }
}
