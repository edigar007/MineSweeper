package com.edigar.minesweeper

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// import androidx.activity.viewModels // Import viewModels delegate (Alternative way)
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi // For combinedClickable
import androidx.compose.foundation.background // 添加background导入
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edigar.minesweeper.ui.theme.MineSweeperTheme

// 游戏难度枚举
enum class GameDifficulty(val rows: Int, val cols: Int, val mines: Int, val displayName: String) {
    EASY(8, 8, 10, "初级"),
    MEDIUM(10, 10, 20, "中级"),
    HARD(12, 12, 30, "高级")
}

// 默认游戏常量 - 现在从枚举获取
var CURRENT_DIFFICULTY = GameDifficulty.EASY // 默认难度设置为 EASY
var GRID_ROWS = CURRENT_DIFFICULTY.rows
var GRID_COLS = CURRENT_DIFFICULTY.cols  
var MINE_COUNT = CURRENT_DIFFICULTY.mines

// 添加触感反馈函数（非Composable函数）
fun performHapticFeedback(context: android.content.Context) {
    try {
        // 使用振动反馈表示操作成功
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
            vibrator?.let {
                // 轻微振动 (10毫秒)
                val vibrationEffect = VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                it.vibrate(vibrationEffect)
            }
        } else {
            // 老版本Android的振动方式
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(ComponentActivity.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(10)
        }
    } catch (e: Exception) {
        // 捕获任何可能的异常，防止应用崩溃
        // 在实际生产环境中，你可能还想记录这个错误
        e.printStackTrace()
    }
}

class MainActivity : ComponentActivity() {
    // Get a reference to the ViewModel using the viewModels delegate (Alternative)
    // private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MineSweeperTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Provide the ViewModel instance to the composable
                    MineSweeperGame(
                        modifier = Modifier.padding(innerPadding)
                        // viewModel = gameViewModel // Pass the ViewModel instance if using viewModels() delegate
                    )
                }
            }
        }
    }
}

