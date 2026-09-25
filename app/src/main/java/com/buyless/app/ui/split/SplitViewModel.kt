package com.buyless.app.ui.split

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.db.PaymentQrEntity
import com.buyless.app.data.repo.QrRepository
import com.buyless.app.split.ParsedReceipt
import com.buyless.app.split.ReceiptScanner
import com.buyless.app.split.Share
import com.buyless.app.split.SplitItem
import com.buyless.app.split.SplitMath
import com.buyless.app.util.AppPrefs
import com.buyless.app.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SplitStep { SCAN, ITEMS, BOARD, SUMMARY }

/** Someone at the table. colorIndex picks their avatar colour, so each person is easy to spot. */
@Immutable
data class Person(val id: Long, val name: String, val colorIndex: Int)

/** An item while it is being checked. Price is kept as text so half-typed values are allowed. */
@Immutable
data class EditableItem(val id: Long, val name: String, val priceText: String) {
    val priceSen: Long? get() = Money.parse(priceText)
}

/** An item on the split board. */
@Immutable
data class BoardItem(val id: Long, val name: String, val priceSen: Long, val owners: List<Long>) {
    val assigned: Boolean get() = owners.isNotEmpty()
}

/**
 * Drives the whole Split flow: scan -> check items -> drag onto people -> totals and QR.
 * Kept in one ViewModel (not one per screen) because every step edits the same bill.
 * Board state is Compose state, so a drop updates only the blocks and bubbles that changed.
 */
