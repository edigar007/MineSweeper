package com.edigar.minesweeper

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

// Enum to represent the game status
enum class GameStatus {
    PLAYING,
    WON,
    LOST
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    
    // 游戏状态管理器
    private val gameStateManager = GameStateManager(application.applicationContext)

    // Use mutableStateListOf for the grid to observe changes in the list structure and cell states
    val grid = mutableStateListOf<List<MineCell>>()
    
    // 添加游戏难度状态，使用不同的命名避免与方法冲突
    var currentDifficulty by mutableStateOf(CURRENT_DIFFICULTY)
        private set
    
    // Add state for game status (playing, won, lost)
    var gameStatus by mutableStateOf(GameStatus.PLAYING)
        private set // 只允许 ViewModel 内部修改状态
    
    // Add state for remaining flags count
    var flagsRemaining by mutableIntStateOf(MINE_COUNT)
        private set // 只允许 ViewModel 内部修改数量
        
    // 计时器状态，记录游戏时间（秒）
    var gameTime by mutableIntStateOf(0)
        private set
        
    // 是否是第一次点击（第一次点击时才开始计时）
    private var isFirstClick = true
    
    // 公开的属性，指示游戏是否已经开始（非第一次点击）
    val isGameStarted: Boolean
        get() = !isFirstClick
    
    // 计时器任务
    private var timerJob: Job? = null
    
    // 撤销功能相关属性
    private var previousGameState: GameStateUndo? = null  // 保存上一步的游戏状态
    var canUndo by mutableStateOf(false)  // 是否可以撤销操作
        private set
        
    // 记录当前游戏是否已使用过第二次机会
    private var hasUsedSecondChance = false
    
    // 提示功能相关状态
    // 每局游戏的默认提示次数
    private val DEFAULT_HINTS_COUNT = 3
    
    // 剩余提示次数
    var hintsRemaining by mutableIntStateOf(DEFAULT_HINTS_COUNT)
        private set
        
    // 当前高亮的安全格子
    var highlightedSafeCell by mutableStateOf<MineCell?>(null)
        private set
        
    // 高亮格子是否可见（用于UI观察）
    var isSafeCellHighlighted by mutableStateOf(false)
        private set
    
    init {
        // 尝试加载保存的游戏状态，如果没有则创建新游戏
        if (!loadSavedGameState()) {
            initializeGame()
        }
    }
      // 设置游戏难度并重新开始游戏
    fun setDifficulty(newDifficulty: GameDifficulty) {
        if (currentDifficulty != newDifficulty) {
            // 先停止计时器
            stopTimer()
            
            // 更新难度相关参数
            currentDifficulty = newDifficulty
            CURRENT_DIFFICULTY = newDifficulty
            GRID_ROWS = newDifficulty.rows
            GRID_COLS = newDifficulty.cols
            MINE_COUNT = newDifficulty.mines
              // 重置所有游戏状态
            gameStatus = GameStatus.PLAYING
            flagsRemaining = MINE_COUNT
            gameTime = 0
            isFirstClick = true
            hasUsedSecondChance = false // 重置第二次机会使用状态
            
            // 重置提示状态
            hintsRemaining = DEFAULT_HINTS_COUNT
            clearHighlightedCell()
            
            // 创建全新的网格（这是关键）
            recreateGrid()
        }
    }
    
