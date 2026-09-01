package com.zomdroid.ui.tools

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zomdroid.R
import java.util.Locale

@Composable fun WikiScreen(onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.TextButton(onClick = onBack) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.app_shell_back)) }
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            WebView(context).apply {
                val file = when (Locale.getDefault().language) { "ru" -> "index_ru.html"; "zh" -> "index_zh.html"; else -> "index.html" }
                loadUrl("file:///android_asset/wiki/$file")
            }
        })
    }
}
