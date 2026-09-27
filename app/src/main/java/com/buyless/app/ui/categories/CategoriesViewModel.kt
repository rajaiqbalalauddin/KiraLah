package com.buyless.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.db.CustomCategoryEntity
import com.buyless.app.data.repo.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Create, edit and delete custom categories. Shared by the editor's category row and the Settings
 * page, so both behave the same way.
 */
class CategoriesViewModel(private val repo: CategoryRepository) : ViewModel() {

    val custom: StateFlow<List<CustomCategoryEntity>> = repo.observeCustom()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a new category (existing = null) or changes one. onCreated gets the new key, so the editor can select it. */
    fun save(existing: CustomCategoryEntity?, name: String, iconKey: String, colorIndex: Int, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (existing == null) {
                onCreated(repo.create(name, iconKey, colorIndex))
            } else {
                repo.update(existing.copy(name = name, iconKey = iconKey, colorIndex = colorIndex))
            }
        }
    }

    suspend fun usageCount(id: Long): Int = repo.usageCount(id)

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
