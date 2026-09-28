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
import com.buyless.app.data.model.CategoryKeys
import com.buyless.app.data.model.Direction
import com.buyless.app.data.model.MANUAL_SOURCE
import com.buyless.app.data.model.TransactionDraft
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.ui.components.kindOf
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import kotlinx.coroutines.Job
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
    /** Category key: a built-in name ("FOOD") or a custom one ("custom:3"). See CategoryKeys. */
    var category by mutableStateOf(Category.FOOD.name)
    var sourcePackage by mutableStateOf(MANUAL_SOURCE)
        private set
    var isInternal by mutableStateOf(false)
        private set

    /**
     * For a transfer between your own apps: the other app (where it went for money out, where it
     * came from for money in). Required when isInternal is on, so every transfer reads "BIMB → TNG".
     */
    var counterpartPackage by mutableStateOf<String?>(null)
        private set
    var counterpartError by mutableStateOf<String?>(null)
        private set

    /**
     * The choice under the category chips. false = "This entry only", true = "Every <merchant> entry",
     * which also files future entries from this merchant under the chosen category.
     */
    var rememberForMerchant by mutableStateOf(false)
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

    /** Labels of apps seen on the loaded row, kept apart so the watched-apps flow cannot overwrite them. */
    private val loadedLabels = HashMap<String, String>()

    /**
     * True once the user taps a category. Until then, typing a merchant with a remembered category
     * picks that category for them; afterwards their choice is never overridden.
     */
    private var categoryTouched = false
    private var lookupJob: Job? = null

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
        // Looked up again here, so a merchant remembered a moment ago (earlier in this same queue) applies too.
        val remembered = tx.rememberedCategory(p.guessMerchant, direction.name)
        category = remembered
            ?: p.guessCategory?.takeIf { isPickable(it) }
            ?: if (direction == Direction.IN) Category.INCOME.name else Category.OTHER.name
        rememberForMerchant = remembered != null
        categoryTouched = false
        sourcePackage = p.sourcePackage
        loadedLabels[p.sourcePackage] = p.sourceLabel
        dateMillis = p.postedAt
        isInternal = false
        counterpartPackage = null
        counterpartError = null
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
        category = t.category
        sourcePackage = t.sourcePackage
        loadedLabels[t.sourcePackage] = t.sourceLabel
        isInternal = t.isInternal
        counterpartPackage = t.counterpartPackage
        t.counterpartPackage?.let { pkg -> t.counterpartLabel?.let { loadedLabels[pkg] = it } }
        rememberForMerchant = !t.isInternal && tx.rememberedCategory(t.merchant, t.direction) == t.category
        dateMillis = t.timestamp
        raw = t.rawText?.let { RawNotification(t.sourcePackage, t.sourceLabel, AppKind.WALLET, Dates.fullDate(t.timestamp), it) }
    }

    /** A category chip was tapped (or a new category was just made). */
    fun pickCategory(key: String) {
        category = key
        categoryTouched = true
    }

    /**
     * Merchant typed. In Quick check and new entries, a merchant with a remembered category selects
     * it straight away, unless the user already picked a category themselves. Editing an old entry
     * never changes its category this way.
     */
    fun onMerchantChange(value: String) {
        merchant = value
        if (mode == EditorMode.EDIT || categoryTouched || isInternal) return
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val remembered = tx.rememberedCategory(value, direction.name)
            if (remembered != null) {
                category = remembered
                rememberForMerchant = true
            } else {
                rememberForMerchant = false
            }
        }
    }

    /** The name "Every ... entry" refers to: what was typed, or the app's name when left blank. */
    fun merchantForRule(): String = merchant.trim().ifEmpty { labelOf(sourcePackage) }

    fun selectSource(packageName: String) {
        sourcePackage = packageName
        // Money cannot move from an app to itself.
        if (counterpartPackage == packageName) counterpartPackage = null
    }

    fun setTransfer(value: Boolean) {
        isInternal = value
        counterpartError = null
        if (!value) {
            counterpartPackage = null
            // Leaving the transfer: TRANSFER is not a chip, so fall back to what the direction suggests.
            if (category == Category.TRANSFER.name) category = if (direction == Direction.IN) Category.INCOME.name else Category.OTHER.name
        }
    }

    fun selectCounterpart(packageName: String) {
        counterpartPackage = packageName
        counterpartError = null
    }

    /** resetTo = true picks the default for the direction, used when the chosen category was just deleted. */
    fun setDirectionAndFixCategory(value: Direction, resetTo: Boolean = false) {
        direction = value
        if (resetTo) {
            category = if (value == Direction.IN) Category.INCOME.name else Category.OTHER.name
            return
        }
        // Keep the category sensible when flipping direction, without overriding a deliberate choice.
        if (value == Direction.IN && CategoryKeys.builtIn(category) != null && category != Category.INCOME.name && category != Category.TRANSFER.name) {
            category = Category.INCOME.name
        }
        if (value == Direction.OUT && category == Category.INCOME.name) category = Category.OTHER.name
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
        if (isInternal && counterpartPackage == null) {
            counterpartError = if (direction == Direction.OUT) "Pick the app the money went to" else "Pick the app the money came from"
            return
        }
        val label = labelOf(sourcePackage)
        val counterpart = if (isInternal) counterpartPackage else null
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
            counterpartPackage = counterpart,
            counterpartLabel = counterpart?.let(this::labelOf),
        )
        val remember = rememberForMerchant && !isInternal
        viewModelScope.launch {
            if (remember) tx.rememberCategory(draft.merchant, draft.direction, draft.category)
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

    private fun labelOf(packageName: String): String = when (packageName) {
        MANUAL_SOURCE -> "Cash / other"
        else -> sourceLabels[packageName] ?: loadedLabels[packageName] ?: raw?.label?.takeIf { packageName == raw?.packageName } ?: packageName
    }

    /** Any stored key but TRANSFER can be shown as a chip (built-in or one of the user's own). */
    private fun isPickable(key: String): Boolean =
        key != Category.TRANSFER.name && (CategoryKeys.builtIn(key) != null || CategoryKeys.customId(key) != null)

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
