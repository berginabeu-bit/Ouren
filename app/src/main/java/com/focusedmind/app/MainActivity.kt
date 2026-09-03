package com.focusedmind.app

import com.google.android.material.progressindicator.LinearProgressIndicator




import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focusedmind.app.ui.theme.FocusedMindTheme
import java.text.DateFormat
import java.util.Calendar
import kotlinx.coroutines.delay
import java.util.Date

private val Bg = Color(0xFF0F1115)
private val Surface = Color(0xFF191C22)
private val SurfaceAlt = Color(0xFF222630)
private val Accent = Color(0xFFFFB74D)
private val Muted = Color(0xFFA7ADB8)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_COMMITMENT_ID = "commitment_id"
        private const val PURCHASE_REQUEST_CODE = 8801
    }

    var uiVersion by mutableIntStateOf(0)
        private set

    private var pendingProduct: String? = null
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        openExactAlarmSettingsIfNeeded()
        refreshRuntimeState()
    }
    private val iap by lazy { HuaweiIapManager(this) }
    private val storeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AppEvents.ACTION_STORE_CHANGED) refreshRuntimeState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmReceiver.createChannel(getSystemService(NotificationManager::class.java))
        ContextCompat.registerReceiver(this, storeReceiver, IntentFilter(AppEvents.ACTION_STORE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        handleNotificationIntent(intent)
        refreshRuntimeState()
        setContent {
            FocusedMindTheme {
                FocusedMindRoot(this, uiVersion)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleNotificationIntent(intent)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(storeReceiver) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshRuntimeState()
    }

    private fun handleNotificationIntent(intent: Intent) {
        if (intent.hasExtra(EXTRA_COMMITMENT_ID)) uiVersion++
    }

    private fun refreshRuntimeState() {
        FocusedMindStore.cleanupExpired(this)
        val now = System.currentTimeMillis()
        FocusedMindStore.commitments(this)
            .filter { it.timestamp > now }
            .forEach { AlarmScheduler.schedule(this, it) }
        uiVersion++
    }

    private fun openExactAlarmSettingsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        }
    }

    fun requestReminderAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        openExactAlarmSettingsIfNeeded()
    }

    fun refreshUi() { uiVersion++ }

    fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    fun exactAlarmEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    fun startPurchase(product: String) {
        pendingProduct = product
        iap.startPurchase(this, product, PURCHASE_REQUEST_CODE) {
            Toast.makeText(this, LocalizedStrings.text(this, "purchase_unavailable"), Toast.LENGTH_LONG).show()
        }
    }

    fun loadProductPrice(product: String, onPrice: (String) -> Unit) {
        iap.queryProductPrice(product, onPrice)
    }

    fun restorePurchases(onDone: () -> Unit) {
        iap.restore(
            onDone = {
                runOnUiThread {
                    uiVersion++
                    onDone()
                }
            },
            onError = {
                runOnUiThread {
                    Toast.makeText(this, LocalizedStrings.text(this, "restore_unavailable"), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PURCHASE_REQUEST_CODE) return

        val product = pendingProduct ?: iap.pendingProductId()
        pendingProduct = null

        if (iap.handlePurchaseResult(resultCode, data, product)) {
            Toast.makeText(this, LocalizedStrings.text(this, "premium_unlocked"), Toast.LENGTH_SHORT).show()
        } else if (resultCode == RESULT_OK) {
            Toast.makeText(this, LocalizedStrings.text(this, "purchase_not_validated"), Toast.LENGTH_LONG).show()
        }
        uiVersion++
    }
}

@Composable
private fun FocusedMindRoot(activity: MainActivity, refreshSignal: Int) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var onboardingOpen by remember { mutableStateOf(!FocusedMindStore.onboardingComplete(context)) }

    LaunchedEffect(refreshSignal) {
        onboardingOpen = !FocusedMindStore.onboardingComplete(context)
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A0B0D)) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(LocalizedStrings.text(context, "home")) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.EmojiEvents, null) },
                    label = { Text(LocalizedStrings.text(context, "progress")) }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                refreshSignal = refreshSignal,
                onRefresh = { activity.refreshUi() },
                onOpenSettings = { settingsOpen = true },
                onReviewPermissions = activity::requestReminderAccess
            )
            else -> ProgressScreen(Modifier.padding(padding), refreshSignal)
        }
    }

    if (settingsOpen) {
        SettingsDialog(
            context = context,
            activity = activity,
            onClose = {
                settingsOpen = false
                activity.refreshUi()
            }
        )
    }

    if (onboardingOpen) {
        PermissionOnboarding(
            request = activity::requestReminderAccess,
            finish = {
                FocusedMindStore.setOnboardingComplete(context)
                onboardingOpen = false
            }
        )
    }
}

