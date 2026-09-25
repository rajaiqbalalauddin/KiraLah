package com.buyless.app.ui.split

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.toArgb
import com.buyless.app.data.db.FriendEntity
import com.buyless.app.data.db.PaymentQrEntity
import com.buyless.app.data.repo.FriendsRepository
import com.buyless.app.share.FriendSearch
import com.buyless.app.share.PayCardFiles
import com.buyless.app.share.PayCardRenderer
import com.buyless.app.share.PayMessage
import com.buyless.app.share.PhoneNumbers
import com.buyless.app.share.SendResult
import com.buyless.app.share.ShareRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import com.buyless.app.data.repo.QrRepository
import com.buyless.app.data.db.SplitBillEntity
import com.buyless.app.data.repo.SplitHistoryRepository
import com.buyless.app.data.repo.SplitSnapshot
import com.buyless.app.split.GeminiException
import com.buyless.app.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.buyless.app.split.ParsedReceipt
import com.buyless.app.split.ReceiptScanner
import com.buyless.app.split.ScanStage
import com.buyless.app.split.Share
import com.buyless.app.split.SplitItem
import com.buyless.app.split.SplitMath
import com.buyless.app.util.AppPrefs
import com.buyless.app.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class SplitStep { SCAN, ITEMS, BOARD, SUMMARY }

/**
 * Someone at the table. colorIndex picks their avatar colour, so each person is easy to spot.
 * friendId links them to their saved friend (null for "Me"); phone is digits with country code.
 */
@Immutable
data class Person(val id: Long, val name: String, val colorIndex: Int, val friendId: Long? = null, val phone: String? = null)

/** A pay card ready to hand to WhatsApp. personId is null for the group summary. */
data class ShareEvent(val personId: Long?, val request: ShareRequest)

/** An item while it is being checked. Price is kept as text so half-typed values are allowed. */
@Immutable
data class EditableItem(val id: Long, val name: String, val priceText: String, val note: String? = null) {
    val priceSen: Long? get() = Money.parse(priceText)
}

/** One saved split in the history list. */
@Immutable
data class HistoryCard(
    val id: Long,
    val title: String,
    val dateText: String,
    val totalText: String,
    val paidText: String,
    val allPaid: Boolean,
    val receiptPath: String?,
)

