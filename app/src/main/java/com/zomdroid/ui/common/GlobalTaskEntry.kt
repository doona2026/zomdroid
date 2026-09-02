package com.zomdroid.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zomdroid.R
import com.zomdroid.ui.state.TaskUiState
import com.zomdroid.ui.state.TaskUiStatus
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download

@Composable
fun GlobalTaskEntry(
    task: TaskUiState,
    onClick: () -> Unit,
) {
    if (task.status == TaskUiStatus.Idle || task.taskCount <= 0) return
    val description = when (task.status) {
        TaskUiStatus.Failed -> stringResource(R.string.ui_global_task_failed)
        else -> stringResource(R.string.ui_global_task_entry)
    }
    BadgedBox(
        badge = {
            Badge {
                Text(text = task.taskCount.toString())
            }
        },
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = MiuixIcons.Download,
                contentDescription = description,
            )
        }
    }
}