class SplitViewModel(
    private val scanner: ReceiptScanner,
    private val qrs: QrRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    var step by mutableStateOf(SplitStep.SCAN)
        private set
    var scanning by mutableStateOf(false)
        private set
    var scanMessage by mutableStateOf<String?>(null)
        private set

    // Step 2: checking.
    var editItems by mutableStateOf<List<EditableItem>>(emptyList())
        private set
    var serviceText by mutableStateOf("")
    var taxText by mutableStateOf("")
    var roundingText by mutableStateOf("")
    var discountText by mutableStateOf("")
    var receiptTotalSen by mutableStateOf<Long?>(null)
        private set

    // Step 3: board.
    var people by mutableStateOf(listOf(Person(ME_ID, "Me", 0)))
        private set
    var items by mutableStateOf<List<BoardItem>>(emptyList())
        private set
    var selectedItemId by mutableStateOf<Long?>(null)
        private set

    /** Bumped on every successful drop, used to trigger the little "pop" animation on the receiver. */
    var lastDrop by mutableStateOf<Pair<Long, Int>?>(null)
        private set
    private var dropCount = 0

    // Step 4: paying.
    var paid by mutableStateOf<Set<Long>>(emptySet())
        private set
    var showingPayFor by mutableStateOf<Long?>(null)
        private set
    var selectedQrId by mutableLongStateOf(prefs.lastQrId)
        private set

    val savedQrs: StateFlow<List<PaymentQrEntity>> = qrs.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var nextId = 1L
    private fun newId() = nextId++

    // ---------- Step 1: scan ----------

    fun onPhoto(uri: Uri) {
        if (scanning) return
        scanning = true
        scanMessage = null
        viewModelScope.launch {
            val result = runCatching { scanner.scan(uri) }
            scanning = false
            val receipt = result.getOrNull()
            when {
                receipt == null -> scanMessage = "Could not read that photo. Try again in better light."
                receipt.items.isEmpty() -> scanMessage = "No prices found. Try a flatter, brighter photo, or type the items."
                else -> loadReceipt(receipt)
            }
        }
    }

    fun startManual() {
        loadReceipt(ParsedReceipt(emptyList()))
        addItem()
    }

    private fun loadReceipt(r: ParsedReceipt) {
        editItems = r.items.map { item ->
            val name = if (item.qty > 1) "${item.name} x${item.qty}" else item.name
            EditableItem(newId(), name, Money.toInput(item.priceSen))
        }
        serviceText = r.serviceSen.takeIf { it > 0 }?.let(Money::toInput) ?: ""
        taxText = r.taxSen.takeIf { it > 0 }?.let(Money::toInput) ?: ""
        roundingText = r.roundingSen.takeIf { it != 0L }?.let { (if (it < 0) "-" else "") + Money.toInput(kotlin.math.abs(it)) } ?: ""
        discountText = r.discountSen.takeIf { it > 0 }?.let(Money::toInput) ?: ""
        receiptTotalSen = r.totalSen
        step = SplitStep.ITEMS
    }

    // ---------- Step 2: check items ----------

    fun updateItem(id: Long, name: String? = null, priceText: String? = null) {
        editItems = editItems.map {
            if (it.id != id) it else it.copy(name = name ?: it.name, priceText = priceText ?: it.priceText)
        }
    }

    fun addItem() {
        editItems = editItems + EditableItem(newId(), "", "")
    }

    fun removeItem(id: Long) {
        editItems = editItems.filterNot { it.id == id }
    }

    val itemsSubtotalSen: Long get() = editItems.sumOf { it.priceSen ?: 0L }

    /** Service + tax + rounding - discount. Rounding may be typed as negative. */
    val extrasSen: Long
        get() {
            val rounding = roundingText.trim().let { t ->
                if (t.startsWith("-")) -(Money.parse(t.drop(1)) ?: 0L) else Money.parse(t) ?: 0L
            }
            return (Money.parse(serviceText) ?: 0L) + (Money.parse(taxText) ?: 0L) + rounding - (Money.parse(discountText) ?: 0L)
        }

    val billTotalSen: Long get() = itemsSubtotalSen + extrasSen

    val canContinueToBoard: Boolean get() = editItems.isNotEmpty() && editItems.all { it.name.isNotBlank() && it.priceSen != null }

    fun continueToBoard() {
        if (!canContinueToBoard) return
        // Keep existing assignments when coming back from the board to fix a typo.
        val previous = items.associateBy { it.id }
        items = editItems.map { e -> BoardItem(e.id, e.name.trim(), e.priceSen!!, previous[e.id]?.owners ?: emptyList()) }
        step = SplitStep.BOARD
    }

    // ---------- Step 3: board ----------

    fun addPerson(name: String) {
        val clean = name.trim().ifEmpty { return }
        people = people + Person(newId(), clean, people.size % PERSON_COLORS)
    }

    fun renamePerson(id: Long, name: String) {
        val clean = name.trim().ifEmpty { return }
        people = people.map { if (it.id == id) it.copy(name = clean) else it }
    }

    /** Removing someone hands their items back to the table. */
    fun removePerson(id: Long) {
        if (id == ME_ID) return
        people = people.filterNot { it.id == id }
        items = items.map { it.copy(owners = it.owners - id) }
    }

    fun selectItem(id: Long?) {
        selectedItemId = if (selectedItemId == id) null else id
    }

    /** Drag-and-drop result. Dropping on a person adds them; dropping on Everyone shares it with all. */
    fun dropOn(itemId: Long, targetId: Long) {
        items = items.map { item ->
            if (item.id != itemId) {
                item
            } else if (targetId == EVERYONE_ID) {
                item.copy(owners = people.map { it.id })
            } else if (targetId in item.owners) {
                item
            } else {
                item.copy(owners = item.owners + targetId)
            }
        }
        lastDrop = targetId to ++dropCount
    }

    /** Tap mode: with a block selected, tapping a person adds or removes them. */
    fun toggleOwner(targetId: Long) {
        val id = selectedItemId ?: return
        if (targetId == EVERYONE_ID) {
            dropOn(id, EVERYONE_ID)
            return
        }
        items = items.map { item ->
            if (item.id != id) item
            else item.copy(owners = if (targetId in item.owners) item.owners - targetId else item.owners + targetId)
        }
        lastDrop = targetId to ++dropCount
    }

    fun clearOwners(itemId: Long) {
        items = items.map { if (it.id == itemId) it.copy(owners = emptyList()) else it }
    }

    /** Anything nobody claimed is shared by everyone, a common way to settle the last few items. */
    fun shareLeftovers() {
        val all = people.map { it.id }
        items = items.map { if (it.assigned) it else it.copy(owners = all) }
    }

    val unassignedCount: Int get() = items.count { !it.assigned }

    /** Live totals per person, recomputed from the board. Cheap: a bill has tens of items at most. */
    fun shares(): Map<Long, Share> =
        SplitMath.compute(people.map { it.id }, items.map { SplitItem(it.id, it.priceSen, it.owners) }, extrasSen)

    fun toSummary() {
        if (unassignedCount == 0) step = SplitStep.SUMMARY
    }

    // ---------- Step 4: summary and pay ----------

    fun showPay(personId: Long?) {
        showingPayFor = personId
    }

    /** Moves the QR screen to the next person who still owes, so the phone can be passed round the table. */
    fun nextToPay() {
        val order = people.filter { it.id != ME_ID }
        if (order.isEmpty()) return
        val current = order.indexOfFirst { it.id == showingPayFor }
        showingPayFor = order[(current + 1) % order.size].id
    }

    fun togglePaid(personId: Long) {
        paid = if (personId in paid) paid - personId else paid + personId
    }

    fun selectQr(id: Long) {
        selectedQrId = id
        prefs.lastQrId = id
    }

    fun addQr(uri: Uri, label: String) {
        viewModelScope.launch { qrs.add(uri, label)?.let { selectQr(it) } }
    }

    fun deleteQr(qr: PaymentQrEntity) {
        viewModelScope.launch { qrs.delete(qr) }
    }

    fun back() {
        when {
            showingPayFor != null -> showingPayFor = null
            step == SplitStep.SUMMARY -> step = SplitStep.BOARD
            step == SplitStep.BOARD -> step = SplitStep.ITEMS
            step == SplitStep.ITEMS -> step = SplitStep.SCAN
        }
    }

    fun startOver() {
        step = SplitStep.SCAN
        editItems = emptyList()
        items = emptyList()
        people = listOf(Person(ME_ID, "Me", 0))
        paid = emptySet()
        showingPayFor = null
        selectedItemId = null
        receiptTotalSen = null
        serviceText = ""; taxText = ""; roundingText = ""; discountText = ""
    }

    companion object {
        const val ME_ID = 0L
        const val EVERYONE_ID = -1L
        const val PERSON_COLORS = 7
    }
}
