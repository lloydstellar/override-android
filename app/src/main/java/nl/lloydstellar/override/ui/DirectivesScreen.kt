package nl.lloydstellar.override.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.override.Mode
import nl.lloydstellar.override.data.DISPATCHES
import nl.lloydstellar.override.data.Store
import nl.lloydstellar.override.engine.Directives
import nl.lloydstellar.override.engine.Sector
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The Daily and Weekly Directives. Every day has its daily, and missed days
 * stay playable in the calendar: nothing is lost by skipping a day. Clearing
 * every daily of a month decrypts that month's dispatch.
 */
@Composable
fun DirectivesScreen(
    store: Store,
    onBack: () -> Unit,
    onStart: (Sector, LocalDate) -> Unit,
    onContinue: (Mode) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var open by rememberSaveable { mutableStateOf<String?>(null) }   /* "d:2026-10-15" or "w:2026-10-15" */
    var month by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }

    Box(Modifier.fillMaxSize()) {
        Tube(dim = 0.2f)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp)) {
                ScreenTitle("DIRECTIVES", "A new daily every day, a new weekly every Monday. Everybody gets the same board.", onBack)
            }
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp).padding(top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val daily = Directives.daily(today)
                DirectiveCard(
                    "TODAY // ${fmtDay(today)}", daily, store.directiveBars(daily.id), store.directiveBest(daily.id),
                    saved = store.savedDaily?.optString("date") == today.toString(),
                    onPlay = { open = "d:$today" }, onContinue = { onContinue(Mode.DAILY) }
                )
                val weekly = Directives.weekly(today)
                val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                DirectiveCard(
                    "THIS WEEK // UNTIL ${fmtDay(sunday)}", weekly, store.directiveBars(weekly.id), store.directiveBest(weekly.id),
                    saved = store.savedWeekly?.optString("date")?.let { runCatching { Directives.weekly(LocalDate.parse(it)).id }.getOrNull() } == weekly.id,
                    onPlay = { open = "w:$today" }, onContinue = { onContinue(Mode.WEEKLY) }
                )
                Calendar(store, LocalDate.parse(month), today, onMonth = { month = it.toString() }, onDay = { open = "d:$it" })
            }
        }
        AnimatedVisibility(open != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            val key = open ?: return@AnimatedVisibility
            val date = LocalDate.parse(key.substring(2))
            val s = if (key.startsWith("w")) Directives.weekly(date) else Directives.daily(date)
            val savedHere = if (s.kind == Sector.Kind.WEEKLY) store.savedWeekly?.optString("date")?.let { runCatching { Directives.weekly(LocalDate.parse(it)).id }.getOrNull() } == s.id
            else store.savedDaily?.optString("date") == date.toString()
            Briefing(
                s, bars = store.directiveBars(s.id), best = store.directiveBest(s.id), savedHere = savedHere, assistOffered = false,
                kicker = if (s.kind == Sector.Kind.WEEKLY) "WEEKLY DIRECTIVE // ${s.id.removePrefix("W")}" else "DAILY DIRECTIVE // ${fmtDay(date)} ${date.year}",
                onClose = { open = null }, onStart = { open = null; onStart(s, date) }
            )
        }
    }
}

@Composable
private fun DirectiveCard(kicker: String, s: Sector, bars: Int, best: Int, saved: Boolean, onPlay: () -> Unit, onContinue: () -> Unit) {
    ConsolePanel(Modifier.fillMaxWidth(), edge = if (bars == 3) C.Amber else C.Main) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kicker, style = mono(12.sp, C.Amber, 1.5.sp), modifier = Modifier.weight(1f))
                Bars(bars, size = 16.dp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TileIcon(s.rules.target, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name.substringAfter("// "), style = mono(16.sp, C.Bright, 1.sp))
                    Text(s.twist, style = mono(12.sp, C.Mid, 0.sp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "PAR ${s.par}  //  ${lockGoal(s.lockBudget)}" + if (best > 0) "  //  BEST $best" else "",
                style = mono(11.sp, C.Dim, 0.5.sp)
            )
            Spacer(Modifier.height(10.dp))
            if (saved) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommandButton("CONTINUE", Modifier.weight(1f), primary = true, height = 48.dp, onClick = onContinue)
                    CommandButton("BRIEFING", Modifier.weight(1f), color = C.Main, height = 48.dp, onClick = onPlay)
                }
            } else {
                CommandButton(if (bars > 0) "PLAY AGAIN" else "BRIEFING", Modifier.fillMaxWidth(), primary = bars == 0, height = 48.dp, onClick = onPlay)
            }
        }
    }
}

