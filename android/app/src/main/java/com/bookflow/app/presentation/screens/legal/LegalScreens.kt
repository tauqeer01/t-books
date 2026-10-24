package com.bookflow.app.presentation.screens.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.presentation.components.BookFlowTopBar
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/** Privacy policy or terms, readable offline. */
@Composable
fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    Scaffold(
        topBar = { BookFlowTopBar(title = document.title, subtitle = "Effective ${LegalInfo.EFFECTIVE_DATE}", onBack = onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 16.dp,
                bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(document.sections) { section ->
                // Readable line length on tablets
                Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        section.heading,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        section.body,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Licenses of every bundled library, generated at build time by the AboutLibraries plugin. */
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { BookFlowTopBar(title = "Open-source licenses", onBack = onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LibrariesContainer(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        )
    }
}
