package com.buyless.app.data.repo

import androidx.room.withTransaction
import com.buyless.app.data.db.BuylessDatabase
import com.buyless.app.data.db.CategoryRuleEntity
import com.buyless.app.data.db.CustomCategoryEntity
import com.buyless.app.data.model.CategoryKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The user's own spending categories. Kept apart from TransactionRepository because categories
 * change rarely and are read by every list, while transactions change all the time.
 */
class CategoryRepository(private val db: BuylessDatabase) {
    private val dao = db.customCategoryDao()
    private val ruleDao = db.categoryRuleDao()

    fun observeCustom(): Flow<List<CustomCategoryEntity>> = dao.observeAll().distinctUntilChanged()

    /** Merchants whose category is remembered, for the list in Settings > Categories. */
    fun observeRules(): Flow<List<CategoryRuleEntity>> = ruleDao.observeAll().distinctUntilChanged()

    /** Forgets one remembered merchant. Entries already saved keep their category. */
    suspend fun deleteRule(id: Long) = ruleDao.delete(id)

    /** Creates a category and returns its key ("custom:12"), so the editor can select it at once. */
    suspend fun create(name: String, iconKey: String, colorIndex: Int): String {
        val id = dao.insert(
            CustomCategoryEntity(name = name.trim(), iconKey = iconKey, colorIndex = colorIndex, createdAt = System.currentTimeMillis()),
        )
        return CategoryKeys.custom(id)
    }

    suspend fun update(category: CustomCategoryEntity) = dao.update(category.copy(name = category.name.trim()))

    /** How many transactions use this category, so the delete dialog can say what will move. */
    suspend fun usageCount(id: Long): Int = dao.usageCount(CategoryKeys.custom(id))

    /**
     * Deletes the category, moves its transactions to Other (or Income) and drops its limits and
     * remembered merchants, in one transaction.
     */
    suspend fun delete(id: Long) {
        db.withTransaction {
            dao.reassign(CategoryKeys.custom(id))
            db.spendingLimitDao().deleteForCategory(CategoryKeys.custom(id))
            ruleDao.deleteForCategory(CategoryKeys.custom(id))
            dao.delete(id)
        }
    }
}
