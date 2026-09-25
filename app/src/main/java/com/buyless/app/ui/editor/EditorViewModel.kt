package com.buyless.app.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.AppKind
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.data.model.MANUAL_SOURCE
import com.buyless.app.data.model.TransactionDraft
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.ui.components.kindOf
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class EditorMode { REVIEW, MANUAL, EDIT }

@Immutable
data class SourceOption(val packageName: String, val label: String, val kind: AppKind?)

/** The raw notification shown at the top of Quick check, so the user can see what was received. */
@Immutable
data class RawNotification(val packageName: String, val label: String, val kind: AppKind, val timeText: String, val text: String) {
    /** "USD 21.60" when the alert is in a foreign currency, so the user knows to type the ringgit amount. */
    val foreign: String? get() = FOREIGN.find(text)?.let { "${it.groupValues[1].uppercase()} ${it.groupValues[2]}" }

    private companion object {
        val FOREIGN = Regex("""\b(USD|SGD|EUR|GBP|JPY|AUD|THB|IDR|CNY|HKD|KRW|TWD|INR)\s?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    }
}

/**
 * One form for three jobs: confirm a Quick check item, add a transaction by hand, or edit one.
 * Sharing the form keeps the three flows identical for the user and the validation in one place.
 * Form fields are Compose state (not flows) because they change on every keystroke and are only
 * read by this screen.
 */
class EditorViewModel(
    private val tx: TransactionRepository,
    apps: AppsRepository,
    handle: SavedStateHandle,
) : ViewModel() {

    private val pendingArg: Long = handle.get<Long>(ARG_PENDING) ?: -1L
    private val txnArg: Long = handle.get<Long>(ARG_TXN) ?: -1L

    val mode: EditorMode = when {
        pendingArg >= 0 -> EditorMode.REVIEW
        txnArg > 0 -> EditorMode.EDIT
        else -> EditorMode.MANUAL
    }

    var amountText by mutableStateOf("")
    var direction by mutableStateOf(Direction.OUT)
    var merchant by mutableStateOf("")
    var category by mutableStateOf(Category.FOOD)
    var sourcePackage by mutableStateOf(MANUAL_SOURCE)
    var isInternal by mutableStateOf(false)
    var dateMillis by mutableLongStateOf(System.currentTimeMillis())
    var raw by mutableStateOf<RawNotification?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var reviewedCount by mutableIntStateOf(0)
        private set

    /** True when Quick check was opened with nothing waiting. */
    var queueEmpty by mutableStateOf(false)
        private set

    /** Set when the screen should close (saved, deleted, or queue finished). */
    var finished by mutableStateOf(false)
        private set

    private var currentPendingId: Long = pendingArg
    private var sourceLabels: Map<String, String> = emptyMap()

    val sources: StateFlow<List<SourceOption>> = apps.observeWatched()
        .map { watched ->
            sourceLabels = watched.associate { it.packageName to it.label }
            watched.map { SourceOption(it.packageName, it.label, kindOf(it.kind)) } +
                SourceOption(MANUAL_SOURCE, "Cash / other", null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openCount: StateFlow<Int> = tx.observeOpenPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            when (mode) {
                EditorMode.REVIEW -> {
                    // 0 means "start from the oldest waiting item" (opened from the bell or banner).
                    val id = if (pendingArg > 0) pendingArg else tx.firstOpenPendingId()
                    if (id == null) queueEmpty = true else loadPending(id)
                }
                EditorMode.EDIT -> loadTransaction(txnArg)
                EditorMode.MANUAL -> Unit
            }
        }
    }

    private suspend fun loadPending(id: Long) {
        val p = tx.getPending(id)
        if (p == null) {
            queueEmpty = true
            return
        }
        currentPendingId = id
        raw = RawNotification(p.sourcePackage, p.sourceLabel, AppKind.WALLET, "${Dates.relativeDay(Dates.toDate(p.postedAt))}, ${Dates.timeOf(p.postedAt)}", p.text)
        amountText = p.guessAmountSen?.let(Money::toInput) ?: ""
        direction = p.guessDirection?.let { runCatching { Direction.valueOf(it) }.getOrNull() } ?: Direction.OUT
        merchant = p.guessMerchant ?: ""
        category = p.guessCategory?.let { runCatching { Category.valueOf(it) }.getOrNull() }
            ?: if (direction == Direction.IN) Category.INCOME else Category.OTHER
        sourcePackage = p.sourcePackage
        dateMillis = p.postedAt
        isInternal = false
        error = null
    }

    private suspend fun loadTransaction(id: Long) {
        val t = tx.getTransaction(id)
        if (t == null) {
            finished = true
            return
        }
        amountText = Money.toInput(t.amountSen)
        direction = Direction.valueOf(t.direction)
        merchant = t.merchant
        category = runCatching { Category.valueOf(t.category) }.getOrDefault(Category.OTHER)
        sourcePackage = t.sourcePackage
        sourceLabels = sourceLabels + (t.sourcePackage to t.sourceLabel)
        isInternal = t.isInternal
        dateMillis = t.timestamp
        raw = t.rawText?.let { RawNotification(t.sourcePackage, t.sourceLabel, AppKind.WALLET, Dates.fullDate(t.timestamp), it) }
    }

    fun setDirectionAndFixCategory(value: Direction) {
        direction = value
        // Keep the category sensible when flipping direction, without overriding a deliberate choice.
        if (value == Direction.IN && category != Category.INCOME && category != Category.TRANSFER) category = Category.INCOME
        if (value == Direction.OUT && category == Category.INCOME) category = Category.OTHER
    }

    /** Keeps the time of day when only the date changes, so ordering within a day stays right. */
    fun setDate(utcMidnightMillis: Long) {
        val picked = Instant.ofEpochMilli(utcMidnightMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val oldTime = if (mode == EditorMode.MANUAL && picked == LocalDate.now(Dates.zone)) {
            LocalTime.now(Dates.zone)
        } else {
            Instant.ofEpochMilli(dateMillis).atZone(Dates.zone).toLocalTime()
        }
        dateMillis = picked.atTime(oldTime).atZone(Dates.zone).toInstant().toEpochMilli()
    }

    fun save() {
        val amount = Money.parse(amountText)
        if (amount == null) {
            error = "Enter an amount, like 12.50"
            return
        }
        if (dateMillis > System.currentTimeMillis() + 60_000) {
            error = "The date cannot be in the future"
            return
        }
        error = null
        val label = when (sourcePackage) {
            MANUAL_SOURCE -> "Cash / other"
            else -> sourceLabels[sourcePackage] ?: raw?.label ?: sourcePackage
        }
        val draft = TransactionDraft(
            amountSen = amount,
            direction = direction,
            merchant = merchant.trim().ifEmpty { label },
            category = category,
            sourcePackage = sourcePackage,
            sourceLabel = label,
            timestamp = dateMillis,
            isInternal = isInternal,
            rawText = raw?.text,
        )
        viewModelScope.launch {
            when (mode) {
                EditorMode.REVIEW -> {
                    tx.savePending(currentPendingId, draft)
                    reviewedCount++
                    advanceQueue()
                }
                EditorMode.MANUAL -> {
                    tx.addManual(draft)
                    finished = true
                }
                EditorMode.EDIT -> {
                    tx.update(txnArg, draft)
                    finished = true
                }
            }
        }
    }

    fun ignore() {
        if (mode != EditorMode.REVIEW) return
        viewModelScope.launch {
            tx.ignorePending(currentPendingId)
            reviewedCount++
            advanceQueue()
        }
    }

    fun delete() {
        if (mode != EditorMode.EDIT) return
        viewModelScope.launch {
            tx.delete(txnArg)
            finished = true
        }
    }

    /** Quick check flows straight into the next waiting item, so clearing a backlog is one tap each. */
    private suspend fun advanceQueue() {
        val next = tx.firstOpenPendingId()
        if (next == null) finished = true else loadPending(next)
    }

    companion object {
        const val ARG_PENDING = "pendingId"
        const val ARG_TXN = "txnId"
    }
}