    // 启动计时器
    private fun startTimer() {
        // 如果计时器已经在运行，先取消
        stopTimer()
        
        // 使用协程启动计时器
        timerJob = viewModelScope.launch {
            while(isActive && gameStatus == GameStatus.PLAYING) {
                delay(1000) // 每秒更新一次
                gameTime++
            }
        }
    }
      // 停止计时器
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        
        // 当计时器停止时自动保存游戏状态
        if (gameStatus == GameStatus.PLAYING && !isFirstClick) {
            saveGameState()
        }
    }fun initializeGame() {
        // 停止计时器
        stopTimer()
          // 重置游戏状态和旗子数量
        gameStatus = GameStatus.PLAYING
        flagsRemaining = MINE_COUNT
        gameTime = 0
        isFirstClick = true
        hasUsedSecondChance = false // 重置第二次机会使用状态
        
        // 重置提示状态
        hintsRemaining = DEFAULT_HINTS_COUNT
        clearHighlightedCell()
        
        // 完全清除网格，创建新的
        grid.clear()
        
        // 使用协程确保UI完全刷新
        viewModelScope.launch {
            // 强制界面先将网格清空
            delay(50) // 增加延迟，确保UI响应
            
            // 创建全新网格
            val freshGrid = List(GRID_ROWS) { row ->
                List(GRID_COLS) { col ->
                    MineCell(row = row, col = col)
                }
            }
            
            // 添加新网格
            grid.addAll(freshGrid)
        }
    }    // 创建一个专门的方法来重新创建网格
    // 这确保所有网格相关的状态都被重置
    private fun recreateGrid() {
        // 先完全清除现有网格 - 这会触发UI更新，清空显示
        grid.clear()
        
        // 使用协程确保UI线程能够完成清除操作后再创建新网格
        viewModelScope.launch {
            // 增加延迟时间，确保UI有足够时间响应清空操作
            delay(50)
            
            try {
                // 完全使用新对象来创建网格，避免任何状态残留
                val freshGrid = List(GRID_ROWS) { row ->
                    List(GRID_COLS) { col ->
                        // 简单创建对象，让默认构造函数处理初始化
                        MineCell(row = row, col = col)
                    }
                }
                
                // 将新创建的网格添加到可观察集合中
                grid.addAll(freshGrid)
                
                // 取消以下额外的重组步骤，因为它可能导致闪烁
                // 如果UI仍未更新，再次尝试启用此步骤
                // val tempGrid = grid.toList()
                // grid.clear()
                // grid.addAll(tempGrid)
            } catch (e: Exception) {
                // 捕获任何可能的异常，防止应用崩溃
                println("Error recreating grid: ${e.message}")
                // 发生错误时创建空白网格
                grid.addAll(emptyList())
            }
        }
    }
      // 新方法：在首次点击后生成地雷
    private fun placeMinesAfterFirstClick(firstClickRow: Int, firstClickCol: Int) {
        val safeRadius = 1 // 定义安全区域半径（可以调整）
        
        // 创建一个安全区域集合，包括首次点击位置及其周围的格子
        val safePositions = mutableSetOf<Pair<Int, Int>>()
        for (r in (firstClickRow - safeRadius)..(firstClickRow + safeRadius)) {
            for (c in (firstClickCol - safeRadius)..(firstClickCol + safeRadius)) {
                if (r >= 0 && r < GRID_ROWS && c >= 0 && c < GRID_COLS) {
                    safePositions.add(Pair(r, c))
                }
            }
        }
        
        // 放置地雷，避开安全区域
        var minesPlaced = 0
        var maxAttempts = GRID_ROWS * GRID_COLS * 10 // 防止无限循环
        var attempts = 0
        
        // 确保可以放置足够的地雷
        val maxPossibleMines = GRID_ROWS * GRID_COLS - safePositions.size
        val actualMineCount = if (MINE_COUNT > maxPossibleMines) maxPossibleMines else MINE_COUNT
        
        while (minesPlaced < actualMineCount && attempts < maxAttempts) {
            attempts++
            val r = Random.nextInt(GRID_ROWS)
            val c = Random.nextInt(GRID_COLS)
            val position = Pair(r, c)
            
            // 只在非安全区域放置地雷
            if (!safePositions.contains(position) && !grid[r][c].isMine) {
                grid[r][c].isMine = true
                minesPlaced++
            }
        }
        
        // 重新计算每个格子周围的地雷数量
        for (r in 0 until GRID_ROWS) {
            for (c in 0 until GRID_COLS) {
                if (!grid[r][c].isMine) {
                    grid[r][c].adjacentMines = countAdjacentMines(grid, r, c)
                }
            }
        }
    }

    // Helper function to count adjacent mines
    private fun countAdjacentMines(currentGrid: List<List<MineCell>>, row: Int, col: Int): Int {
        var count = 0
        for (r in (row - 1)..(row + 1)) {
            for (c in (col - 1)..(col + 1)) {
                // Check bounds and ignore the cell itself
                if (r >= 0 && r < GRID_ROWS && c >= 0 && c < GRID_COLS && !(r == row && c == col)) {
                    if (currentGrid[r][c].isMine) {
                        count++
                    }
                }
            }
        }
        return count
    }    // Implement function to handle cell click (reveal)
    fun onCellClick(cell: MineCell) {
        // 如果游戏已结束、单元格已经被揭开或标记了旗子，则忽略点击
        // 问号标记的单元格可以被点击揭开，所以不需要检查isQuestionMarked
        if (gameStatus != GameStatus.PLAYING || cell.isRevealed || cell.isFlagged) {
            return
        }
        
        // 如果不是第一次点击，保存当前状态用于撤销功能
        if (!isFirstClick) {
            saveStateForUndo()
        }
        
        // 如果单元格有问号标记，点击时先清除问号标记
        if (cell.isQuestionMarked) {
            cell.isQuestionMarked = false
        }
        
        // 如果是第一次点击，确保安全首击并启动计时器
        if (isFirstClick) {
            // 在第一次点击后生成地雷，保证首次点击及其周围区域安全
            placeMinesAfterFirstClick(cell.row, cell.col)
            isFirstClick = false
            startTimer()
        }

        // 揭开单元格
        cell.isRevealed = true        // 由于安全首击，第一次点击不可能是地雷
        // 但为了代码完整性，仍然保留地雷判断逻辑
        if (cell.isMine) {
            // 踩到地雷，游戏结束
            gameStatus = GameStatus.LOST
            stopTimer() // 停止计时器
            
            // 根据是否有第二次机会决定如何显示地雷
            revealAllMines(if (hasUsedSecondChance) null else cell)
            
            // 清除保存的游戏（游戏结束不需要保存）
            clearSavedGame()
            
            // 如果已经使用过第二次机会，则禁用撤销功能
            if (hasUsedSecondChance) {
                canUndo = false
            }
            // 否则保留撤销功能（用于第二次机会）
        } else {
            // 如果是空白单元格（周围没有地雷），递归揭开相邻单元格
            if (cell.adjacentMines == 0) {
                revealNeighbors(cell.row, cell.col)
            }
            
            // 检查是否获胜（所有非地雷单元格都已揭开）
            checkWinCondition()
            
            // 保存游戏状态（每次操作后保存）
            saveGameState()
        }
    }

    // 递归函数：揭开空白单元格周围的单元格
    private fun revealNeighbors(row: Int, col: Int) {
        for (r in (row - 1)..(row + 1)) {
            for (c in (col - 1)..(col + 1)) {
                if (r >= 0 && r < GRID_ROWS && c >= 0 && c < GRID_COLS && !(r == row && c == col)) {
                    val neighbor = grid[r][c]
                    
                    // 只揭开未揭开且未标记旗子的单元格
                    if (!neighbor.isRevealed && !neighbor.isFlagged) {
                        neighbor.isRevealed = true
                        
                        // 如果这个相邻单元格也是空白的，继续递归
                        if (neighbor.adjacentMines == 0) {
                            revealNeighbors(r, c)
                        }
                    }
                }
            }
        }
    }    // Implement function to handle cell long press (flag/question mark)
    fun onCellLongPress(cell: MineCell) {
        // 如果游戏已结束或单元格已揭开，则忽略长按
        if (gameStatus != GameStatus.PLAYING || cell.isRevealed) {
            return
        }

        // 实现三态循环切换：未标记 -> 旗子 -> 问号 -> 未标记
        when {
            // 如果已经标记了旗子，切换到问号状态
            cell.isFlagged -> {
                cell.isFlagged = false
                cell.isQuestionMarked = true
                flagsRemaining++ // 移除旗子时增加可用旗子数
            }
            // 如果已经标记了问号，切换到未标记状态
            cell.isQuestionMarked -> {
                cell.isQuestionMarked = false
                // 不需要修改旗子数量，因为问号不消耗旗子
            }
            // 如果未标记，尝试标记旗子（如果有剩余旗子）
            else -> {
                if (flagsRemaining > 0) {
                    cell.isFlagged = true
                    flagsRemaining--
                } else {
                    // 如果没有剩余旗子，直接切换到问号状态
                    cell.isQuestionMarked = true
                }
            }
        }
        
        // 一般来说，只在揭开单元格时检查获胜条件，但如果想要在标记旗子后也检查，可以取消下面的注释
        // checkWinCondition()
    }

    // 检查是否满足获胜条件
    private fun checkWinCondition() {
        if (gameStatus != GameStatus.PLAYING) return // 已经赢了或输了，不需要再检查

        var revealedNonMines = 0
        val totalNonMines = GRID_ROWS * GRID_COLS - MINE_COUNT
        
        for (rowList in grid) {
            for (cell in rowList) {
                if (cell.isRevealed && !cell.isMine) {
                    revealedNonMines++
                }
            }
        }
        
        if (revealedNonMines == totalNonMines) {
            // 所有非地雷单元格都已揭开，游戏胜利
            gameStatus = GameStatus.WON
            // 停止计时器
            stopTimer()
            // 自动标记所有地雷（可选）
            flagAllMines()
        }
    }    // 游戏失败时揭开地雷
    // 如果是第二次机会情况（hasUsedSecondChance为false），只揭开当前踩到的地雷
    // 如果没有第二次机会（hasUsedSecondChance为true），揭开所有地雷
    private fun revealAllMines(currentMineCell: MineCell? = null) {
        if (hasUsedSecondChance || currentMineCell == null) {
            // 已经使用过第二次机会或没有指定当前地雷，直接显示所有地雷
            for (rowList in grid) {
                for (cell in rowList) {
                    if (cell.isMine) {
                        cell.isRevealed = true
                    }
                }
            }
        } else {
            // 只显示当前踩到的地雷，给予第二次机会
            currentMineCell.isRevealed = true
        }
    }// 游戏胜利时自动标记所有地雷（可选）
    private fun flagAllMines() {
        for (rowList in grid) {
            for (cell in rowList) {
                if (cell.isMine && !cell.isFlagged) {
                    cell.isFlagged = true
                    // 不需要更新 flagsRemaining，因为游戏已经结束
                }
            }
        }
        // 将剩余旗子数量设为 0
        flagsRemaining = 0
    }
    
    // 长按已揭开的格子时触发的功能
    fun onRevealedCellLongPress(cell: MineCell) {
        // 如果游戏已结束，或格子没有揭开，或格子没有显示数字(空白格子)，则忽略操作
        if (gameStatus != GameStatus.PLAYING || !cell.isRevealed || cell.adjacentMines == 0) {
            return
        }
        
        // 计算周围已标记旗子的数量
        var flaggedCount = 0
        val neighbors = getNeighborCells(cell.row, cell.col)
        
        for (neighbor in neighbors) {
            if (neighbor.isFlagged) {
                flaggedCount++
            }
        }
        
        // 如果周围旗子数量等于格子数字，自动揭开周围未标记旗子的格子
        if (flaggedCount == cell.adjacentMines) {
            for (neighbor in neighbors) {
                if (!neighbor.isFlagged && !neighbor.isRevealed) {
                    // 使用现有的点击逻辑来揭开格子
                    onCellClick(neighbor)
                }
            }
        }
    }
    
    // 获取指定坐标周围的格子列表
    private fun getNeighborCells(row: Int, col: Int): List<MineCell> {
        val neighbors = mutableListOf<MineCell>()
        
        for (r in (row - 1)..(row + 1)) {
            for (c in (col - 1)..(col + 1)) {
                // 检查边界并忽略中心格子本身
                if (r >= 0 && r < GRID_ROWS && c >= 0 && c < GRID_COLS && !(r == row && c == col)) {
                    neighbors.add(grid[r][c])
                }
            }
        }
        
        return neighbors
    }
    
    // 自动保存游戏状态
    private fun saveGameState() {
        // 仅在游戏进行中时保存状态
        if (gameStatus == GameStatus.PLAYING && !isFirstClick) {
            // 创建格子状态的副本
            val cellStates = grid.map { row ->
                row.map { cell ->
                    MineCellState(
                        row = cell.row,
                        col = cell.col,
                        isMine = cell.isMine,
                        adjacentMines = cell.adjacentMines,
                        isRevealed = cell.isRevealed,
                        isFlagged = cell.isFlagged,
                        isQuestionMarked = cell.isQuestionMarked
                    )
                }
            }
              // 创建完整游戏状态
            val gameState = GameState(
                difficulty = currentDifficulty,
                gameStatus = gameStatus,
                gameTime = gameTime,
                flagsRemaining = flagsRemaining,
                isFirstClick = isFirstClick,
                hintsRemaining = hintsRemaining, // 保存提示次数
                gridState = cellStates
            )
            
            // 保存状态
            gameStateManager.saveGameState(gameState)
        }
    }
    
    // 尝试加载保存的游戏状态
    private fun loadSavedGameState(): Boolean {
        val savedState = gameStateManager.loadGameState()
        
        // 如果没有保存的状态，返回false
        if (savedState == null) {
            return false
        }
        
        try {
            // 停止计时器
            stopTimer()
            
            // 恢复难度设置
            currentDifficulty = savedState.difficulty
            CURRENT_DIFFICULTY = savedState.difficulty
            GRID_ROWS = savedState.difficulty.rows
            GRID_COLS = savedState.difficulty.cols
            MINE_COUNT = savedState.difficulty.mines
              // 恢复游戏状态
            gameStatus = savedState.gameStatus
            flagsRemaining = savedState.flagsRemaining
            gameTime = savedState.gameTime
            isFirstClick = savedState.isFirstClick
            hintsRemaining = savedState.hintsRemaining // 恢复提示次数
            
            // 清空当前网格
            grid.clear()
            
            // 重建网格并恢复单元格状态
            val restoredGrid = List(GRID_ROWS) { row ->
                List(GRID_COLS) { col ->
                    // 尝试找到对应的保存状态
                    val cellState = savedState.gridState.getOrNull(row)?.getOrNull(col)
                    
                    if (cellState != null) {
                        // 恢复格子状态
                        MineCell(row = row, col = col).apply {
                            isMine = cellState.isMine
                            adjacentMines = cellState.adjacentMines
                            isRevealed = cellState.isRevealed
                            isFlagged = cellState.isFlagged
                            isQuestionMarked = cellState.isQuestionMarked
                        }
                    } else {
                        // 如果找不到对应状态，创建新格子
                        MineCell(row = row, col = col)
                    }
                }
            }
            
            // 更新网格
            grid.addAll(restoredGrid)
            
            // 如果游戏正在进行且不是第一次点击，重新启动计时器
            if (gameStatus == GameStatus.PLAYING && !isFirstClick) {
                startTimer()
            }
            
            return true
        } catch (e: Exception) {
            // 如果恢复过程出错，清理保存的状态并返回false
            gameStateManager.clearSavedGame()
            return false
        }
    }
    
    // 清除保存的游戏状态
    fun clearSavedGame() {
        gameStateManager.clearSavedGame()
    }

    // 检查是否有保存的游戏
    fun hasSavedGame(): Boolean = gameStateManager.hasSavedGame()

    // 保存当前游戏状态用于撤销（每次操作前调用）
    private fun saveStateForUndo() {
        if (gameStatus == GameStatus.PLAYING && !isFirstClick) {
            // 创建当前网格状态的深拷贝
            val currentGridState = grid.map { row ->
                row.map { cell ->
                    CellState(
                        row = cell.row,
                        col = cell.col,
                        isMine = cell.isMine,
                        adjacentMines = cell.adjacentMines,
                        isRevealed = cell.isRevealed,
                        isFlagged = cell.isFlagged,
                        isQuestionMarked = cell.isQuestionMarked
                    )
                }
            }
            
            // 保存当前游戏状态
            previousGameState = GameStateUndo(
                gridState = currentGridState,
                flagsRemaining = flagsRemaining,
                gameTime = gameTime
            )
            
            // 启用撤销功能
            canUndo = true
        }
    }    // 撤销上一步操作（每局游戏只能使用一次）
    fun undoLastMove() {
        val prevState = previousGameState ?: return
        
        // 恢复旗子数量和游戏时间
        flagsRemaining = prevState.flagsRemaining
        gameTime = prevState.gameTime
        
        // 恢复网格状态
        for (r in grid.indices) {
            for (c in 0 until grid[r].size) {
                val currentCell = grid[r][c]
                val prevCell = prevState.gridState[r][c]
                
                // 更新每个单元格的状态
                currentCell.isRevealed = prevCell.isRevealed
                currentCell.isFlagged = prevCell.isFlagged
                currentCell.isQuestionMarked = prevCell.isQuestionMarked
            }
        }
        
        // 将游戏状态恢复为进行中
        gameStatus = GameStatus.PLAYING
        
        // 标记已使用过第二次机会
        hasUsedSecondChance = true
        
        // 重新启动计时器
        startTimer()
        
        // 禁用撤销功能，因为已经撤销了一步
        previousGameState = null
        canUndo = false
    }
    
    // 使用提示功能，高亮显示一个安全格子，优先选择与已揭开数字格子相邻的格子
    fun useHint() {
        // 如果游戏已结束或还未开始（第一次点击前）或没有提示次数，则不执行操作
        if (gameStatus != GameStatus.PLAYING || isFirstClick || hintsRemaining <= 0) {
            return
        }
        
        // 寻找并高亮显示一个有策略意义的安全格子
        val safeCell = findStrategicSafeCell()
        
        if (safeCell != null) {
            // 清除之前的高亮
            highlightedSafeCell = null
            
            // 设置新的高亮格子
            highlightedSafeCell = safeCell
            isSafeCellHighlighted = true
            
            // 减少提示次数
            hintsRemaining--
        }
        
        // 在10秒后自动取消高亮
        viewModelScope.launch {
            delay(10000) // 10秒
            clearHighlightedCell()
        }
    }
    
    // 寻找一个战略性的安全格子（优先选择与已揭开的数字格子相邻的安全格子）
    private fun findStrategicSafeCell(): MineCell? {
        // 优先找到所有已揭开且有数字的格子
        val revealedNumberCells = mutableListOf<MineCell>()
        
        for (rowList in grid) {
            for (cell in rowList) {
                // 找出已揭开且有数字的格子
                if (cell.isRevealed && cell.adjacentMines > 0) {
                    revealedNumberCells.add(cell)
                }
            }
        }
        
        // 收集所有与已揭开数字格子相邻的安全格子
        val adjacentSafeCells = mutableListOf<MineCell>()
        
        // 随机打乱已揭开数字格子的顺序，增加提示的随机性
        revealedNumberCells.shuffle()
        
        // 遍历每个已揭开的数字格子
        for (numberCell in revealedNumberCells) {
            // 获取其相邻的格子
            val neighbors = getNeighborCells(numberCell.row, numberCell.col)
            
            // 筛选出安全的未揭开格子
            for (neighbor in neighbors) {
                if (!neighbor.isRevealed && !neighbor.isMine && !neighbor.isFlagged && !neighbor.isQuestionMarked) {
                    // 如果是安全的未揭开格子，添加到候选列表
                    adjacentSafeCells.add(neighbor)
                }
            }
        }
        
        // 如果找到了相邻的安全格子，随机选择一个
        if (adjacentSafeCells.isNotEmpty()) {
            return adjacentSafeCells[Random.nextInt(adjacentSafeCells.size)]
        }
        
        // 如果没有找到相邻的安全格子，退回到原来的随机选择方法
        return findRandomSafeCell()
    }

    // 随机选择一个安全格子（备选方案，当没有相邻安全格子时使用）
    private fun findRandomSafeCell(): MineCell? {
        // 收集所有符合条件的安全格子
        val safeCells = mutableListOf<MineCell>()
        
        for (rowList in grid) {
            for (cell in rowList) {
                // 安全格子：未揭开、非地雷、未标记旗子或问号
                if (!cell.isRevealed && !cell.isMine && !cell.isFlagged && !cell.isQuestionMarked) {
                    safeCells.add(cell)
                }
            }
        }
        
        // 如果找到安全格子，随机选择一个
        return if (safeCells.isNotEmpty()) {
            safeCells[Random.nextInt(safeCells.size)]
        } else {
            null // 如果没有安全格子（极少数情况），返回null
        }
    }

    // 清除高亮的安全格子
    private fun clearHighlightedCell() {
        highlightedSafeCell = null
        isSafeCellHighlighted = false
    }
}

// 游戏状态数据类，用于保存游戏状态供撤销功能使用
data class GameStateUndo(
    val gridState: List<List<CellState>>,
    val flagsRemaining: Int,
    val gameTime: Int
)

// 单元格状态数据类，仅包含必要的状态信息
data class CellState(
    val row: Int,
    val col: Int,
    val isMine: Boolean,
    val adjacentMines: Int,
    val isRevealed: Boolean,
    val isFlagged: Boolean,
    val isQuestionMarked: Boolean
)
