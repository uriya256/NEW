package com.uriya.medreminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val RESOLVED = setOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.MISSED)

private fun pct(taken: Int, total: Int): String = if (total == 0) "—" else "${(taken * 100f / total).toInt()}%"

@Composable
fun ReportScreen(now: LocalDateTime, meds: List<Medication>, logs: Map<String, LogEntry>) {
    val today = now.toLocalDate()
    // resolved doses (taken / skipped / missed) for the last 30 days, newest first
    val byDay: List<Pair<LocalDate, List<Dose>>> = remember(meds, logs, now) {
        (0L until 30L).map { off ->
            val d = today.minusDays(off)
            d to computeDoses(meds, logs, d, now).filter { it.status in RESOLVED }
        }
    }
    val last7 = byDay.take(7).flatMap { it.second }
    val last30 = byDay.flatMap { it.second }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("7 ימים אחרונים", last7, Modifier.weight(1f))
                StatCard("30 ימים אחרונים", last30, Modifier.weight(1f))
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("היענות לפי יום", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        byDay.take(7).reversed().forEach { (day, list) ->
                            val total = list.size
                            val taken = list.count { it.status == DoseStatus.TAKEN }
                            val frac = if (total == 0) 0f else taken / total.toFloat()
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
                                Text(if (total == 0) "" else pct(taken, total), style = MaterialTheme.typography.labelSmall)
                                Box(
                                    Modifier.width(22.dp).height(100.dp).clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth().fillMaxHeight(frac.coerceAtLeast(0.001f))
                                            .background(
                                                if (frac >= 0.8f) statusColor(DoseStatus.TAKEN)
                                                else if (frac >= 0.5f) statusColor(DoseStatus.DUE)
                                                else statusColor(DoseStatus.MISSED)
                                            )
                                    )
                                }
                                Text(DAY_SHORT.getValue(day.dayOfWeek.value), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        if (meds.isNotEmpty()) {
            item { SectionTitle("לפי תרופה (30 יום)") }
            items(meds.sortedBy { it.name }, key = { "m${it.id}" }) { m ->
                val list = last30.filter { it.med.id == m.id }
                val taken = list.count { it.status == DoseStatus.TAKEN }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MedAvatar(m, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (list.isEmpty()) 0f else taken / list.size.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = medColor(m.colorIndex),
                                strokeCap = StrokeCap.Round
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${pct(taken, list.size)}\n$taken/${list.size}", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        val history = byDay.take(7).flatMap { (_, l) -> l.sortedByDescending { it.time } }
        if (history.isNotEmpty()) {
            item { SectionTitle("היסטוריה (שבוע אחרון)") }
            items(history, key = { "h" + it.key }) { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.med.form.emoji)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.med.name, fontWeight = FontWeight.Medium)
                        Text(
                            d.date.format(DateTimeFormatter.ofPattern("dd/MM")) + " · " + d.time +
                                (if (d.status == DoseStatus.TAKEN && d.loggedAt != null) " · נלקח ב-${hhmm(d.loggedAt)}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusChip(d.status)
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, doses: List<Dose>, modifier: Modifier) {
    val total = doses.size
    val taken = doses.count { it.status == DoseStatus.TAKEN }
    val missed = doses.count { it.status == DoseStatus.MISSED }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(pct(taken, total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("$taken נלקחו · $missed הוחמצו", style = MaterialTheme.typography.bodySmall)
        }
    }
}
