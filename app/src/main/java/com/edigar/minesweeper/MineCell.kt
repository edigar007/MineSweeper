package com.edigar.minesweeper

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Represents the state of a single cell in the Minesweeper grid
@Stable // Optimize Compose recomposition
data class MineCell(
    val row: Int,
    val col: Int,
) {
    // 所有状态属性都使用 mutableStateOf，确保变化时触发UI更新
    var isMine by mutableStateOf(false)
    var adjacentMines by mutableStateOf(0)
    var isRevealed by mutableStateOf(false)
    var isFlagged by mutableStateOf(false)
    var isQuestionMarked by mutableStateOf(false) // 新增问号标记状态
}
