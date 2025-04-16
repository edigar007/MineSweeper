package com.edigar.minesweeper

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.edigar.minesweeper.GameDifficulty
import com.edigar.minesweeper.GameStatus

/**
 * 游戏状态管理器 - 负责保存和加载游戏进度
 */
class GameStateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 保存游戏状态
     */
    fun saveGameState(gameState: GameState) {
        prefs.edit {
            // 保存游戏难度
            putString(KEY_DIFFICULTY, gameState.difficulty.name)
            
            // 保存游戏状态基本信息
            putInt(KEY_GAME_TIME, gameState.gameTime)
            putInt(KEY_FLAGS_REMAINING, gameState.flagsRemaining)
            putBoolean(KEY_IS_FIRST_CLICK, gameState.isFirstClick)
            putString(KEY_GAME_STATUS, gameState.gameStatus.name)
            
            // 将格子数组转换为JSON并保存
            putString(KEY_GRID_STATE, gson.toJson(gameState.gridState))
            
            // 保存时间戳，用于检查游戏是否过期
            putLong(KEY_SAVED_TIMESTAMP, System.currentTimeMillis())
        }
    }
    
    /**
     * 加载游戏状态
     * @return 返回保存的游戏状态，如果没有保存或保存过期则返回null
     */
    fun loadGameState(): GameState? {
        // 检查是否有保存的游戏
        if (!hasSavedGame()) {
            return null
        }
        
        // 检查游戏是否过期
        val savedTimestamp = prefs.getLong(KEY_SAVED_TIMESTAMP, 0L)
        if (System.currentTimeMillis() - savedTimestamp > MAX_SAVE_AGE_MS) {
            clearSavedGame()
            return null
        }
          try {
            // 加载游戏难度
            val difficultyName = prefs.getString(KEY_DIFFICULTY, GameDifficulty.EASY.name) ?: GameDifficulty.EASY.name
            val difficulty = GameDifficulty.valueOf(difficultyName)
            
            // 加载游戏基本信息
            val gameTime = prefs.getInt(KEY_GAME_TIME, 0)
            val flagsRemaining = prefs.getInt(KEY_FLAGS_REMAINING, difficulty.mines)
            val isFirstClick = prefs.getBoolean(KEY_IS_FIRST_CLICK, true)
            val gameStatusName = prefs.getString(KEY_GAME_STATUS, GameStatus.PLAYING.name) ?: GameStatus.PLAYING.name
            val gameStatus = GameStatus.valueOf(gameStatusName)
            
            // 加载格子状态
            val gridJson = prefs.getString(KEY_GRID_STATE, null) ?: return null
            val gridType = object : TypeToken<List<List<MineCellState>>>() {}.type
            val gridState: List<List<MineCellState>> = gson.fromJson(gridJson, gridType)
            
            return GameState(
                difficulty = difficulty,
                gameStatus = gameStatus,
                gameTime = gameTime,
                flagsRemaining = flagsRemaining,
                isFirstClick = isFirstClick,
                gridState = gridState
            )
        } catch (e: Exception) {
            // 如果解析出错，清除保存的游戏并返回null
            clearSavedGame()
            return null
        }
    }
    
    /**
     * 检查是否有已保存的游戏
     */
    fun hasSavedGame(): Boolean {
        return prefs.contains(KEY_GRID_STATE)
    }
    
    /**
     * 清除已保存的游戏
     */
    fun clearSavedGame() {
        prefs.edit {
            remove(KEY_DIFFICULTY)
            remove(KEY_GAME_TIME)
            remove(KEY_FLAGS_REMAINING)
            remove(KEY_IS_FIRST_CLICK)
            remove(KEY_GAME_STATUS)
            remove(KEY_GRID_STATE)
            remove(KEY_SAVED_TIMESTAMP)
        }
    }
    
    companion object {
        private const val PREFS_NAME = "minesweeper_game_state"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_GAME_TIME = "game_time"
        private const val KEY_FLAGS_REMAINING = "flags_remaining"
        private const val KEY_IS_FIRST_CLICK = "is_first_click"
        private const val KEY_GAME_STATUS = "game_status"
        private const val KEY_GRID_STATE = "grid_state"
        private const val KEY_SAVED_TIMESTAMP = "saved_timestamp"
        
        // 保存的游戏最长有效期（3天）
        private const val MAX_SAVE_AGE_MS = 3 * 24 * 60 * 60 * 1000L
    }
}

/**
 * 游戏状态数据类 - 用于序列化和反序列化游戏状态
 */
data class GameState(
    val difficulty: GameDifficulty,
    val gameStatus: GameStatus,
    val gameTime: Int,
    val flagsRemaining: Int,
    val isFirstClick: Boolean,
    val gridState: List<List<MineCellState>>
)

/**
 * 单元格状态数据类 - 用于序列化和反序列化单元格状态
 */
data class MineCellState(
    val row: Int,
    val col: Int,
    val isMine: Boolean,
    val adjacentMines: Int,
    val isRevealed: Boolean,
    val isFlagged: Boolean,
    val isQuestionMarked: Boolean
)
