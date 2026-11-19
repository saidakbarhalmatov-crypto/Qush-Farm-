package com.example.qushfarm

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// Природная зелёно-белая палитра QUSH FARM
val DarkGreen = Color(0xFF1B5E20)
val PrimaryGreen = Color(0xFF2E7D32)
val MediumGreen = Color(0xFF43A047)
val LightGreenBg = Color(0xFFF1F8E9)
val CardMint = Color(0xFFE8F5E9)
val SoftCream = Color(0xFFFAFDF8)
val AccentGold = Color(0xFFF57F17)
val ExpenseRed = Color(0xFFC62828)

private val FarmColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = CardMint,
    onPrimaryContainer = DarkGreen,
    secondary = MediumGreen,
    onSecondary = Color.White,
    background = SoftCream,
    surface = Color.White,
    onSurface = Color(0xFF1C261E)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = FarmColorScheme) {
                QushFarmApp()
            }
        }
    }
}

enum class FarmScreen(val title: String) {
    HOME("QUSH FARM"),
    EGGS("Сбор яиц"),
    SALES("Продажа яиц"),
    FINANCE("Финансы (KGS)"),
    FEEDS("Учёт кормов"),
    STATS("История и статистика"),
    REMINDERS("Напоминания"),
    ABOUT("О приложении")
}

@Composable
fun QushFarmApp() {
    val context = LocalContext.current
    val prefs = remember { FarmPrefs(context) }
    val db = remember { FarmDatabase.getDatabase(context) }
    val dao = remember { db.farmDao() }

    var currentUser by remember { mutableStateOf(prefs.getUserName()) }
    var currentScreen by remember { mutableStateOf(FarmScreen.HOME) }
    var eggPrice by remember { mutableStateOf(prefs.getEggPrice()) }

    // Запрос разрешения на уведомления при входе
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Разрешите уведомления в настройках, чтобы получать напоминания о кормлении",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Потоки данных из базы Room
    val collections by dao.getAllCollections().collectAsState(initial = emptyList())
    val sales by dao.getAllSales().collectAsState(initial = emptyList())
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val feeds by dao.getAllFeeds().collectAsState(initial = emptyList())
    val reminders by dao.getAllReminders().collectAsState(initial = emptyList())

    if (currentUser.isBlank()) {
        WelcomeScreen(
            onUserSelected = { name ->
                prefs.setUserName(name)
                currentUser = name
            }
        )
    } else {
        AnimatedContent(targetState = currentScreen, label = "screen_nav") { screen ->
            when (screen) {
                FarmScreen.HOME -> HomeDashboardScreen(
                    userName = currentUser,
                    eggPrice = eggPrice,
                    collections = collections,
                    sales = sales,
                    transactions = transactions,
                    feeds = feeds,
                    reminders = reminders,
                    onNavigate = { currentScreen = it },
                    onSwitchUser = {
                        prefs.setUserName("")
                        currentUser = ""
                    }
                )
                FarmScreen.EGGS -> EggCollectionScreen(
                    userName = currentUser,
                    eggPrice = eggPrice,
                    collections = collections,
                    dao = dao,
                    onBack = { currentScreen = FarmScreen.HOME }
                )
                FarmScreen.SALES -> EggSalesScreen(
                    userName = currentUser,
                    eggPrice = eggPrice,
                    collections = collections,
                    sales = sales,
                    dao = dao,
                    onBack = { currentScreen = FarmScreen.HOME }
                )
                FarmScreen.FINANCE -> FinanceScreen(
                    userName = currentUser,
                    eggPrice = eggPrice,
                    onEggPriceChange = { newPrice ->
                        prefs.setEggPrice(newPrice)
                        eggPrice = newPrice
                    },
                    collections = collections,
                    sales = sales,
                    transactions = transactions,
                    dao = dao,
                    onBack = { currentScreen = FarmScreen.HOME }
                )
                FarmScreen.FEEDS -> FeedScreen(
                    userName = currentUser,
                    feeds = feeds,
                    dao = dao,
                    onBack = { currentScreen = FarmScreen.HOME }
                )
                FarmScreen.STATS -> StatsAndHistoryScreen(
                    collections = collections,
                    sales = sales,
                    transactions = transactions,
                    feeds = feeds,
                    onBack = { currentScreen = FarmScreen.HOME }
                )
                FarmScreen.REMINDERS -> RemindersScreen(
                    reminders = reminders,
                    dao = dao,
                    onBack = { currentScreen = FarmScreen.HOME }
                )
                FarmScreen.ABOUT -> AboutScreen(
                    onBack = { currentScreen = FarmScreen.HOME }
                )
            }
        }
    }
}

