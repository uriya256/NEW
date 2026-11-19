package com.uriya.medreminder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDate

private fun open(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    // re-read system state whenever the user comes back from the system settings
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    val notifOk = remember(refresh) { Notifier.hasPermission(ctx) }
    val exactOk = remember(refresh) { Scheduler.canExact(ctx) }
    val batteryOk = remember(refresh) {
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) }
            }.isSuccess
            Toast.makeText(ctx, if (ok) "הגיבוי נשמר" else "שמירת הגיבוי נכשלה", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            val ok = text != null && Repo.importJson(ctx, text)
            Toast.makeText(ctx, if (ok) "הנתונים שוחזרו" else "הקובץ לא תקין", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("כדי שהתזכורות יגיעו בזמן", Modifier.padding(top = 0.dp))

        StatusRow(
            ok = notifOk,
            title = "הרשאת התראות",
            okText = "מאושר",
            badText = "ההתראות חסומות — לא תקבל תזכורות",
            button = "פתח הגדרות התראות"
        ) {
            open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }

        StatusRow(
            ok = exactOk,
            title = "התראות בשעה מדויקת",
            okText = "מאושר",
            badText = "בלי זה התזכורת עלולה להתעכב בכמה דקות",
            button = "אשר שעונים מדויקים"
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
            }
        }

        StatusRow(
            ok = batteryOk,
            title = "חיסכון בסוללה",
            okText = "האפליקציה לא מוגבלת",
            badText = "בחלק מהמכשירים (שיאומי, סמסונג וכו׳) חיסכון בסוללה מעכב תזכורות. מומלץ לבחור \"ללא הגבלה\".",
            button = "פתח הגדרות סוללה"
        ) {
            open(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        FilledTonalButton(onClick = { Notifier.test(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text("שלח התראת בדיקה")
        }

        SectionTitle("גיבוי ושחזור")
        Text(
            "כל הנתונים נשמרים רק בטלפון שלך, בלי אינטרנט ובלי חשבון. כדאי לשמור גיבוי מדי פעם.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("medreminder-backup-${LocalDate.now()}.json") },
                modifier = Modifier.weight(1f)
            ) { Text("שמירת גיבוי") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.weight(1f)
            ) { Text("שחזור מגיבוי") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "תרופה בזמן · גרסה 1.0\nהאפליקציה היא כלי תזכורת בלבד ואינה מחליפה ייעוץ רפואי.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusRow(
    ok: Boolean,
    title: String,
    okText: String,
    badText: String,
    button: String,
    onFix: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (ok) "✅" else "⚠️")
                Text("  $title", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Text(
                if (ok) okText else badText,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else statusColor(DoseStatus.MISSED),
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!ok) {
                FilledTonalButton(onClick = onFix, modifier = Modifier.padding(top = 8.dp)) { Text(button) }
            }
        }
    }
}