/** A month of dailies: tap any day up to today. */
@Composable
private fun Calendar(store: Store, month: LocalDate, today: LocalDate, onMonth: (LocalDate) -> Unit, onDay: (LocalDate) -> Unit) {
    val firstMonth = LocalDate.ofEpochDay(store.firstDay).withDayOfMonth(1)
    val thisMonth = today.withDayOfMonth(1)
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val canBack = month.isAfter(firstMonth)
                val canNext = month.isBefore(thisMonth)
                Text(if (canBack) "◂" else " ", style = mono(18.sp, C.Main, 0.sp),
                    modifier = Modifier.clickable(enabled = canBack, role = Role.Button, onClick = click { onMonth(month.minusMonths(1)) })
                        .semantics { contentDescription = "Previous month" }.padding(horizontal = 10.dp, vertical = 4.dp))
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase()} ${month.year}",
                    style = mono(14.sp, C.Bright, 2.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                Text(if (canNext) "▸" else " ", style = mono(18.sp, C.Main, 0.sp),
                    modifier = Modifier.clickable(enabled = canNext, role = Role.Button, onClick = click { onMonth(month.plusMonths(1)) })
                        .semantics { contentDescription = "Next month" }.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row {
                listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").forEach {
                    Text(it, style = mono(11.sp, C.Dim, 1.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            val lead = month.dayOfWeek.value - 1
            val days = month.lengthOfMonth()
            val cells = lead + days
            for (row in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val i = row * 7 + col - lead
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                            if (i in 0 until days) DayCell(store, month.plusDays(i.toLong()), today, onDay)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val cleared = (0 until days).count { store.directiveBars(Directives.dailyId(month.plusDays(it.toLong()))) > 0 }
            val file = DISPATCHES[month.monthValue - 1]
            Text(
                if (cleared == days) "${file.title} DECRYPTED. IT IS IN THE ARCHIVE."
                else "${file.title}: $cleared OF $days DAYS. CLEAR THEM ALL TO DECRYPT IT.",
                style = mono(11.sp, if (cleared == days) C.Green else C.Mid, 0.5.sp)
            )
        }
    }
}

@Composable
private fun DayCell(store: Store, day: LocalDate, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val bars = store.directiveBars(Directives.dailyId(day))
    val future = day.isAfter(today)
    val isToday = day == today
    val ink = when {
        future -> C.Dim.copy(alpha = 0.5f)
        bars == 3 -> C.Amber
        bars > 0 -> C.Bright
        else -> C.Mid
    }
    Column(
        Modifier.fillMaxSize()
            .background(if (bars > 0) C.Main.copy(alpha = 0.10f) else C.Void.copy(alpha = 0f))
            .border(1.dp, if (isToday) C.Amber else C.Main.copy(alpha = if (future) 0.08f else 0.22f))
            .clickable(enabled = !future, role = Role.Button, onClick = click { onDay(day) })
            .semantics { contentDescription = "${fmtDay(day)}, " + if (bars > 0) "$bars bars" else if (future) "not yet" else "not cleared" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(day.dayOfMonth.toString(), style = mono(13.sp, ink, 0.sp))
        if (bars > 0) Text("■".repeat(bars), style = mono(7.sp, C.Amber, 0.sp))
    }
}

fun fmtDay(d: LocalDate) = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()}"