// New Composable for the main game screen
@OptIn(ExperimentalFoundationApi::class) // Needed for combinedClickable
@Composable
fun MineSweeperGame(
    modifier: Modifier = Modifier,
    gameViewModel: GameViewModel = viewModel() // Get ViewModel instance using compose function
) {
    // 获取游戏状态和剩余旗子数量以及游戏时间
    val gameStatus = gameViewModel.gameStatus
    val flagsRemaining = gameViewModel.flagsRemaining
    val gameTime = gameViewModel.gameTime
    // 获取是否可以撤销操作
    val canUndo = gameViewModel.canUndo
    
    // 创建垂直滚动状态
    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(verticalScrollState), // 添加垂直滚动
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 显示游戏标题
        Text(text = "扫雷游戏", fontSize = 22.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        // 显示游戏状态信息（游戏状态、剩余旗子数量）
        val statusText = when (gameStatus) {
            GameStatus.PLAYING -> "剩余旗子: $flagsRemaining"
            GameStatus.WON -> "恭喜你赢了！🎉"
            GameStatus.LOST -> "游戏结束 💣"
        }
        Text(text = statusText, fontSize = 18.sp)
        
        // 当游戏失败且可以撤销时，显示第二次机会按钮
        if (gameStatus == GameStatus.LOST && canUndo) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { gameViewModel.undoLastMove() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE91E63) // 粉红色按钮，更醒目
                )
            ) {
                Text("第二次机会", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "你可以撤销最后一步，避开地雷",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        
        // 显示游戏时间
        val minutes = gameTime / 60
        val seconds = gameTime % 60
        val timeText = String.format("时间: %02d:%02d", minutes, seconds)
        Text(text = timeText, fontSize = 16.sp)
        
        // 显示当前难度
        Text(
            text = "当前难度: ${gameViewModel.currentDifficulty.displayName}",
            fontSize = 16.sp
        )
        
        // 添加难度选择按钮
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            GameDifficulty.values().forEach { difficulty ->
                Button(
                    onClick = { gameViewModel.setDifficulty(difficulty) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameViewModel.currentDifficulty == difficulty) 
                            Color(0xFF3F51B5) // 高亮显示当前选中的难度
                        else 
                            ButtonDefaults.buttonColors().containerColor
                    )
                ) {
                    Text(difficulty.displayName, fontSize = 14.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        MineSweeperGrid(viewModel = gameViewModel)
        
        Spacer(modifier = Modifier.height(16.dp))

        // 重置按钮
        Button(onClick = { gameViewModel.initializeGame() }) {
            Text("重新开始")
        }
    }
}

// New Composable for the grid itself
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MineSweeperGrid(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel
) {
    // 创建水平滚动状态
    val horizontalScrollState = rememberScrollState()
    
    // 整个网格可以作为一个整体进行水平滚动
    Box(
        modifier = modifier
            .horizontalScroll(horizontalScrollState)
    ) {
        // 网格内容
        Column {
            viewModel.grid.forEach { rowList ->
                Row {
                    rowList.forEach { cell ->
                        MineCellView(cell = cell, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// Composable for rendering a single cell
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MineCellView(cell: MineCell, viewModel: GameViewModel) {
    // 创建交互源，用于检测按下状态
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 检查游戏是否处于进行中状态，决定单元格是否可交互
    val interactionEnabled = viewModel.gameStatus == GameStatus.PLAYING
    
    // 根据单元格状态确定背景颜色
    val baseBackgroundColor = when {
        !cell.isRevealed -> ButtonDefaults.buttonColors().containerColor // 未揭开：默认按钮颜色
        cell.isMine -> Color.Red // 是地雷：红色
        else -> Color.LightGray // 已揭开且不是地雷：灰色
    }
    
    // 按下时改变颜色（加深或变亮）
    val backgroundColor = if (isPressed && interactionEnabled && (!cell.isRevealed || cell.isFlagged)) {
        // 按下时颜色变化（这里使颜色变亮）
        baseBackgroundColor.copy(alpha = 0.7f)
    } else {
        baseBackgroundColor
    }
    
    // 根据单元格状态确定文本颜色
    val textColor = when {
        cell.isFlagged || cell.isQuestionMarked -> Color.Black // 有旗子或问号：黑色
        !cell.isRevealed -> Color.Transparent // 未揭开且无标记：透明（不显示）
        cell.isMine -> Color.Black // 是地雷：黑色
        cell.adjacentMines > 0 -> numberColor(cell.adjacentMines) // 有数字：根据数字确定颜色
        else -> Color.Transparent // 空白单元格：透明（不显示）
    }
    
    // 根据单元格状态确定显示的文本
    val text = when {
        cell.isFlagged -> "🚩" // 有旗子：显示旗子
        cell.isQuestionMarked -> "❓" // 有问号：显示问号
        !cell.isRevealed -> "" // 未揭开且无标记：空文本
        cell.isMine -> "💣" // 是地雷：显示地雷
        cell.adjacentMines > 0 -> cell.adjacentMines.toString() // 有数字：显示数字
        else -> "" // 空白单元格：空文本
    }
    
    // 使用animateFloatAsState来创建平滑的动画效果
    val scale by animateFloatAsState(
        targetValue = if (isPressed && interactionEnabled && (!cell.isRevealed || cell.isFlagged)) 0.85f else 1f,
        animationSpec = tween(durationMillis = 150), // 调整动画持续时间，让效果更明显
        label = "ScaleAnimation"
    )
    
    // 提前获取Context，这样不会在lambda中调用Composable函数
    val context = LocalContext.current
    
    // 使用Box替代Button，并把combinedClickable直接放在Box上
    Box(
        modifier = Modifier
            .size(44.dp) // 增加方块尺寸，更容易点击
            .padding(1.dp)
            // 使用scale修饰符应用动画效果，这次使用animateFloatAsState的结果
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource, // 使用交互源
                indication = null, // 禁用默认的水波纹指示器，我们使用自定义动画
                // 修改enabled条件：允许已揭开的数字格子也能响应长按
                enabled = interactionEnabled && (
                    !cell.isRevealed || 
                    cell.isFlagged || 
                    (cell.isRevealed && cell.adjacentMines > 0)
                ),
                onClick = { 
                    // 点击处理 - 揭开单元格
                    if (interactionEnabled && !cell.isRevealed && !cell.isFlagged) {
                        // 使用预先获取的context
                        performHapticFeedback(context)
                        viewModel.onCellClick(cell)
                    }
                },
                onLongClick = { 
                    // 长按处理 - 对未揭开的格子插旗/取消插旗，对已揭开的数字格子执行快速揭开
                    if (interactionEnabled) {
                        // 使用预先获取的context
                        performHapticFeedback(context)
                        if (cell.isRevealed && cell.adjacentMines > 0) {
                            // 已揭开的数字格子 - 尝试快速揭开周围格子
                            viewModel.onRevealedCellLongPress(cell)
                        } else if (!cell.isRevealed || cell.isFlagged) {
                            // 未揭开的格子或有旗子的格子 - 标记/取消标记旗子
                            viewModel.onCellLongPress(cell)
                        }
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 创建一个内部Box用于显示背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(4.dp) // 圆角效果，类似Button
                )
        )
        
        // 添加文本在中间
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp
        )
    }
}

// Helper function to get color for adjacent mine count
fun numberColor(count: Int): Color {
    return when (count) {
        1 -> Color.Blue
        2 -> Color(0xFF008000) // Green
        3 -> Color.Red
        4 -> Color(0xFF000080) // Navy
        5 -> Color(0xFF800000) // Maroon
        6 -> Color(0xFF008080) // Teal
        7 -> Color.Black
        8 -> Color.Gray
        else -> Color.Transparent
    }
}

@Preview(showBackground = true)
@Composable
fun MineSweeperGamePreview() {
    MineSweeperTheme {
        MineSweeperGame()
    }
}