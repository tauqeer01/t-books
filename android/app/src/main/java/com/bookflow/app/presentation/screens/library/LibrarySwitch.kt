package com.bookflow.app.presentation.screens.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Library's Books | Collections switch (Collections used to be its own tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySwitch(showCollections: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        SegmentedButton(selected = !showCollections, onClick = { onChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Books") }
        SegmentedButton(selected = showCollections, onClick = { onChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Collections") }
    }
}