// ==========================================
// 1. ЭКРАН ПРИВЕТСТВИЯ И ВХОДА
// ==========================================
@Composable
fun WelcomeScreen(onUserSelected: (String) -> Unit) {
    var customName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkGreen, PrimaryGreen, LightGreenBg)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🐔", fontSize = 64.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "QUSH FARM",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkGreen
                )
                Text(
                    text = "Семейное управление куриной фермой",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Кто сейчас работает в приложении?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkGreen
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onUserSelected("Мама") },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("👩 Мама", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onUserSelected("Папа") },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
                    ) {
                        Text("👨 Папа", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Или введите другое имя:", fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Ваше имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (customName.isNotBlank()) onUserSelected(customName.trim())
                    },
                    enabled = customName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Войти в ферму", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 2. ГЛАВНЫЙ ЭКРАН (ДАШБОРД)
// ==========================================
@Composable
fun HomeDashboardScreen(
    userName: String,
    eggPrice: Double,
    collections: List<EggCollectionEntity>,
    sales: List<EggSaleEntity>,
    transactions: List<TransactionEntity>,
    feeds: List<FeedEntity>,
    reminders: List<ReminderEntity>,
    onNavigate: (FarmScreen) -> Unit,
    onSwitchUser: () -> Unit
) {
    val today = DateUtils.todayDateStr()
    val todayCollected = collections.filter { it.dateStr == today }.sumOf { it.totalEggs }
    val todaySold = sales.filter { it.dateStr == today }.sumOf { it.totalEggsSold }

    val totalCollected = collections.sumOf { it.totalEggs }
    val totalSold = sales.sumOf { it.totalEggsSold }
    val remainingEggs = (totalCollected - totalSold).coerceAtLeast(0)
    val remainingFlakes = remainingEggs / 30
    val remainingLoose = remainingEggs % 30

    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amountKgs }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountKgs }
    val netProfit = totalIncome - totalExpense

    val totalFeedKg = feeds.sumOf {
        if (it.unit == "кг") it.remainingQuantity else it.remainingQuantity / 1000.0
    }
    val activeRemindersCount = reminders.count { it.isEnabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftCream)
            .verticalScroll(rememberScrollState())
    ) {
        // Зелёная шапка приветствия
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(DarkGreen, PrimaryGreen)),
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Добро пожаловать, $userName!",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Сегодня: ${DateUtils.formatDisplayDate(today)} • 1 яйцо = ${formatMoney(eggPrice)} KGS",
                            color = CardMint,
                            fontSize = 13.sp
                        )
                    }
                    FilledTonalButton(
                        onClick = onSwitchUser,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Сменить", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Сводка за сегодня в шапке
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        HeaderMiniStat("🥚 Собрано сегодня", "$todayCollected шт.")
                        HeaderMiniStat("🛒 Продано сегодня", "$todaySold шт.")
                        HeaderMiniStat("📦 Остаток", "$remainingEggs шт.")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Сетка основных карточек управления
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🥚",
                    title = "Сбор яиц",
                    value = "$todayCollected шт.",
                    subtitle = "${todayCollected / 30} фл. за сегодня",
                    bgColor = CardMint,
                    onClick = { onNavigate(FarmScreen.EGGS) }
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "💵",
                    title = "Продажа яиц",
                    value = "$todaySold шт.",
                    subtitle = "Оформить продажу",
                    bgColor = Color(0xFFFFF8E1),
                    onClick = { onNavigate(FarmScreen.SALES) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Карточка остатка яиц
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(FarmScreen.EGGS) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("🧺 Остаток яиц на складе", fontWeight = FontWeight.Bold, color = DarkGreen)
                        Text(
                            text = "$remainingFlakes флеек и $remainingLoose шт. (оценка: ${formatMoney(remainingEggs * eggPrice)} KGS)",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        text = "$remainingEggs шт.",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Финансовые карточки: Доходы, Расходы, Прибыль
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "📈",
                    title = "Доходы",
                    value = "${formatMoney(totalIncome)} с.",
                    subtitle = "Все поступления",
                    bgColor = Color(0xFFE8F5E9),
                    onClick = { onNavigate(FarmScreen.FINANCE) }
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "📉",
                    title = "Расходы",
                    value = "${formatMoney(totalExpense)} с.",
                    subtitle = "Корм и затраты",
                    bgColor = Color(0xFFFFEBEE),
                    valueColor = ExpenseRed,
                    onClick = { onNavigate(FarmScreen.FINANCE) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Чистая прибыль
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(FarmScreen.FINANCE) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (netProfit >= 0) DarkGreen else ExpenseRed
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("💰 Чистая прибыль фермы", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                        Text("Доходы минус расходы", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Text(
                        text = "${formatMoney(netProfit)} KGS",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Учёт кормов и Напоминания
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌾",
                    title = "Учёт кормов",
                    value = String.format(Locale.US, "%.1f кг", totalFeedKg),
                    subtitle = "Остаток корма",
                    bgColor = Color(0xFFF3E5F5),
                    onClick = { onNavigate(FarmScreen.FEEDS) }
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🔔",
                    title = "Напоминания",
                    value = "$activeRemindersCount акт.",
                    subtitle = "Корм, яйца, уборка",
                    bgColor = Color(0xFFE3F2FD),
                    onClick = { onNavigate(FarmScreen.REMINDERS) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // История и статистика + О приложении
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "📊",
                    title = "Статистика",
                    value = "Графики",
                    subtitle = "История операций",
                    bgColor = Color.White,
                    onClick = { onNavigate(FarmScreen.STATS) }
                )
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    emoji = "❤️",
                    title = "О приложении",
                    value = "Для родителей",
                    subtitle = "От Саидакбара",
                    bgColor = Color(0xFFFCE4EC),
                    onClick = { onNavigate(FarmScreen.ABOUT) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeaderMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = label, color = CardMint, fontSize = 11.sp)
    }
}

@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    value: String,
    subtitle: String,
    bgColor: Color,
    valueColor: Color = DarkGreen,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF263238))
                Text(text = emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = valueColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 12.sp, color = Color.DarkGray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkGreen)
    )
}

// ==========================================
// 3. УЧЁТ СОБРАННЫХ ЯИЦ (ФЛЕЙКИ И ШТУКИ)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EggCollectionScreen(
    userName: String,
    eggPrice: Double,
    collections: List<EggCollectionEntity>,
    dao: FarmDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var amountText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("Флейки") }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val parsedAmount = amountText.toIntOrNull() ?: 0
    val calculatedEggs = if (selectedUnit == "Флейки") parsedAmount * 30 else parsedAmount
    val estimatedSum = calculatedEggs * eggPrice

    Scaffold(
        topBar = { FarmTopBar("🥚 Сбор яиц", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SoftCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Добавить собранные яйца",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = DarkGreen
                        )
                        Text(
                            "1 флейка = 30 яиц • Цена 1 яйца: ${formatMoney(eggPrice)} KGS",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { if (it.length <= 6) amountText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Количество") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = unitMenuExpanded,
                                onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedUnit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Единица") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = unitMenuExpanded,
                                    onDismissRequest = { unitMenuExpanded = false }
                                ) {
                                    listOf("Флейки", "Штук").forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(if (unit == "Флейки") "Флейки (по 30 шт.)" else "Штук") },
                                            onClick = {
                                                selectedUnit = unit
                                                unitMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Автоматический расчёт
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardMint, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Итого к сохранению: $calculatedEggs яиц",
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Ожидаемый доход: ${formatMoney(estimatedSum)} KGS",
                                    color = PrimaryGreen,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (calculatedEggs > 0) {
                                    scope.launch {
                                        dao.insertCollection(
                                            EggCollectionEntity(
                                                inputAmount = parsedAmount,
                                                unit = selectedUnit,
                                                totalEggs = calculatedEggs,
                                                estimatedValue = estimatedSum,
                                                dateStr = DateUtils.todayDateStr(),
                                                timeStr = DateUtils.currentTimeStr(),
                                                timestamp = System.currentTimeMillis(),
                                                addedBy = userName
                                            )
                                        )
                                        amountText = ""
                                        Toast.makeText(context, "Сохранено: +$calculatedEggs яиц", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = calculatedEggs > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Записать сбор", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            item {
                Text(
                    "История сбора яиц (${collections.size})",
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen,
                    fontSize = 16.sp
                )
            }

            items(collections) { item ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "+${item.totalEggs} яиц (${item.inputAmount} ${item.unit.lowercase()})",
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen
                            )
                            Text(
                                text = "${DateUtils.formatDisplayDate(item.dateStr)} в ${item.timeStr} • Добавил(а): ${item.addedBy}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "Оценка: ${formatMoney(item.estimatedValue)} KGS",
                                fontSize = 12.sp,
                                color = PrimaryGreen
                            )
                        }
                        IconButton(onClick = { scope.launch { dao.deleteCollection(item) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. ПРОДАЖА ЯИЦ
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EggSalesScreen(
    userName: String,
    eggPrice: Double,
    collections: List<EggCollectionEntity>,
    sales: List<EggSaleEntity>,
    dao: FarmDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val remainingEggs = (collections.sumOf { it.totalEggs } - sales.sumOf { it.totalEggsSold }).coerceAtLeast(0)

    var amountText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("Флейки") }
    var pricePerEggText by remember { mutableStateOf(eggPrice.toString()) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    val parsedAmount = amountText.toIntOrNull() ?: 0
    val eggsToSell = if (selectedUnit == "Флейки") parsedAmount * 30 else parsedAmount
    val pricePerEgg = pricePerEggText.replace(",", ".").toDoubleOrNull() ?: eggPrice
    val totalRevenue = eggsToSell * pricePerEgg

    Scaffold(
        topBar = { FarmTopBar("💵 Продажа яиц", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SoftCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Доступно для продажи: $remainingEggs яиц (${remainingEggs / 30} фл.)",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Количество") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = unitMenuExpanded,
                                onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedUnit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Единица") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = unitMenuExpanded,
                                    onDismissRequest = { unitMenuExpanded = false }
                                ) {
                                    listOf("Флейки", "Штук").forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit) },
                                            onClick = {
                                                selectedUnit = unit
                                                unitMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = pricePerEggText,
                            onValueChange = { pricePerEggText = it },
                            label = { Text("Цена за 1 яйцо (KGS)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF8E1), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Продаётся: $eggsToSell яиц", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Итоговая сумма: ${formatMoney(totalRevenue)} KGS",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = DarkGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (eggsToSell > 0) {
                                    scope.launch {
                                        val today = DateUtils.todayDateStr()
                                        val time = DateUtils.currentTimeStr()
                                        val now = System.currentTimeMillis()
                                        dao.insertSale(
                                            EggSaleEntity(
                                                inputAmount = parsedAmount,
                                                unit = selectedUnit,
                                                totalEggsSold = eggsToSell,
                                                pricePerEgg = pricePerEgg,
                                                totalAmountKgs = totalRevenue,
                                                dateStr = today,
                                                timeStr = time,
                                                timestamp = now,
                                                addedBy = userName
                                            )
                                        )
                                        // Автоматически добавляем в Доходы
                                        dao.insertTransaction(
                                            TransactionEntity(
                                                type = "INCOME",
                                                category = "Продажа яиц",
                                                description = "Продано $eggsToSell яиц ($parsedAmount ${selectedUnit.lowercase()})",
                                                amountKgs = totalRevenue,
                                                dateStr = today,
                                                timeStr = time,
                                                timestamp = now,
                                                addedBy = userName
                                            )
                                        )
                                        amountText = ""
                                        Toast.makeText(context, "Продажа оформлена: +${formatMoney(totalRevenue)} KGS", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = eggsToSell > 0 && totalRevenue > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Подтвердить продажу", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            item {
                Text("История продаж (${sales.size})", fontWeight = FontWeight.Bold, color = DarkGreen)
            }

            items(sales) { sale ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "+${formatMoney(sale.totalAmountKgs)} KGS (${sale.totalEggsSold} яиц)",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreen
                            )
                            Text(
                                text = "${DateUtils.formatDisplayDate(sale.dateStr)} ${sale.timeStr} • ${sale.addedBy}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { scope.launch { dao.deleteSale(sale) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. ФИНАНСЫ И ЦЕНА ЗА 1 ЯЙЦО (KGS)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    userName: String,
    eggPrice: Double,
    onEggPriceChange: (Double) -> Unit,
    collections: List<EggCollectionEntity>,
    sales: List<EggSaleEntity>,
    transactions: List<TransactionEntity>,
    dao: FarmDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var priceInput by remember { mutableStateOf(eggPrice.toString()) }
    var txType by remember { mutableStateOf("INCOME") } // INCOME или EXPENSE
    val incomeCategories = listOf("Продажа яиц", "Продажа кур", "Другие поступления")
    val expenseCategories = listOf("Покупка корма", "Покупка оборудования", "Другие расходы")
    var selectedCategory by remember { mutableStateOf(incomeCategories.first()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var amountKgsText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }

    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amountKgs }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountKgs }
    val netProfit = totalIncome - totalExpense
    val remainingEggs = (collections.sumOf { it.totalEggs } - sales.sumOf { it.totalEggsSold }).coerceAtLeast(0)

    Scaffold(
        topBar = { FarmTopBar("💰 Финансы (KGS)", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SoftCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Сводка финансов
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGreen)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Чистая прибыль: ${formatMoney(netProfit)} KGS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Доходы: +${formatMoney(totalIncome)} с.", color = CardMint, fontSize = 13.sp)
                            Text("Расходы: -${formatMoney(totalExpense)} с.", color = Color(0xFFFFCDD2), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Потенциал остатка яиц ($remainingEggs шт.): +${formatMoney(remainingEggs * eggPrice)} KGS",
                            color = Color(0xFFFFF59D),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Настройка базовой цены 1 яйца
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Цена 1 яйца для авторасчёта (KGS)", fontWeight = FontWeight.Bold, color = DarkGreen)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = priceInput,
                                onValueChange = { priceInput = it },
                                label = { Text("Сом за 1 яйцо") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    val newP = priceInput.replace(",", ".").toDoubleOrNull()
                                    if (newP != null && newP > 0) {
                                        onEggPriceChange(newP)
                                        Toast.makeText(context, "Цена яйца обновлена: $newP KGS", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Сохранить")
                            }
                        }
                    }
                }
            }

            // Добавление дохода или расхода
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Новая финансовая операция", fontWeight = FontWeight.Bold, color = DarkGreen)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = txType == "INCOME",
                                onClick = {
                                    txType = "INCOME"
                                    selectedCategory = incomeCategories.first()
                                },
                                label = { Text("➕ Доход") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = txType == "EXPENSE",
                                onClick = {
                                    txType = "EXPENSE"
                                    selectedCategory = expenseCategories.first()
                                },
                                label = { Text("➖ Расход") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val categories = if (txType == "INCOME") incomeCategories else expenseCategories
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Категория") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            selectedCategory = cat
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = amountKgsText,
                            onValueChange = { amountKgsText = it },
                            label = { Text("Сумма (KGS)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            label = { Text("Комментарий (необязательно)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val sum = amountKgsText.replace(",", ".").toDoubleOrNull() ?: 0.0
                                if (sum > 0) {
                                    scope.launch {
                                        dao.insertTransaction(
                                            TransactionEntity(
                                                type = txType,
                                                category = selectedCategory,
                                                description = descriptionText.ifBlank { selectedCategory },
                                                amountKgs = sum,
                                                dateStr = DateUtils.todayDateStr(),
                                                timeStr = DateUtils.currentTimeStr(),
                                                timestamp = System.currentTimeMillis(),
                                                addedBy = userName
                                            )
                                        )
                                        amountKgsText = ""
                                        descriptionText = ""
                                        Toast.makeText(context, "Операция сохранена", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Добавить запись", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("Последние операции (${transactions.size})", fontWeight = FontWeight.Bold, color = DarkGreen)
            }

            items(transactions) { tx ->
                val isInc = tx.type == "INCOME"
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.category, fontWeight = FontWeight.Bold)
                            Text(tx.description, fontSize = 12.sp, color = Color.DarkGray)
                            Text(
                                "${DateUtils.formatDisplayDate(tx.dateStr)} • ${tx.addedBy}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "${if (isInc) "+" else "-"}${formatMoney(tx.amountKgs)} с.",
                            fontWeight = FontWeight.Bold,
                            color = if (isInc) PrimaryGreen else ExpenseRed
                        )
                        IconButton(onClick = { scope.launch { dao.deleteTransaction(tx) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. УЧЁТ КОРМОВ (ПОКУПКА И СПИСАНИЕ)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    userName: String,
    feeds: List<FeedEntity>,
    dao: FarmDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var feedName by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("кг") }
    var costText by remember { mutableStateOf("") }
    var unitExpanded by remember { mutableStateOf(false) }

    var writeOffFeed by remember { mutableStateOf<FeedEntity?>(null) }
    var writeOffAmountText by remember { mutableStateOf("") }

    Scaffold(
        topBar = { FarmTopBar("🌾 Учёт кормов", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SoftCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Записать покупку корма", fontWeight = FontWeight.Bold, color = DarkGreen)
                        Text(
                            "Стоимость автоматически учтётся в расходах фермы",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = feedName,
                            onValueChange = { feedName = it },
                            label = { Text("Название корма (Пшеница, Комбикорм...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = { quantityText = it },
                                label = { Text("Количество") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenuBox(
                                expanded = unitExpanded,
                                onExpandedChange = { unitExpanded = !unitExpanded },
                                modifier = Modifier.weight(0.8f)
                            ) {
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Ед. изм.") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = unitExpanded,
                                    onDismissRequest = { unitExpanded = false }
                                ) {
                                    listOf("кг", "г").forEach { u ->
                                        DropdownMenuItem(
                                            text = { Text(if (u == "кг") "Килограмм (кг)" else "Грамм (г)") },
                                            onClick = {
                                                unit = u
                                                unitExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it },
                            label = { Text("Стоимость покупки (KGS)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val qty = quantityText.replace(",", ".").toDoubleOrNull() ?: 0.0
                                val cost = costText.replace(",", ".").toDoubleOrNull() ?: 0.0
                                if (feedName.isNotBlank() && qty > 0) {
                                    scope.launch {
                                        val today = DateUtils.todayDateStr()
                                        val now = System.currentTimeMillis()
                                        dao.insertFeed(
                                            FeedEntity(
                                                name = feedName.trim(),
                                                quantity = qty,
                                                remainingQuantity = qty,
                                                unit = unit,
                                                costKgs = cost,
                                                dateStr = today,
                                                timestamp = now,
                                                addedBy = userName
                                            )
                                        )
                                        if (cost > 0) {
                                            dao.insertTransaction(
                                                TransactionEntity(
                                                    type = "EXPENSE",
                                                    category = "Покупка корма",
                                                    description = "${feedName.trim()} ($qty $unit)",
                                                    amountKgs = cost,
                                                    dateStr = today,
                                                    timeStr = DateUtils.currentTimeStr(),
                                                    timestamp = now,
                                                    addedBy = userName
                                                )
                                            )
                                        }
                                        feedName = ""
                                        quantityText = ""
                                        costText = ""
                                        Toast.makeText(context, "Корм добавлен и учтён в расходах", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Сохранить корм", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("Запасы и история кормов (${feeds.size})", fontWeight = FontWeight.Bold, color = DarkGreen)
            }

            items(feeds) { feed ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(feed.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkGreen)
                                Text(
                                    text = "Куплено: ${feed.quantity} ${feed.unit} за ${formatMoney(feed.costKgs)} KGS",
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Остаток: ${feed.remainingQuantity} ${feed.unit} • ${DateUtils.formatDisplayDate(feed.dateStr)} (${feed.addedBy})",
                                    fontSize = 12.sp,
                                    color = PrimaryGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row {
                                TextButton(onClick = {
                                    writeOffFeed = feed
                                    writeOffAmountText = ""
                                }) {
                                    Text("Списать")
                                }
                                IconButton(onClick = { scope.launch { dao.deleteFeed(feed) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Диалог списания корма
        if (writeOffFeed != null) {
            AlertDialog(
                onDismissRequest = { writeOffFeed = null },
                title = { Text("Списать корм: ${writeOffFeed!!.name}") },
                text = {
                    Column {
                        Text("Текущий остаток: ${writeOffFeed!!.remainingQuantity} ${writeOffFeed!!.unit}")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = writeOffAmountText,
                            onValueChange = { writeOffAmountText = it },
                            label = { Text("Сколько израсходовано (${writeOffFeed!!.unit})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val used = writeOffAmountText.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (used > 0) {
                            val target = writeOffFeed!!
                            val newRem = (target.remainingQuantity - used).coerceAtLeast(0.0)
                            scope.launch {
                                dao.updateFeed(target.copy(remainingQuantity = newRem))
                                writeOffFeed = null
                            }
                        }
                    }) {
                        Text("Подтвердить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { writeOffFeed = null }) { Text("Отмена") }
                }
            )
        }
    }
}

// ==========================================
// 7. ИСТОРИЯ, ГРАФИКИ И РЕЗЕРВНОЕ КОПИРОВАНИЕ
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsAndHistoryScreen(
    collections: List<EggCollectionEntity>,
    sales: List<EggSaleEntity>,
    transactions: List<TransactionEntity>,
    feeds: List<FeedEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var periodFilter by remember { mutableStateOf("ALL") } // TODAY, WEEK, MONTH, ALL
    val today = DateUtils.todayDateStr()

    fun matchPeriod(dateStr: String): Boolean {
        return when (periodFilter) {
            "TODAY" -> dateStr == today
            "WEEK" -> DateUtils.isWithinLastDays(dateStr, 7)
            "MONTH" -> DateUtils.isWithinLastDays(dateStr, 30)
            else -> true
        }
    }

    val filteredCollections = collections.filter { matchPeriod(it.dateStr) }
    val filteredSales = sales.filter { matchPeriod(it.dateStr) }
    val filteredTx = transactions.filter { matchPeriod(it.dateStr) }

    val eggsCollected = filteredCollections.sumOf { it.totalEggs }
    val eggsSold = filteredSales.sumOf { it.totalEggsSold }
    val totalRemaining = (collections.sumOf { it.totalEggs } - sales.sumOf { it.totalEggsSold }).coerceAtLeast(0)
    val earned = filteredTx.filter { it.type == "INCOME" }.sumOf { it.amountKgs }
    val spent = filteredTx.filter { it.type == "EXPENSE" }.sumOf { it.amountKgs }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 Статистика и история", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                actions = {
                    // Кнопка резервного копирования / экспорта отчёта
                    IconButton(onClick = {
                        val backupText = buildString {
                            appendLine("🐔 ОТЧЁТ И РЕЗЕРВНАЯ КОПИЯ QUSH FARM (${DateUtils.formatDisplayDate(today)})")
                            appendLine("Собрано яиц: $eggsCollected шт.")
                            appendLine("Продано яиц: $eggsSold шт.")
                            appendLine("Остаток на складе: $totalRemaining шт.")
                            appendLine("Доходы: ${formatMoney(earned)} KGS")
                            appendLine("Расходы: ${formatMoney(spent)} KGS")
                            appendLine("Чистая прибыль: ${formatMoney(earned - spent)} KGS")
                            appendLine("\n--- Записи сбора ---")
                            collections.take(20).forEach {
                                appendLine("${DateUtils.formatDisplayDate(it.dateStr)} ${it.timeStr}: +${it.totalEggs} шт. (${it.addedBy})")
                            }
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Резервная копия QUSH FARM")
                            putExtra(Intent.EXTRA_TEXT, backupText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Сохранить резервную копию"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Резервная копия", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkGreen)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SoftCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Фильтры периода
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "TODAY" to "Сегодня",
                        "WEEK" to "Неделя",
                        "MONTH" to "Месяц",
                        "ALL" to "Всё время"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = periodFilter == key,
                            onClick = { periodFilter = key },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Сводка показателей за выбранный период
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Показатели за период", fontWeight = FontWeight.Bold, color = DarkGreen, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        StatLine("🥚 Собрано яиц:", "$eggsCollected шт. (${eggsCollected / 30} фл.)")
                        StatLine("🛒 Продано яиц:", "$eggsSold шт.")
                        StatLine("🧺 Осталось всего:", "$totalRemaining шт.")
                        StatLine("📈 Заработано:", "+${formatMoney(earned)} KGS", PrimaryGreen)
                        StatLine("📉 Потрачено:", "-${formatMoney(spent)} KGS", ExpenseRed)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        StatLine(
                            "💰 Итог (прибыль):",
                            "${formatMoney(earned - spent)} KGS",
                            if (earned >= spent) DarkGreen else ExpenseRed
                        )
                    }
                }
            }

            // Наглядная диаграмма доходов и расходов
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Диаграмма: Доходы и Расходы", fontWeight = FontWeight.Bold, color = DarkGreen)
                        Spacer(modifier = Modifier.height(12.dp))
                        val maxVal = maxOf(earned, spent, 1.0)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val barWidth = size.width / 4f
                            val incomeHeight = (earned / maxVal).toFloat() * (size.height - 20f)
                            val expenseHeight = (spent / maxVal).toFloat() * (size.height - 20f)

                            drawRect(
                                color = PrimaryGreen,
                                topLeft = Offset(barWidth * 0.7f, size.height - incomeHeight),
                                size = Size(barWidth, incomeHeight.coerceAtLeast(6f))
                            )
                            drawRect(
                                color = ExpenseRed,
                                topLeft = Offset(barWidth * 2.3f, size.height - expenseHeight),
                                size = Size(barWidth, expenseHeight.coerceAtLeast(6f))
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Text("Доходы (${formatMoney(earned)} с.)", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Расходы (${formatMoney(spent)} с.)", color = ExpenseRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Лента операций за период
            item {
                Text("Журнал операций", fontWeight = FontWeight.Bold, color = DarkGreen, fontSize = 16.sp)
            }

            items(filteredTx) { tx ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${tx.category}: ${tx.description}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${DateUtils.formatDisplayDate(tx.dateStr)} ${tx.timeStr} • ${tx.addedBy}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(
                            text = "${if (tx.type == "INCOME") "+" else "-"}${formatMoney(tx.amountKgs)} с.",
                            fontWeight = FontWeight.Bold,
                            color = if (tx.type == "INCOME") PrimaryGreen else ExpenseRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatLine(label: String, value: String, valueColor: Color = Color.Black) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.DarkGray, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Bold, color = valueColor, fontSize = 14.sp)
    }
}

// ==========================================
// 8. НАПОМИНАНИЯ (БЕЗ ОГРАНИЧЕНИЙ)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminders: List<ReminderEntity>,
    dao: FarmDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf("FEED") } // FEED, EGGS, CLEANING
    var titleText by remember { mutableStateOf("") }
    var selectedHour by remember { mutableStateOf(8) }
    var selectedMinute by remember { mutableStateOf(0) }
    var selectedDayOfWeek by remember { mutableStateOf(Calendar.SATURDAY) }

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            selectedHour = hourOfDay
            selectedMinute = minute
        },
        selectedHour,
        selectedMinute,
        true
    )

    Scaffold(
        topBar = { FarmTopBar("🔔 Напоминания фермы", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SoftCream)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Добавить напоминание (без ограничений)",
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = selectedType == "FEED",
                                onClick = { selectedType = "FEED" },
                                label = { Text("🌾 Корм", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedType == "EGGS",
                                onClick = { selectedType = "EGGS" },
                                label = { Text("🥚 Яйца", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedType == "CLEANING",
                                onClick = { selectedType = "CLEANING" },
                                label = { Text("🧹 Уборка", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = titleText,
                            onValueChange = { titleText = it },
                            label = {
                                Text(
                                    when (selectedType) {
                                        "FEED" -> "Название (напр. Утренний корм)"
                                        "EGGS" -> "Название (напр. Вечерний сбор)"
                                        else -> "Название (напр. Чистка курятника)"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (selectedType == "CLEANING") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("День недели (раз в неделю):", fontSize = 13.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val days = listOf(
                                    Calendar.MONDAY to "Пн",
                                    Calendar.TUESDAY to "Вт",
                                    Calendar.WEDNESDAY to "Ср",
                                    Calendar.THURSDAY to "Чт",
                                    Calendar.FRIDAY to "Пт",
                                    Calendar.SATURDAY to "Сб",
                                    Calendar.SUNDAY to "Вс"
                                )
                                days.forEach { (dayConst, shortName) ->
                                    FilterChip(
                                        selected = selectedDayOfWeek == dayConst,
                                        onClick = { selectedDayOfWeek = dayConst },
                                        label = { Text(shortName, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { timePickerDialog.show() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "⏰ Выбрать время: %02d:%02d".format(selectedHour, selectedMinute),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val defaultTitle = when (selectedType) {
                                    "FEED" -> "Кормление кур"
                                    "EGGS" -> "Сбор яиц"
                                    else -> "Уборка за курами"
                                }
                                val finalTitle = titleText.ifBlank { defaultTitle }
                                val dayParam = if (selectedType == "CLEANING") selectedDayOfWeek else null

                                scope.launch {
                                    val newItem = ReminderEntity(
                                        type = selectedType,
                                        title = finalTitle,
                                        hour = selectedHour,
                                        minute = selectedMinute,
                                        dayOfWeek = dayParam,
                                        isEnabled = true
                                    )
                                    val insertedId = dao.insertReminder(newItem).toInt()
                                    ReminderScheduler.scheduleReminder(context, newItem.copy(id = insertedId))
                                    titleText = ""
                                    Toast.makeText(context, "Напоминание установлено!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Добавить напоминание", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("Активное расписание (${reminders.size})", fontWeight = FontWeight.Bold, color = DarkGreen)
            }

            items(reminders) { rem ->
                val iconEmoji = when (rem.type) {
                    "FEED" -> "🌾"
                    "EGGS" -> "🥚"
                    else -> "🧹"
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$iconEmoji %02d:%02d — ${rem.title}".format(rem.hour, rem.minute),
                                fontWeight = FontWeight.Bold,
                                color = DarkGreen,
                                fontSize = 16.sp
                            )
                            Text(
                                text = DateUtils.dayOfWeekName(rem.dayOfWeek),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = rem.isEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    val updated = rem.copy(isEnabled = enabled)
                                    dao.updateReminder(updated)
                                    ReminderScheduler.scheduleReminder(context, updated)
                                }
                            }
                        )
                        IconButton(onClick = {
                            scope.launch {
                                ReminderScheduler.cancelReminder(context, rem.id)
                                dao.deleteReminder(rem)
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. СТРАНИЦА «О ПРИЛОЖЕНИИ» ДЛЯ РОДИТЕЛЕЙ
// ==========================================
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { FarmTopBar("❤️ О приложении", onBack) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(LightGreenBg, Color.White, CardMint))
                )
                .padding(padding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFCE4EC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "❤️", fontSize = 42.sp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Моим любимым маме и папе! ❤️",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DarkGreen,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "«Я создал это приложение специально для вас, чтобы вам было легче вести учёт нашей фермы. Спасибо вам за всё, за вашу заботу, труд и любовь. Я вас очень люблю!»",
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF2E3B30),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = CardMint, thickness = 2.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Создатель приложения:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Халматов Саидакбар Рашиджонович",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🐔 QUSH FARM • Сделано с любовью",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

fun formatMoney(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", amount)
    } else {
        String.format(Locale.US, "%.2f", amount)
    }
}
