package com.buyless.app.ui.categories

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.buyless.app.data.db.CustomCategoryEntity
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.CategoryKeys
import com.buyless.app.ui.components.CategoryStyle
import com.buyless.app.ui.components.style
import com.buyless.app.ui.theme.BColors

/**
 * A colour a custom category can take: a soft fill and a strong ink, for day and for night.
 * Resolved when read, so badges recolour the moment night mode flips.
 */
@Immutable
data class CategoryColor(val dayBg: Color, val dayFg: Color, val nightBg: Color, val nightFg: Color) {
    val bg: Color get() = if (BColors.isDark) nightBg else dayBg
    val fg: Color get() = if (BColors.isDark) nightFg else dayFg
}

/**
 * The ten colours offered in the category sheet. Stored by index, so only append to this list;
 * reordering would repaint categories people already made.
 */
val CategoryColors = listOf(
    CategoryColor(Color(0xFFECE8FF), Color(0xFF5B3DF5), Color(0xFF2E2757), Color(0xFFA99BFF)), // violet
    CategoryColor(Color(0xFFFFE6DC), Color(0xFFC23F12), Color(0xFF3D1F16), Color(0xFFFF9B78)), // coral
    CategoryColor(Color(0xFFFFF1D6), Color(0xFF9A5200), Color(0xFF3A2A0E), Color(0xFFF0A640)), // amber
    CategoryColor(Color(0xFFFFF7C2), Color(0xFF7A6200), Color(0xFF363010), Color(0xFFFFD84D)), // yellow
    CategoryColor(Color(0xFFD8F5EA), Color(0xFF0B7A5F), Color(0xFF123A2F), Color(0xFF4FD8A8)), // green
    CategoryColor(Color(0xFFD5F5F5), Color(0xFF0B7373), Color(0xFF0F3535), Color(0xFF4FD1D1)), // teal
    CategoryColor(Color(0xFFDDEBFF), Color(0xFF1F55C7), Color(0xFF16284A), Color(0xFF8DB4FF)), // blue
    CategoryColor(Color(0xFFFFE3EE), Color(0xFFB8275F), Color(0xFF3F1A2A), Color(0xFFFF8FB8)), // pink
    CategoryColor(Color(0xFFF1E4FF), Color(0xFF7B2FBF), Color(0xFF2F1B45), Color(0xFFD29BFF)), // purple
    CategoryColor(Color(0xFFECEAF2), Color(0xFF4F4B63), Color(0xFF2A2838), Color(0xFFBDB8D1)), // grey
)

fun categoryColor(index: Int): CategoryColor = CategoryColors[index.mod(CategoryColors.size)]

/** How a user-made category looks. Call it during composition (not once and cache it), so colours follow night mode. */
fun CustomCategoryEntity.style(): CategoryStyle {
    val c = categoryColor(colorIndex)
    return CategoryStyle(name, categoryIcon(iconKey), c.bg, c.fg)
}

/** One chip in the editor's category row. customId is set only for the user's own categories. */
@Immutable
data class CategoryOption(val key: String, val customId: Long? = null)

/**
 * Turns any stored category key ("FOOD", "custom:3") into how it looks. Built from the live list
 * of custom categories and shared through LocalCategoryCatalog, so lists stay plain data (a key)
 * and every screen shows a renamed or recoloured category straight away.
 */
@Immutable
class CategoryCatalog(val custom: List<CustomCategoryEntity>) {
    private val byKey: Map<String, CustomCategoryEntity> = custom.associateBy { CategoryKeys.custom(it.id) }

    /** Read at composition time, so colours follow night mode. Unknown keys show as Other. */
    fun style(key: String): CategoryStyle {
        CategoryKeys.builtIn(key)?.let { return it.style() }
        return byKey[key]?.style() ?: Category.OTHER.style()
    }

    fun customFor(key: String): CustomCategoryEntity? = byKey[key]

    /** Editor chips: the built-ins people pick by hand, then the user's own, alphabetical. */
    val pickable: List<CategoryOption> =
        BuiltInPickable.map { CategoryOption(it.name) } + custom.map { CategoryOption(CategoryKeys.custom(it.id), it.id) }

    private companion object {
        /** TRANSFER is set by pairing, never picked, so it is left out. */
        val BuiltInPickable = listOf(Category.FOOD, Category.TRANSPORT, Category.SHOPPING, Category.BILLS, Category.INCOME, Category.OTHER)
    }
}

/** The live catalog, provided once at the top of the app. */
val LocalCategoryCatalog = compositionLocalOf { CategoryCatalog(emptyList()) }
