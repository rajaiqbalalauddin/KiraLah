package com.buyless.app.data.repo

import com.buyless.app.data.db.RawNotificationDao
import com.buyless.app.data.db.RawNotificationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Notification samples from watched apps. This is the "listen first" step for banks without exact
 * templates yet (MAE, TNG...): collect what they really send, share it, then add a template.
 */
class SampleRepository(private val dao: RawNotificationDao) {

    fun observeAll(): Flow<List<RawNotificationEntity>> = dao.observeAll().distinctUntilChanged()

    suspend fun record(sample: RawNotificationEntity) {
        dao.insert(sample)
        dao.trim(MAX_SAMPLES)
    }

    suspend fun clear() = dao.clear()

    /**
     * Text for sharing. Long digit runs (account, card and phone numbers) are masked first, so a
     * sample can be pasted into a chat without leaking them. Amounts are short runs and survive.
     */
    fun exportText(samples: List<RawNotificationEntity>): String = samples.joinToString("\n\n") { s ->
        val title = s.title?.let { "$it | " }.orEmpty()
        "[${s.sourceLabel}] $title${s.body}".replace(LONG_DIGITS, "######")
    }

    private companion object {
        const val MAX_SAMPLES = 300
        val LONG_DIGITS = Regex("""\d{6,}""")
    }
}
