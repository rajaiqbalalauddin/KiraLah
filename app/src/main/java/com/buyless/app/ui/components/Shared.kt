package com.buyless.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.buyless.app.AppContainer
import com.buyless.app.BuylessApp
import com.buyless.app.ui.theme.BColors

/**
 * Creates a ViewModel wired to the shared AppContainer. Saves writing a Factory class per screen,
 * and the ViewModel survives rotation like any other.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM = viewModel<VM>(
    factory = viewModelFactory {
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BuylessApp
            create(app.container, createSavedStateHandle())
        }
    },
)

/** Previous / next month control. Next is disabled on the current month since the future is empty. */
@Composable
fun MonthSwitcher(label: String, canGoNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(22.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
        }
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 2.dp))
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
        }
    }
}
