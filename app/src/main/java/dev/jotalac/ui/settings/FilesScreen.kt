package dev.jotalac.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jotalac.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

private data class Node(val file: File, val depth: Int)

private val prettyJson = Json { prettyPrint = true }

/** Debug browser of the core's storage directory, the counterpart of web-client FileExplorer.vue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(root: File, onBack: () -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf<File?>(null) }
    val nodes by produceState(emptyList<Node>(), reload, expanded) {
        value = withContext(Dispatchers.IO) { buildList { collect(root, 0, expanded) } }
    }
    val preview by produceState<String?>(null, selected) {
        value = selected?.let { withContext(Dispatchers.IO) { previewOf(it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.files_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Outlined.Refresh, null) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (nodes.isEmpty()) {
                Text(stringResource(R.string.files_empty), Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(nodes, key = { it.file.path }) { node ->
                    val dir = node.file.isDirectory
                    val open = node.file.path in expanded
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable {
                                if (dir) expanded = if (open) expanded - node.file.path else expanded + node.file.path
                                else selected = node.file
                            }
                            .padding(start = (12 + node.depth * 16).dp, end = 12.dp),
                    ) {
                        Icon(
                            when {
                                dir && open -> Icons.Outlined.FolderOpen
                                dir -> Icons.Outlined.Folder
                                else -> Icons.Outlined.Description
                            },
                            null,
                            Modifier.size(18.dp),
                        )
                        Text(node.file.name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                        if (!dir) Text(sizeLabel(node.file.length()), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            preview?.let { text ->
                HorizontalDivider()
                Text(selected?.name.orEmpty(), Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
                SelectionContainer(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                ) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun MutableList<Node>.collect(dir: File, depth: Int, expanded: Set<String>) {
    val children = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
    for (child in children) {
        add(Node(child, depth))
        if (child.isDirectory && child.path in expanded) collect(child, depth + 1, expanded)
    }
}

private fun sizeLabel(bytes: Long) = if (bytes < 1024) "$bytes B" else "%.1f KiB".format(bytes / 1024.0)

private fun previewOf(file: File): String {
    if (file.length() > 2L * 1024 * 1024) return "(${sizeLabel(file.length())})"
    val bytes = file.readBytes()
    val control = bytes.count { it < 9 || (it in 14..31) }
    if (bytes.contains(0) || control > bytes.size * 0.02) return "(binary, ${sizeLabel(bytes.size.toLong())})"
    val text = bytes.decodeToString()
    if (file.extension == "json") {
        runCatching { return prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text)) }
    }
    return text
}
