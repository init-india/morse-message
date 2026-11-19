@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.morsemessage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsemessage.app
import com.morsemessage.data.Outputs
import com.morsemessage.learn.Article
import com.morsemessage.learn.LearnContent
import com.morsemessage.morse.MorseCode

@Composable
fun LearnScreen() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { KochTrainer() }
        item { ReferenceCard("Letters", MorseCode.letters) }
        item { ReferenceCard("Numbers", MorseCode.digits) }
        item { ReferenceCard("Punctuation", MorseCode.punctuation) }
        item { ProsignCard() }
        items(LearnContent.articles, key = { it.title }) { ArticleCard(it) }
    }
}

@Composable
private fun ReferenceCard(title: String, table: Map<Char, String>) {
    val context = LocalContext.current
    val app = context.app
    SectionCard(title) {
        Text("Tap to hear it.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            table.forEach { (ch, code) ->
                OutlinedCard(
                    Modifier.width(76.dp).clickable {
                        app.player.playMorse(code, app.settings.current, Outputs(sound = true))
                    }
                ) {
                    Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ch.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(MorseCode.pretty(code), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProsignCard() {
    val app = LocalContext.current.app
    SectionCard("Prosigns (sent as one run-together character)") {
        MorseCode.prosigns.forEach { (name, pair) ->
            val (code, meaning) = pair
            Row(
                Modifier.fillMaxWidth().clickable {
                    app.player.playMorse(code, app.settings.current, Outputs(sound = true))
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, Modifier.width(56.dp), fontWeight = FontWeight.Bold)
                Text(MorseCode.pretty(code), Modifier.width(120.dp), fontFamily = FontFamily.Monospace)
                Text(meaning, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article) {
    var open by rememberSaveable(article.title) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(article.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            if (open) article.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Koch-method practice: hear a group of 5 characters, type what you heard, get a score. */
@Composable
private fun KochTrainer() {
    val app = LocalContext.current.app
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val maxLesson = LearnContent.KOCH_ORDER.length - 1
    val lesson = settings.kochLesson.coerceIn(1, maxLesson)
    val charset = LearnContent.KOCH_ORDER.take(lesson + 1)

    var group by remember(lesson) { mutableStateOf("") }
    var answer by remember(lesson) { mutableStateOf("") }
    var result by remember(lesson) { mutableStateOf<Pair<Int, String>?>(null) }

    fun newGroup() {
        // Lean towards the newest character, which needs the most practice.
        group = (1..5).map { if (Math.random() < 0.3) charset.last() else charset.random() }.joinToString("")
        answer = ""
        result = null
        app.player.playText(group, app.settings.current, Outputs(sound = true))
    }

    SectionCard("Practice (Koch method)") {
        Text(
            "Lesson $lesson of $maxLesson. Characters: ${charset.toList().joinToString(" ")}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Listen to 5 characters, then type what you heard. Reach 90% to unlock the next character.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { newGroup() }) { Text(if (group.isEmpty()) "Start" else "New group") }
            OutlinedButton(enabled = group.isNotEmpty(), onClick = {
                app.player.playText(group, app.settings.current, Outputs(sound = true))
            }) { Text("Replay") }
        }
        if (group.isNotEmpty()) {
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it.uppercase().filter { c -> !c.isWhitespace() }.take(5) },
                label = { Text("What did you hear?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = answer.isNotEmpty() && result == null, onClick = {
                    val correct = group.indices.count { it < answer.length && answer[it] == group[it] }
                    result = (correct * 100 / group.length) to group
                }) { Text("Check") }
            }
        }
        result?.let { (score, expected) ->
            Text("$score% correct. It was: $expected", style = MaterialTheme.typography.titleMedium)
            if (score >= 90 && lesson < maxLesson) {
                Text("Great! Ready for the next character: ${LearnContent.KOCH_ORDER[lesson + 1]}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = lesson > 1, onClick = { app.settings.update { it.copy(kochLesson = lesson - 1) } }) {
                Text("◀ Previous lesson")
            }
            TextButton(enabled = lesson < maxLesson, onClick = { app.settings.update { it.copy(kochLesson = lesson + 1) } }) {
                Text("Next lesson ▶")
            }
        }
    }
}