/** An item on the split board. */
@Immutable
data class BoardItem(val id: Long, val name: String, val priceSen: Long, val owners: List<Long>, val note: String? = null) {
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
    private val history: SplitHistoryRepository,
    private val friends: FriendsRepository,
    private val payCards: PayCardFiles,
) : ViewModel() {

    // ---------- History ----------

    /** Past splits for the list on the Split tab, newest first. */
    val historyCards: StateFlow<List<HistoryCard>> = history.observeAll()
        .map { rows -> rows.map { it.toCard() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Private copy of the receipt photo, kept with the saved split. */
    var receiptPath by mutableStateOf<String?>(null)
        private set
    var showingReceipt by mutableStateOf(false)
        private set

    private var billId: Long? = null
    private var billCreatedAt = 0L
    private val saveLock = Mutex()

    var step by mutableStateOf(SplitStep.SCAN)
        private set
    var scanning by mutableStateOf(false)
        private set
    var scanMessage by mutableStateOf<String?>(null)
        private set

    /** Drives the loading animation: which step the reader is on and which photo it is reading. */
    var scanStage by mutableStateOf(ScanStage.PREPARING)
        private set
    var scanPhoto by mutableStateOf<Uri?>(null)
        private set

    /** Shown on the check-items screen, e.g. when the phone had to read the receipt offline. */
    var scanNotice by mutableStateOf<String?>(null)
        private set
    var merchant by mutableStateOf<String?>(null)
        private set

    // Step 2: checking.
    var editItems by mutableStateOf<List<EditableItem>>(emptyList())
        private set
    var serviceText by mutableStateOf("")
    var taxText by mutableStateOf("")
    var roundingText by mutableStateOf("")
    var discountText by mutableStateOf("")

    /** Service / SST already inside the item prices: shown for reference, never added again. */
    var serviceIncluded by mutableStateOf(false)
    var taxIncluded by mutableStateOf(false)
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

    // ---------- Friends and WhatsApp ----------

    /** Saved friends for the people drawer, favourites first. */
    val savedFriends: StateFlow<List<FriendEntity>> = friends.observeRanked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** People whose WhatsApp chat was opened with their pay card. */
    var sent by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Person (or GROUP_ID) whose pay card is being drawn, to show a small spinner on that button. */
    var preparingShare by mutableStateOf<Long?>(null)
        private set

    /** "Send to everyone": people still to go, and how many there were at the start. */
    var sendQueue by mutableStateOf<List<Long>>(emptyList())
        private set
    var sendQueueTotal by mutableStateOf(0)
        private set

    /** True after WhatsApp opened during "Send to everyone", until the user comes back to Buyless. */
    var awaitingReturn by mutableStateOf(false)
        private set

    private val shareChannel = Channel<ShareEvent>(Channel.BUFFERED)
    val shareEvents: Flow<ShareEvent> = shareChannel.receiveAsFlow()

    /** Friend usage is counted once per bill, the first time its totals are shown. */
    private var usageRecorded = false

    private var nextId = 1L
    private fun newId() = nextId++

    // ---------- Step 1: scan ----------

    fun onPhoto(uri: Uri) {
        if (scanning) return
        beginNewBill()
        scanning = true
        scanMessage = null
        scanPhoto = uri
        scanStage = ScanStage.PREPARING
        scanJob = viewModelScope.launch {
            val result = runCatching { scanner.scan(uri) { scanStage = it } }
            // Keep our own copy of the photo: the camera file is temporary and gallery links can break.
            if (result.isSuccess) receiptPath = history.keepReceipt(uri)
            val error = result.exceptionOrNull()
            if (error is CancellationException) return@launch
            scanning = false
            val scan = result.getOrNull()
            when {
                error is GeminiException && error.kind == GeminiException.Kind.NOT_A_RECEIPT ->
                    scanMessage = "That does not look like a receipt. Try another photo."
                scan == null -> scanMessage = "Could not read that photo. Try again in better light."
                scan.receipt.items.isEmpty() -> scanMessage = "No prices found. Try a flatter, brighter photo, or type the items."
                else -> {
                    scanNotice = scan.notice
                    loadReceipt(scan.receipt)
                }
            }
        }
    }

    /** Lets the user back out of a slow scan. The network call is cancelled with the job. */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        scanning = false
    }

    private var scanJob: Job? = null

    /**
     * A fresh scan or manual entry is always a new bill, so it never overwrites one from history.
     * People are kept on purpose: the same friends often eat together again.
     */
    private fun beginNewBill() {
        billId = null
        receiptPath = null
        paid = emptySet()
        sent = emptySet()
        usageRecorded = false
        stopSendAll()
        showingPayFor = null
        showingReceipt = false
    }

    fun startManual() {
        beginNewBill()
        scanPhoto = null
        scanNotice = null
        loadReceipt(ParsedReceipt(emptyList()))
        addItem()
    }

    private fun loadReceipt(r: ParsedReceipt) {
        editItems = r.items.map { item ->
            val name = if (item.qty > 1) "${item.name} x${item.qty}" else item.name
            EditableItem(newId(), name, Money.toInput(item.priceSen), item.note)
        }
        serviceText = r.serviceSen.takeIf { it > 0 }?.let(Money::toInput) ?: ""
        taxText = r.taxSen.takeIf { it > 0 }?.let(Money::toInput) ?: ""
        roundingText = r.roundingSen.takeIf { it != 0L }?.let { (if (it < 0) "-" else "") + Money.toInput(kotlin.math.abs(it)) } ?: ""
        discountText = r.discountSen.takeIf { it > 0 }?.let(Money::toInput) ?: ""
        serviceIncluded = r.serviceIncluded
        taxIncluded = r.taxIncluded
        receiptTotalSen = r.totalSen
        merchant = r.merchant
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
            val service = if (serviceIncluded) 0L else Money.parse(serviceText) ?: 0L
            val tax = if (taxIncluded) 0L else Money.parse(taxText) ?: 0L
            return service + tax + rounding - (Money.parse(discountText) ?: 0L)
        }

    val billTotalSen: Long get() = itemsSubtotalSen + extrasSen

    val canContinueToBoard: Boolean get() = editItems.isNotEmpty() && editItems.all { it.name.isNotBlank() && it.priceSen != null }

    fun continueToBoard() {
        if (!canContinueToBoard) return
        // Keep existing assignments when coming back from the board to fix a typo.
        val previous = items.associateBy { it.id }
        items = editItems.map { e -> BoardItem(e.id, e.name.trim(), e.priceSen!!, previous[e.id]?.owners ?: emptyList(), e.note) }
        step = SplitStep.BOARD
    }

    // ---------- Step 3: board ----------

    /**
     * Adds someone by name. They are saved as a friend straight away (or linked to the friend with that
     * name), so next time they are one tap away in the drawer.
     */
    fun addPerson(name: String, phone: String? = null) {
        val clean = name.trim().ifEmpty { return }
        val key = FriendSearch.key(clean)
        if (people.any { FriendSearch.key(it.name) == key }) return
        val id = newId()
        val color = people.size % PERSON_COLORS
        people = people + Person(id, clean, color, phone = PhoneNumbers.normalize(phone))
        viewModelScope.launch {
            val friend = friends.ensure(clean, phone, color)
            people = people.map {
                if (it.id != id) it else it.copy(friendId = friend.id, colorIndex = friend.colorIndex, phone = it.phone ?: friend.phone)
            }
        }
    }

    /** Adds friends picked in the drawer. Anyone already on this bill is skipped. */
    fun addFriends(picked: List<FriendEntity>) {
        val onBoard = people.mapNotNull { it.friendId }.toSet()
        val names = people.map { FriendSearch.key(it.name) }.toSet()
        val fresh = picked.filter { it.id !in onBoard && it.nameKey !in names }
        people = people + fresh.map { Person(newId(), it.name, it.colorIndex, it.id, it.phone) }
    }

    /** Adds a phone contact: saved as a friend (with their number) and put on the bill. */
    fun addContact(name: String, phone: String?) {
        val clean = name.trim().ifEmpty { return }
        viewModelScope.launch {
            val friend = friends.ensure(clean, phone, people.size % PERSON_COLORS)
            val normalized = PhoneNumbers.normalize(phone)
            if (normalized != null && friend.phone != normalized) friends.setPhone(friend.id, normalized)
            addFriends(listOf(friend.copy(phone = normalized ?: friend.phone)))
        }
    }

    /** Edits a person on the bill and their saved friend, so the fix sticks for next time. */
    fun editPerson(id: Long, name: String, phoneText: String?) {
        val clean = name.trim().ifEmpty { return }
        val person = people.firstOrNull { it.id == id } ?: return
        val phone = PhoneNumbers.normalize(phoneText)
        people = people.map { if (it.id == id) it.copy(name = clean, phone = phone) else it }
        if (id == ME_ID) return
        viewModelScope.launch {
            val fid = person.friendId
            if (fid != null && friends.edit(fid, clean, phone)) return@launch
            // No friend yet, or the new name belongs to another saved friend: link to that one.
            val friend = friends.ensure(clean, phone, person.colorIndex)
            if (phone != null && friend.phone != phone) friends.setPhone(friend.id, phone)
            people = people.map { if (it.id == id) it.copy(friendId = friend.id) else it }
        }
    }

    /** Saves a number for someone on the bill (and their friend), used by "Add number" before sending. */
    fun setPhone(personId: Long, phoneText: String) {
        val phone = PhoneNumbers.normalize(phoneText) ?: return
        val person = people.firstOrNull { it.id == personId } ?: return
        people = people.map { if (it.id == personId) it.copy(phone = phone) else it }
        viewModelScope.launch {
            val fid = person.friendId ?: friends.ensure(person.name, phone, person.colorIndex).id
            friends.setPhone(fid, phone)
            people = people.map { if (it.id == personId) it.copy(friendId = fid) else it }
        }
        if (step == SplitStep.SUMMARY) persist()
    }

    fun toggleFavourite(friend: FriendEntity) {
        viewModelScope.launch { friends.setFavourite(friend.id, !friend.favourite) }
    }

    /** Edits a saved friend from the drawer. onResult(false) means the name is taken by someone else. */
    fun editFriend(friend: FriendEntity, name: String, phoneText: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = friends.edit(friend.id, name, phoneText)
            if (ok) {
                val phone = PhoneNumbers.normalize(phoneText)
                people = people.map { if (it.friendId == friend.id) it.copy(name = name.trim(), phone = phone) else it }
            }
            onResult(ok)
        }
    }

    /** Forgets a saved friend. Anyone already on this bill stays on it. */
    fun deleteFriend(friend: FriendEntity) {
        viewModelScope.launch { friends.delete(friend.id) }
        people = people.map { if (it.friendId == friend.id) it.copy(friendId = null) else it }
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
        if (unassignedCount != 0) return
        step = SplitStep.SUMMARY
        if (!usageRecorded) {
            usageRecorded = true
            val ids = people.mapNotNull { it.friendId }
            viewModelScope.launch { friends.recordSplit(ids) }
        }
        persist()
    }

    /**
     * Saves the split to history. Called when totals are first shown and whenever someone is marked
     * paid, so the history always matches what was last on screen. A lock stops two quick saves from
     * creating two rows for the same bill.
     */
    private fun persist() {
        val snapshot = SplitSnapshot(
            merchant = merchant,
            people = people.map { SplitSnapshot.SnapPerson(it.id, it.name, it.colorIndex, it.friendId, it.phone) },
            items = items.map { SplitSnapshot.SnapItem(it.id, it.name, it.priceSen, it.owners, it.note) },
            serviceText = serviceText,
            taxText = taxText,
            roundingText = roundingText,
            discountText = discountText,
            serviceIncluded = serviceIncluded,
            taxIncluded = taxIncluded,
            receiptTotalSen = receiptTotalSen,
            paid = paid,
            sent = sent,
        )
        val friends = people.filter { it.id != ME_ID }
        val total = billTotalSen
        val paidCount = friends.count { it.id in paid }
        viewModelScope.launch {
            saveLock.withLock {
                val now = System.currentTimeMillis()
                if (billId == null) billCreatedAt = now
                billId = history.save(
                    SplitBillEntity(
                        id = billId ?: 0,
                        title = snapshot.merchant ?: "Bill split",
                        createdAt = billCreatedAt,
                        updatedAt = now,
                        totalSen = total,
                        peopleCount = friends.size,
                        paidCount = paidCount,
                        receiptPath = receiptPath,
                        stateJson = SplitHistoryRepository.encode(snapshot),
                    ),
                )
            }
        }
    }

    /** Reopens a saved split on its Totals screen, with QR cards and paid marks as they were. */
    fun openBill(id: Long) {
        viewModelScope.launch {
            val entity = history.get(id) ?: return@launch
            val s = runCatching { SplitHistoryRepository.decode(entity.stateJson) }.getOrNull() ?: return@launch
            billId = entity.id
            billCreatedAt = entity.createdAt
            receiptPath = entity.receiptPath
            scanPhoto = null
            scanNotice = null
            merchant = s.merchant
            people = s.people.map { Person(it.id, it.name, it.colorIndex, it.friendId, it.phone) }.ifEmpty { listOf(Person(ME_ID, "Me", 0)) }
            items = s.items.map { BoardItem(it.id, it.name, it.priceSen, it.owners, it.note) }
            editItems = s.items.map { EditableItem(it.id, it.name, Money.toInput(it.priceSen), it.note) }
            serviceText = s.serviceText
            taxText = s.taxText
            roundingText = s.roundingText
            discountText = s.discountText
            serviceIncluded = s.serviceIncluded
            taxIncluded = s.taxIncluded
            receiptTotalSen = s.receiptTotalSen
            paid = s.paid
            sent = s.sent
            usageRecorded = true
            stopSendAll()
            showingPayFor = null
            selectedItemId = null
            // New ids must not collide with the ones in the saved bill.
            nextId = (s.people.map { it.id } + s.items.map { it.id }).maxOrNull()?.plus(1) ?: 1L
            step = SplitStep.SUMMARY
        }
    }

    /** Removes a split from history and hands it back for Undo. The photo is kept until app restart. */
    fun deleteBill(id: Long, onDeleted: (SplitBillEntity) -> Unit) {
        viewModelScope.launch {
            val entity = history.get(id) ?: return@launch
            history.delete(id)
            if (billId == id) billId = null
            onDeleted(entity)
        }
    }

    fun restoreBill(entity: SplitBillEntity) {
        viewModelScope.launch { history.restore(entity) }
    }

    fun showReceipt(show: Boolean) {
        showingReceipt = show && receiptPath != null
    }

    private fun SplitBillEntity.toCard(): HistoryCard = HistoryCard(
        id = id,
        title = title,
        dateText = "${Dates.relativeDay(Dates.toDate(createdAt))}, ${Dates.timeOf(createdAt)}",
        totalText = Money.format(totalSen),
        paidText = when {
            peopleCount == 0 -> "Just you"
            paidCount >= peopleCount -> "All paid"
            else -> "$paidCount of $peopleCount paid"
        },
        allPaid = peopleCount > 0 && paidCount >= peopleCount,
        receiptPath = receiptPath,
    )

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
        if (step == SplitStep.SUMMARY) persist()
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

    // ---------- WhatsApp ----------

    /** The QR friends pay to: the one picked on Totals, else the first saved. */
    private fun currentQr(): PaymentQrEntity? {
        val all = savedQrs.value
        return all.firstOrNull { it.id == selectedQrId } ?: all.firstOrNull()
    }

    private fun billDateText(): String = Dates.shortDate(if (billCreatedAt > 0) billCreatedAt else System.currentTimeMillis())

    /** Draws this person's pay card and writes their message, then hands both to the screen to open WhatsApp. */
    fun sendTo(personId: Long) {
        val person = people.firstOrNull { it.id == personId && it.id != ME_ID } ?: return
        if (preparingShare != null) return
        preparingShare = personId
        viewModelScope.launch {
            try {
                val share = shares()[person.id] ?: return@launch
                val ids = people.map { it.id }
                val lines = items.filter { person.id in it.owners }.map {
                    PayMessage.Line(it.name, SplitMath.shareOf(it.priceSen, it.owners, ids, person.id), it.owners.size)
                }
                val qr = currentQr()
                val date = billDateText()
                val text = PayMessage.forPerson(person.name, merchant, date, share.totalSen, lines, share.extrasSen, qr?.label)
                val (bg, fg) = PersonColors[person.colorIndex % PersonColors.size]
                val qrBitmap = payCards.loadQr(qr?.filePath)
                val bitmap = withContext(Dispatchers.Default) {
                    PayCardRenderer.person(
                        PayCardRenderer.PersonCard(
                            name = person.name,
                            amountText = Money.format(share.totalSen),
                            placeText = listOfNotNull(merchant?.let { "for $it" }, date).joinToString(" · "),
                            qr = qrBitmap,
                            qrLabel = qr?.label,
                            bg = bg.toArgb(),
                            fg = fg.toArgb(),
                        ),
                    ).also { qrBitmap?.recycle() }
                }
                val uri = payCards.save("pay_${person.id}", bitmap)
                shareChannel.send(ShareEvent(person.id, ShareRequest(uri, text, person.phone)))
            } finally {
                preparingShare = null
            }
        }
    }

    /** One summary picture for the group chat. WhatsApp shows its chat picker so the group can be chosen. */
    fun sendToGroup() {
        if (preparingShare != null) return
        val others = people.filter { it.id != ME_ID }
        if (others.isEmpty()) return
        preparingShare = GROUP_ID
        viewModelScope.launch {
            try {
                val shares = shares()
                val qr = currentQr()
                val date = billDateText()
                val rows = others.map { PayMessage.GroupRow(it.name, shares[it.id]?.totalSen ?: 0L, it.id in paid) }
                val text = PayMessage.forGroup(merchant, date, rows, qr?.label)
                val qrBitmap = payCards.loadQr(qr?.filePath)
                val bitmap = withContext(Dispatchers.Default) {
                    PayCardRenderer.group(
                        title = merchant ?: "Bill split",
                        placeText = "$date · ${Money.format(billTotalSen)} total",
                        rows = others.map { p ->
                            val (bg, fg) = PersonColors[p.colorIndex % PersonColors.size]
                            PayCardRenderer.GroupRow(p.name, Money.format(shares[p.id]?.totalSen ?: 0L), p.id in paid, bg.toArgb(), fg.toArgb())
                        },
                        qr = qrBitmap,
                        qrLabel = qr?.label,
                    ).also { qrBitmap?.recycle() }
                }
                val uri = payCards.save("pay_group", bitmap)
                shareChannel.send(ShareEvent(null, ShareRequest(uri, text, phone = null)))
            } finally {
                preparingShare = null
            }
        }
    }

    /** Called by the screen after trying to open WhatsApp. Sent tags are saved with the bill. */
    fun onShared(personId: Long?, result: SendResult) {
        if (result == SendResult.FAILED) {
            stopSendAll()
            return
        }
        sent = if (personId == null) sent + people.filter { it.id != ME_ID }.map { it.id } else sent + personId
        // Mid "Send to everyone": wait for the user to come back, then open the next chat.
        if (sendQueue.isNotEmpty()) awaitingReturn = true else sendQueueTotal = 0
        if (step == SplitStep.SUMMARY) persist()
    }

    /** Friends "Send to everyone" will go through: has a number, has not paid yet. */
    val sendableIds: List<Long>
        get() = people.filter { it.id != ME_ID && it.phone != null && it.id !in paid }.map { it.id }

    val missingNumberCount: Int get() = people.count { it.id != ME_ID && it.phone == null && it.id !in paid }

    /** Opens WhatsApp for each friend in turn. The next one opens when the user comes back to Buyless. */
    fun startSendAll() {
        val queue = sendableIds
        if (queue.isEmpty()) return
        sendQueueTotal = queue.size
        sendQueue = queue.drop(1)
        awaitingReturn = false
        sendTo(queue.first())
    }

    /** Called when the app comes back to the front after WhatsApp. */
    fun onReturnedFromChat() {
        if (!awaitingReturn) return
        awaitingReturn = false
        val next = sendQueue.firstOrNull() ?: return stopSendAll()
        sendQueue = sendQueue.drop(1)
        sendTo(next)
    }

    fun stopSendAll() {
        sendQueue = emptyList()
        sendQueueTotal = 0
        awaitingReturn = false
    }

    fun back() {
        when {
            showingReceipt -> showingReceipt = false
            showingPayFor != null -> showingPayFor = null
            step == SplitStep.SUMMARY -> step = SplitStep.BOARD
            step == SplitStep.BOARD -> step = SplitStep.ITEMS
            step == SplitStep.ITEMS -> step = SplitStep.SCAN
        }
    }

    fun startOver() {
        billId = null
        receiptPath = null
        scanPhoto = null
        scanNotice = null
        merchant = null
        showingReceipt = false
        step = SplitStep.SCAN
        editItems = emptyList()
        items = emptyList()
        people = listOf(Person(ME_ID, "Me", 0))
        paid = emptySet()
        sent = emptySet()
        usageRecorded = false
        stopSendAll()
        showingPayFor = null
        selectedItemId = null
        receiptTotalSen = null
        serviceText = ""; taxText = ""; roundingText = ""; discountText = ""
        serviceIncluded = false; taxIncluded = false
    }

    companion object {
        const val ME_ID = 0L
        const val EVERYONE_ID = -1L

        /** Stands in for "the group chat" in preparingShare. */
        const val GROUP_ID = -2L
        const val PERSON_COLORS = 7
    }
}
