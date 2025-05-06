package com.edigar.minesweeper

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import kotlin.math.abs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// import androidx.activity.viewModels // Import viewModels delegate (Alternative way)
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi // For combinedClickable
import androidx.compose.foundation.background // 添加background导入
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha // 添加alpha修饰符导入
import androidx.compose.ui.platform.LocalDensity // 添加LocalDensity导入
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edigar.minesweeper.ui.theme.MineSweeperTheme
import kotlin.math.abs

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
    // 获取剩余提示次数
    val hintsRemaining = gameViewModel.hintsRemaining
    // 获取是否有高亮的安全格子
    val isSafeCellHighlighted = gameViewModel.isSafeCellHighlighted
    
    // 创建垂直滚动状态
    val verticalScrollState = rememberScrollState()

    // 状态：当前正在拖动的标记类型
    var currentDragMarkType by remember { mutableStateOf<MarkType?>(null) }
    // 状态：当前拖动的位置（现在使用绝对位置）
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    // 状态：是否正在拖动
    var isDragging by remember { mutableStateOf(false) }
    // 状态：网格位置
    var gridPosition by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(Offset.Zero) }
    // 记住屏幕密度，避免重复计算
    val density = LocalDensity.current
    
    // 获取Context用于触感反馈
    val context = LocalContext.current

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
        
        // 添加提示功能按钮和剩余提示次数信息
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            // 显示剩余提示次数
            Text(
                text = "提示次数: $hintsRemaining",
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            
            // 提示按钮
            Button(
                onClick = { gameViewModel.useHint() },
                enabled = gameStatus == GameStatus.PLAYING && hintsRemaining > 0 && gameViewModel.isGameStarted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50) // 绿色按钮
                )
            ) {
                Text("获取提示", fontSize = 16.sp)
            }
        }
        
        // 如果当前有高亮的安全格子，显示提示信息
        if (isSafeCellHighlighted) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "提示: 绿色高亮的格子是安全的!",
                fontSize = 14.sp,
                color = Color(0xFF4CAF50)
            )
        }
        
        // 添加拖放标记区域（游戏进行中才显示）
        if (gameStatus == GameStatus.PLAYING) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // 可拖动的标记图标区域
            DraggableMarkersRow(
                onStartDrag = { markType, offset ->
                    currentDragMarkType = markType
                    // 直接使用传递的绝对屏幕位置
                    dragPosition = offset
                    isDragging = true
                },                    
                onDrag = { absolutePosition ->
                    // 检查位置是否有显著变化，避免微小变化触发不必要的重绘
                    val delta = abs(dragPosition.x - absolutePosition.x) + abs(dragPosition.y - absolutePosition.y)
                    if (delta > 1f) {
                        // 立即更新拖动位置，确保标记跟随手指移动
                        dragPosition = absolutePosition
                    }
                },
                onEndDrag = { lastKnownPosition ->
                    // 使用最后一个已知的拖动位置作为释放点，但先检查其有效性
                    var releasePosition = lastKnownPosition ?: dragPosition
                    
                    // 如果位置明显无效（例如负值太大），则使用最后一个已知的有效位置
                    var shouldContinue = true
                    if (releasePosition.x < -100 || releasePosition.y < -100) {
                        println("检测到无效的释放位置: X=${releasePosition.x}, Y=${releasePosition.y}，尝试修正")
                        // 尝试使用更可靠的拖动位置
                        releasePosition = dragPosition
                        
                        // 如果仍然无效，则放弃这次拖放操作
                        if (releasePosition.x < -100 || releasePosition.y < -100) {
                            println("无法获取有效的释放位置，放弃本次操作")
                            // 重置拖动状态
                            isDragging = false
                            currentDragMarkType = null
                            shouldContinue = false // 设置标志变量，而不是使用return
                        }
                    }
                    
                    // 只有当shouldContinue为true时才继续处理
                    if (shouldContinue) {
                        // 添加调试日志
                        println("释放位置 X: ${releasePosition.x}, Y: ${releasePosition.y}")
                        println("网格位置 X: ${gridPosition.x}, Y: ${gridPosition.y}")
                        println("网格大小 宽: ${gridSize.x}, 高: ${gridSize.y}")
                        println("滚动偏移量: ${verticalScrollState.value}")
    
                        // 调整释放位置，考虑滚动和坐标系差异
                        // 释放位置是基于屏幕的绝对坐标，而网格位置是相对于布局的
                        val scrollOffset = verticalScrollState.value.toFloat()
                        
                        // 更宽松的边界检查，允许一定的容差
                        val tolerance = 0f
    
                        // 计算拖放位置相对于网格的有效坐标
                        // 考虑精确的手指位置，不添加额外偏移以确保定位准确
                        val effectiveReleaseX = releasePosition.x
                        val effectiveReleaseY = releasePosition.y + scrollOffset
                        
                        println("调整后释放位置 X: ${effectiveReleaseX}, Y: ${effectiveReleaseY}")
                        println("拖动标记类型: $currentDragMarkType")

                        // 计算列和行（假设所有格子大小相同）
                        val cellSize = gridSize.x / gameViewModel.currentDifficulty.cols

                        // 精确检查X和Y坐标是否在网格范围内
                        val isTooFarLeft = effectiveReleaseX < gridPosition.x  // 如果X坐标小于网格左边界
                        val isTooFarRight = effectiveReleaseX > gridPosition.x + gridSize.x  // 如果X坐标大于网格右边界
                        // gridPosition.y是网格的下边界，上边界是gridPosition.y - gridSize.y - cellSize
                        val isTooFarUp = effectiveReleaseY < gridPosition.y - gridSize.y - cellSize// 精确边界检查，去掉额外的cellSize偏移
                        val isTooFarDown = effectiveReleaseY > gridPosition.y  // 如果Y坐标大于网格下边界
    
                        // 如果任何一个条件为真，说明释放位置太远，可打印日志
                        if (isTooFarLeft || isTooFarRight || isTooFarUp || isTooFarDown) {
                            println("释放位置超出网格范围太多: 左=${isTooFarLeft}, 右=${isTooFarRight}, 上=${isTooFarUp}, 下=${isTooFarDown}")
                        }
    
                        // 检查拖放位置是否在网格内（使用精确的边界检查）
                        // gridPosition.y是网格的下边界，上边界是gridPosition.y - gridSize.y
                        if (currentDragMarkType != null &&
                            effectiveReleaseX >= gridPosition.x - tolerance && 
                            effectiveReleaseX <= gridPosition.x + gridSize.x + tolerance &&
                            effectiveReleaseY >= gridPosition.y - gridSize.y - cellSize - tolerance && // 精确的上边界检查
                            effectiveReleaseY <= gridPosition.y + tolerance // 下边界
                        ) {
                            // 使用经过滚动调整的释放位置
                            val fingerX = effectiveReleaseX
                            val fingerY = effectiveReleaseY
                            
                            // 计算拖放位置对应的网格单元格
                            // X坐标相对于网格左边界的距离
                            val relativeX = fingerX - gridPosition.x
                            
                            // 调整相对Y坐标计算，考虑gridPosition.y是网格下边界
                            // 网格的上边界位置是 (gridPosition.y - gridSize.y)
                            // 相对Y坐标是从上边界算起的偏移量
                            val relativeY = fingerY - (gridPosition.y - gridSize.y - cellSize)

                            val col = (relativeX / cellSize).toInt()
                            val row = (relativeY / cellSize).toInt()
                            
                            // 添加更多调试信息
                            println("网格上边界Y: ${gridPosition.y - gridSize.y}")
                            println("网格下边界Y: ${gridPosition.y}")
                            
                            // 输出调试信息
                            println("相对位置 X: $relativeX, Y: $relativeY")
                            println("格子大小: $cellSize")
                            println("计算得到行: $row, 列: $col")
                            
                            // 确保行列在有效范围内
                            if (row >= 0 && row < gameViewModel.currentDifficulty.rows && 
                                col >= 0 && col < gameViewModel.currentDifficulty.cols) {
                                
                                // 获取对应的格子
                                val targetCell = gameViewModel.grid[row][col]
                                
                                // 应用标记
                                currentDragMarkType?.let { markType ->
                                    gameViewModel.onDragMarkCell(targetCell, markType)
                                    performHapticFeedback(context) // 提供触感反馈
                                }
                            }
                        }
                    }
                    
                    // 无论如何都重置拖动状态
                    isDragging = false
                    currentDragMarkType = null
                }
            )
            
            Text(
                text = "拖动图标到格子上标记",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 记录网格位置
        Box(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    // 记录网格位置和大小，用于后续判断拖放位置
                    val scrollOffset = verticalScrollState.value.toFloat()
                    val rawPosition = coordinates.positionInRoot()
                    
                    // 计算网格的绝对位置，存储滚动偏移量而不是直接调整坐标
                    // 重要：gridPosition.y代表网格的下边界位置
                    gridPosition = rawPosition
                    
                    // 记录网格大小
                    gridSize = Offset(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()
                    )
                    
                    // 调试信息
//                    println("网格左边界X: ${gridPosition.x}")
//                    println("网格右边界X: ${gridPosition.x + gridSize.x}")
//                    println("网格上边界Y: ${gridPosition.y - gridSize.y}")
//                    println("网格下边界Y: ${gridPosition.y}")
//                    println("滚动偏移量: $scrollOffset")
                }
        ) {
            MineSweeperGrid(viewModel = gameViewModel)
            
            // 如果正在拖动，绘制拖动中的标记
            if (isDragging && currentDragMarkType != null) {
                Box(
                    modifier = Modifier
                        .offset { 
                            // 使用预先保存的密度计算，避免重复计算
                            // 将标记显示在手指正下方，精确定位
                            val markerSize = with(density) { 32.dp.toPx() } // 使用与实际大小完全一致的值
                            val offsetX = dragPosition.x - (markerSize / 2)
                            // 向上偏移10像素，使标记出现在手指下方而不被遮挡
                            val offsetY = dragPosition.y - (markerSize / 2) - 10f
                            IntOffset(
                                offsetX.toInt(),
                                offsetY.toInt()
                            )
                        }
                        .size(32.dp) // 进一步减小大小，使其不会覆盖太多网格内容且更准确
                        .zIndex(10f) // 确保在最上层
                        .alpha(1f), // 保持完全不透明以提高可见性
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when(currentDragMarkType) {
                            MarkType.FLAG -> "🚩"
                            MarkType.QUESTION -> "❓"
                            MarkType.CLEAR -> "✖️"
                            null -> ""
                        },
                        fontSize = 20.sp // 匹配缩小后的标记大小，保持良好的比例
                    )
                }
            }
        }
        
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
    
    // 检查这个单元格是否是当前被提示高亮的安全格子
    val isHighlightedSafeCell = viewModel.isSafeCellHighlighted && 
                               viewModel.highlightedSafeCell == cell
    
    // 根据单元格状态确定背景颜色
    val baseBackgroundColor = when {
        isHighlightedSafeCell -> Color(0xFF4CAF50) // 高亮的安全格子显示为绿色
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

// 可拖动的标记图标区域
@Composable
fun DraggableMarkersRow(
    onStartDrag: (MarkType, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEndDrag: (Offset?) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(8.dp)
            .border(
                width = 1.dp,
                color = Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        // 旗子标记
        DraggableMarker(
            text = "🚩",
            markType = MarkType.FLAG,
            onStartDrag = onStartDrag,
            onDrag = onDrag,
            onEndDrag = onEndDrag
        )
        
        // 问号标记
        DraggableMarker(
            text = "❓",
            markType = MarkType.QUESTION,
            onStartDrag = onStartDrag,
            onDrag = onDrag,
            onEndDrag = onEndDrag
        )
        
        // 清除标记
        DraggableMarker(
            text = "✖️",
            markType = MarkType.CLEAR,
            onStartDrag = onStartDrag,
            onDrag = onDrag,
            onEndDrag = onEndDrag
        )
    }
}

// 单个可拖动的标记图标
@Composable
fun DraggableMarker(
    text: String,
    markType: MarkType,
    onStartDrag: (MarkType, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEndDrag: (Offset?) -> Unit
) {
    // 变换效果，当拖动时缩小
    var isBeingDragged by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isBeingDragged) 0.8f else 1f,
        animationSpec = tween(150),
        label = "DragScale"
    )
    
    // 保存控件位置和当前拖动位置
    var position by remember { mutableStateOf(Offset.Zero) }
    var currentDragPosition by remember { mutableStateOf(Offset.Zero) }
    
    Card(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .onGloballyPositioned { coordinates -> 
                // 记录这个卡片的全局位置
                position = coordinates.positionInRoot()
            }
            .pointerInput(markType) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isBeingDragged = true
                        // 计算指针在屏幕上的绝对位置，更加精确地处理初始触摸点
                        val touchPosition = position + offset
                        // 初始化当前拖动位置 - 不添加任何偏移以获得最准确的位置
                        currentDragPosition = touchPosition
                        // 立即通知开始拖动，确保UI响应迅速
                        onStartDrag(markType, touchPosition)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        // 保存当前拖动位置用于onDragEnd
                        // 使用绝对位置，但确保它在屏幕有效范围内
                        val absolutePosition = change.position
                        
                        // 检查位置坐标是否有效，过滤掉明显不合理的值
                        if (absolutePosition.x > -100 && absolutePosition.y > -100) {
                            // 使用精确的触摸位置，增加实时性
                            currentDragPosition = absolutePosition
                            // 立即传递手指位置，确保UI能快速响应
                            onDrag(absolutePosition)
                        }
                    },
                    onDragEnd = {
                        isBeingDragged = false
                        
                        // 检查最后的拖动位置是否有效，过滤掉明显不合理的值
                        if (currentDragPosition.x < -100 || currentDragPosition.y < -100) {
                            println("检测到onDragEnd中的无效位置: X=${currentDragPosition.x}, Y=${currentDragPosition.y}")
                            // 无效位置不传递，使用null以触发回退逻辑
                            onEndDrag(null)
                        } else {
                            // 精确地将最后的拖动位置传递给onEndDrag
                            println("有效拖动结束位置: X=${currentDragPosition.x}, Y=${currentDragPosition.y}")
                            onEndDrag(currentDragPosition)
                        }
                    },
                    onDragCancel = {
                        isBeingDragged = false
                        // 拖动取消时不传递位置
                        onEndDrag(null)
                    }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (markType) {
                MarkType.FLAG -> Color(0xFFFFDAB9)     // 淡橙色
                MarkType.QUESTION -> Color(0xFFE6E6FA) // 淡紫色
                MarkType.CLEAR -> Color(0xFFFFE4E1)    // 淡粉色
            }
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = 18.sp
            )
        }
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