@Composable
private fun PermissionOnboarding(request: () -> Unit, finish: () -> Unit) {
    val context = LocalContext.current
    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.NotificationsActive, null, tint = Color.Black, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(LocalizedStrings.text(context, "app_title"), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(LocalizedStrings.text(context, "permission_desc"), color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = request, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(LocalizedStrings.text(context, "enable"))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = finish) { Text(LocalizedStrings.text(context, "continue_anyway")) }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    refreshSignal: Int,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onReviewPermissions: () -> Unit
) {
    val context = LocalContext.current
    val commitments = remember(refreshSignal) { FocusedMindStore.commitments(context) }
    var clockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(refreshSignal, commitments) {
        while (true) {
            clockMs = System.currentTimeMillis()
            delay(if (commitments.any {
                val start = it.timestamp
                val end = start + FocusedMindStore.RESPONSE_WINDOW_MS
                clockMs in (start - 2_000L)..(end + 2_000L)
            }) 1_000L else 15_000L)
        }
    }
    var createCategory by remember { mutableStateOf<Int?>(null) }
    var academicOpen by remember { mutableStateOf(false) }
    var academicSubject by remember { mutableStateOf<String?>(null) }
    var paywallProduct by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocalizedStrings.text(context, "app_title_upper"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(LocalizedStrings.text(context, "tagline"), color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, null, tint = Accent)
                }
            }
        }

        val remindersReady = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                (context as? MainActivity)?.notificationsEnabled() == true) &&
                ((context as? MainActivity)?.exactAlarmEnabled() == true)

        if (!remindersReady) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onReviewPermissions() },
                    colors = CardDefaults.cardColors(containerColor = SurfaceAlt),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(LocalizedStrings.text(context, "reminder_access"), color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(LocalizedStrings.text(context, "permission_desc"), color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(LocalizedStrings.text(context, "enable"), color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(LocalizedStrings.text(context, "window"), color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(LocalizedStrings.text(context, "two_reminders"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        item {
            Text(LocalizedStrings.text(context, "create"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        items(FocusCategoryCatalog.all) { category ->
            val premiumProduct = PremiumProducts.productFor(category.id)
            val locked = category.premium && premiumProduct != null && !FocusedMindStore.premium(context, premiumProduct)
            Card(
                Modifier.fillMaxWidth().clickable {
                    when {
                        locked -> paywallProduct = premiumProduct
                        category.id == 5 -> academicOpen = true
                        else -> createCategory = category.id
                    }
                },
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (locked) Icons.Default.Lock else Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Accent
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(LocalizedStrings.category(context, category.id), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (category.id == 5) LocalizedStrings.text(context, "choose_subject") else LocalizedStrings.text(context, "category_desc"),
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    if (category.premium) {
                        Text("${LocalizedStrings.text(context, if (locked) "locked_symbol" else "unlocked_symbol")} ${LocalizedStrings.text(context, "premium")}", color = Accent, fontSize = 11.sp)
                    }
                }
            }
        }

        if (commitments.isNotEmpty()) {
            item {
                Text(LocalizedStrings.text(context, "active"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            items(commitments, key = { it.id }) { commitment ->
                CommitmentCard(commitment, onRefresh, clockMs)
            }
        }
    }

    createCategory?.let { categoryId ->
        CommitmentDialog(
            categoryId = categoryId,
            subject = academicSubject,
            onClose = { createCategory = null; academicSubject = null },
            onSaved = { onRefresh(); createCategory = null; academicSubject = null }
        )
    }

    if (academicOpen) {
        AcademicDialog(
            onClose = { academicOpen = false },
            onCreate = { subject ->
                academicOpen = false
                academicSubject = subject
                createCategory = 5
            }
        )
    }

    paywallProduct?.let { product ->
        PaywallDialog(product, onClose = { paywallProduct = null })
    }
}

@Composable
private fun CommitmentCard(
    commitment: FocusedMindStore.Commitment,
    onRefresh: () -> Unit,
    now: Long
) {
    val context = LocalContext.current
    val inWindow = now in commitment.timestamp..(commitment.timestamp + FocusedMindStore.RESPONSE_WINDOW_MS)

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(commitment.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(formatCommitmentTime(context, commitment.timestamp), color = Accent, fontSize = 13.sp)
                    Text(LocalizedStrings.category(context, commitment.categoryId), color = Muted, fontSize = 11.sp)
                }
                IconButton(
                    onClick = {
                        FocusedMindStore.removeCommitment(context, commitment.id)
                        onRefresh()
                    }
                ) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Muted)
                }
            }
            if (inWindow) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            FocusedMindStore.markCompleted(context, commitment.id)
                            onRefresh()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(LocalizedStrings.text(context, "completed"))
                    }
                    OutlinedButton(
                        onClick = {
                            FocusedMindStore.markNotCompleted(context, commitment.id)
                            onRefresh()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(LocalizedStrings.text(context, "not_completed"))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitmentDialog(
    categoryId: Int,
    subject: String?,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(FocusedMindStore.RepeatMode.ONE_TIME) }
    var days by remember { mutableStateOf(emptySet<Int>()) }
    var timestamp by remember { mutableLongStateOf(defaultFutureTimestamp()) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(LocalizedStrings.text(context, "create")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(LocalizedStrings.text(context, "question")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${LocalizedStrings.text(context, "when")}: ${formatCommitmentTime(context, timestamp)}",
                    color = Color.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val current = Calendar.getInstance().apply { timeInMillis = timestamp }
                            DatePickerDialog(
                                LanguageManager.localizedContext(context),
                                { _, year, month, day ->
                                    val selected = Calendar.getInstance().apply {
                                        timeInMillis = timestamp
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                    }
                                    timestamp = selected.timeInMillis
                                },
                                current.get(Calendar.YEAR),
                                current.get(Calendar.MONTH),
                                current.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(LocalizedStrings.text(context, "date")) }

                    Button(
                        onClick = {
                            val current = Calendar.getInstance().apply { timeInMillis = timestamp }
                            TimePickerDialog(
                                LanguageManager.localizedContext(context),
                                { _, hour, minute ->
                                    val selected = Calendar.getInstance().apply {
                                        timeInMillis = timestamp
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    timestamp = selected.timeInMillis
                                },
                                current.get(Calendar.HOUR_OF_DAY),
                                current.get(Calendar.MINUTE),
                                android.text.format.DateFormat.is24HourFormat(context)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(LocalizedStrings.text(context, "time")) }
                }

                Text(LocalizedStrings.text(context, "repeat"), color = Muted)
                FocusedMindStore.RepeatMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = repeat == mode,
                            onClick = { repeat = mode },
                            role = androidx.compose.ui.semantics.Role.RadioButton
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = repeat == mode, onClick = { repeat = mode })
                        Text(repeatLabel(context, mode), color = Color.White)
                    }
                }

                if (repeat == FocusedMindStore.RepeatMode.SPECIFIC) {
                    Text(LocalizedStrings.text(context, "choose_days"), color = Muted)
                    specificDays.forEach { day ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = day in days,
                                onCheckedChange = { checked ->
                                    days = if (checked) days + day else days - day
                                }
                            )
                            Text(LocalizedStrings.day(context, day), color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        title.isBlank() -> Toast.makeText(context, LocalizedStrings.text(context, "enter_commitment"), Toast.LENGTH_SHORT).show()
                        timestamp < System.currentTimeMillis() + FocusedMindStore.MIN_LEAD_MS -> Toast.makeText(context, LocalizedStrings.text(context, "minimum_lead_required"), Toast.LENGTH_SHORT).show()
                        repeat == FocusedMindStore.RepeatMode.SPECIFIC && days.isEmpty() -> Toast.makeText(context, LocalizedStrings.text(context, "choose_at_least_one_day"), Toast.LENGTH_SHORT).show()
                        else -> {
                            val commitment = FocusedMindStore.Commitment(
                                id = newCommitmentId(),
                                categoryId = categoryId,
                                title = title.trim(),
                                timestamp = timestamp,
                                subject = subject,
                                repeatMode = repeat,
                                repeatDays = days
                            )
                            FocusedMindStore.upsertCommitment(context, commitment)
                            AlarmScheduler.schedule(context, commitment)
                            onSaved()
                        }
                    }
                }
            ) { Text(LocalizedStrings.text(context, "add")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(LocalizedStrings.text(context, "cancel")) } }
    )
}

@Composable
private fun AcademicDialog(onClose: () -> Unit, onCreate: (String) -> Unit) {
    val context = LocalContext.current
    val subjects = listOf("languages", "mathematics", "physics", "chemistry", "biology", "history", "geography")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(LocalizedStrings.text(context, "academic_focus")) },
        text = {
            LazyColumn {
                items(subjects) { key ->
                    ListItem(
                        headlineContent = { Text(LocalizedStrings.subject(context, key)) },
                        supportingContent = { Text(LocalizedStrings.text(context, "specialized")) },
                        modifier = Modifier.clickable { onCreate(key) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(LocalizedStrings.text(context, "cancel")) } }
    )
}

@Composable
private fun PaywallDialog(product: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val academic = product == PremiumProducts.ACADEMIC_FOCUS
    var livePrice by remember(product) { mutableStateOf<String?>(null) }
    LaunchedEffect(product) {
        activity?.let { a ->
            a.loadProductPrice(product) { price -> livePrice = price }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (academic) LocalizedStrings.text(context, "academic_focus") else LocalizedStrings.text(context, "important_events")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalizedStrings.text(context, "locked_desc"))
                Text(
                    livePrice ?: if (academic) LocalizedStrings.text(context, "academic_price") else LocalizedStrings.text(context, "events_price"),
                    color = Accent,
                    fontWeight = FontWeight.Bold
                )
                Text(LocalizedStrings.text(context, "huawei_iap_info"), color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = { activity?.startPurchase(product); onClose() }) {
                Text(LocalizedStrings.text(context, "unlock"))
            }
        },
        dismissButton = {
            TextButton(onClick = { activity?.restorePurchases { onClose() } }) {
                Text(LocalizedStrings.text(context, "restore"))
            }
        }
    )
}

@Composable
private fun ProgressScreen(modifier: Modifier, refreshSignal: Int) {
    val context = LocalContext.current
    val xp = remember(refreshSignal) { FocusedMindStore.xp(context) }
    val level = remember(xp) { FocusedMindStore.levelFor(xp) }
    val streak = remember(refreshSignal) { FocusedMindStore.streak(context) }
    val best = remember(refreshSignal) { FocusedMindStore.bestStreak(context) }
    val completed = remember(refreshSignal) { FocusedMindStore.completed(context) }
    val pending = remember(refreshSignal) { FocusedMindStore.pending(context) }
    val missed = remember(refreshSignal) { FocusedMindStore.missed(context) }
    val expired = remember(refreshSignal) { FocusedMindStore.expired(context) }
    val rate = remember(refreshSignal) { FocusedMindStore.completionRate(context) }
    val days = remember(refreshSignal) { FocusedMindStore.activeDays(context) }
    val academic = remember(refreshSignal) { FocusedMindStore.academicSessions(context) }
    val language = LanguageManager.effectiveLocale(context).toLanguageTag()
    val message = remember(language, refreshSignal) {
        FocusedMindStore.progressMessage(context, language) {
            LocalizedContentRepository.randomProgress(context, "growing")
        }
    }

    LazyColumn(
        modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(LocalizedStrings.text(context, "progress"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(message, color = Muted, fontSize = 12.sp)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).clip(CircleShape).background(Accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(level.number.toString(), color = Color.Black, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${LocalizedStrings.text(context, "level")} ${level.number}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(LocalizedStrings.text(context, level.nameKey), color = Accent)
                        }
                        Text("$xp XP", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    val denominator = (level.next - level.floor).coerceAtLeast(1L)
                    val fraction = ((xp - level.floor).toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("${(level.next - xp).coerceAtLeast(0)} ${LocalizedStrings.text(context, "to_next")}", color = Muted, fontSize = 12.sp)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(LocalizedStrings.text(context, "streak"), streak.toString(), Modifier.weight(1f))
                Stat(LocalizedStrings.text(context, "best"), best.toString(), Modifier.weight(1f))
                Stat(LocalizedStrings.text(context, "completed_count"), completed.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(LocalizedStrings.text(context, "pending_count"), pending.toString(), Modifier.weight(1f))
                Stat(LocalizedStrings.text(context, "missed_count"), missed.toString(), Modifier.weight(1f))
                Stat(LocalizedStrings.text(context, "expired_count"), expired.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(LocalizedStrings.text(context, "rate"), "$rate%", Modifier.weight(1f))
                Stat(LocalizedStrings.text(context, "active_days"), days.toString(), Modifier.weight(1f))
                Stat(LocalizedStrings.text(context, "academic_sessions"), academic.toString(), Modifier.weight(1f))
            }
        }

        item { Text(LocalizedStrings.text(context, "category_progress"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        items(FocusCategoryCatalog.all) { category ->
            val count = FocusedMindStore.categoryCompleted(context, category.id)
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(14.dp)) {
                ListItem(
                    headlineContent = { Text(LocalizedStrings.category(context, category.id), color = Color.White) },
                    supportingContent = { Text("$count ${LocalizedStrings.text(context, "completed_count").lowercase()}", color = Muted) }
                )
            }
        }

        item { Text(LocalizedStrings.text(context, "achievements"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        items(achievementIds) { id ->
            val unlocked = when (id) {
                "first" -> completed >= 1
                "3" -> best >= 3
                "7" -> best >= 7
                "14" -> best >= 14
                "30" -> best >= 30
                "50" -> completed >= 50
                "100" -> completed >= 100
                else -> academic >= 1
            }
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(14.dp)) {
                ListItem(
                    leadingContent = {
                        Icon(
                            if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (unlocked) Accent else Muted
                        )
                    },
                    headlineContent = { Text(LocalizedStrings.achievement(context, id), color = Color.White) },
                    supportingContent = { Text(if (unlocked) LocalizedStrings.text(context, "unlocked") else LocalizedStrings.text(context, "keep_going"), color = Muted) }
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(14.dp)) {
        Column(
            Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(label, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsDialog(context: Context, activity: MainActivity, onClose: () -> Unit) {
    var selected by remember { mutableStateOf(LanguageManager.selectedTag(context)) }
    val languageOptions = LanguageManager.supported

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(LocalizedStrings.text(context, "settings")) },
        text = {
            LazyColumn {
                item {
                    Text(LocalizedStrings.text(context, "language"), fontWeight = FontWeight.Bold)
                }
                items(languageOptions) { language ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = language.tag }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected == language.tag, onClick = { selected = language.tag })
                        Text(language.label, color = Color.White)
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(LocalizedStrings.text(context, "reminder_access"), fontWeight = FontWeight.Bold)
                    Text(
                        if (activity.notificationsEnabled() && activity.exactAlarmEnabled()) LocalizedStrings.text(context, "enabled") else LocalizedStrings.text(context, "disabled"),
                        color = if (activity.notificationsEnabled() && activity.exactAlarmEnabled()) Accent else Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = activity::requestReminderAccess) { Text(LocalizedStrings.text(context, "enable")) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { LanguageManager.set(context, selected); onClose() }) {
                Text(LocalizedStrings.text(context, "save"))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(LocalizedStrings.text(context, "cancel")) } }
    )
}

private fun repeatLabel(context: Context, mode: FocusedMindStore.RepeatMode): String = when (mode) {
    FocusedMindStore.RepeatMode.ONE_TIME -> LocalizedStrings.text(context, "one_time")
    FocusedMindStore.RepeatMode.DAILY -> LocalizedStrings.text(context, "daily")
    FocusedMindStore.RepeatMode.WEEKDAYS -> LocalizedStrings.text(context, "weekdays")
    FocusedMindStore.RepeatMode.WEEKENDS -> LocalizedStrings.text(context, "weekends")
    FocusedMindStore.RepeatMode.SPECIFIC -> LocalizedStrings.text(context, "specific")
}

private fun formatCommitmentTime(context: Context, timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, LanguageManager.effectiveLocale(context)).format(Date(timestamp))

private fun defaultFutureTimestamp(): Long = Calendar.getInstance().apply {
    add(Calendar.MINUTE, 30)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun newCommitmentId(): Long = ((System.currentTimeMillis() shl 20) xor System.nanoTime()).let { if (it <= 0) -it + 1 else it }

private val specificDays = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

private val achievementIds = listOf("first", "3", "7", "14", "30", "50", "100", "academic")
