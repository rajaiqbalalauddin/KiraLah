package com.buyless.app.ui.split

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.data.db.FriendEntity
import com.buyless.app.share.FriendSearch
import com.buyless.app.share.PhoneNumbers
import com.buyless.app.ui.theme.BColors

/**
 * The people drawer. Favourites sit on top as one-tap chips, everyone else you have split with is
 * listed by how often you eat together. Tick several and add them in one go, type a new name, or
 * pick someone from your phone contacts.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PeopleSheet(vm: SplitViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val friends by vm.savedFriends.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var editing by remember { mutableStateOf<FriendEntity?>(null) }

    val onBoardIds = vm.people.mapNotNull { it.friendId }.toSet()
    val onBoardNames = vm.people.map { FriendSearch.key(it.name) }.toSet()
    fun isOnBoard(f: FriendEntity) = f.id in onBoardIds || f.nameKey in onBoardNames

    val matching = friends.filter { FriendSearch.matches(it.name, it.phone, query) }
    val favourites = if (query.isBlank()) matching.filter { it.favourite } else emptyList()
    val others = if (query.isBlank()) matching.filterNot { it.favourite } else matching
    val typedKey = FriendSearch.key(query)
    val canAddTyped = typedKey.isNotEmpty() && friends.none { it.nameKey == typedKey } && typedKey !in onBoardNames

    val contactPicker = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        val contact = uri?.let { readContact(context, it) } ?: return@rememberLauncherForActivityResult
        vm.addContact(contact.first, contact.second)
        onDismiss()
    }

    fun toggle(f: FriendEntity) {
        if (isOnBoard(f)) return
        picked = if (f.id in picked) picked - f.id else picked + f.id
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BColors.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Add people", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { contactPicker.launch(Unit) }) {
                    Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Contacts")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(20) },
                placeholder = { Text("Search, or type a new name") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = splitFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (canAddTyped) {
                    item(key = "new") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(BColors.VioletSoft)
                                .clickable {
                                    vm.addPerson(query)
                                    query = ""
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = BColors.Violet)
                            Text("Add \"${query.trim()}\" to this bill", style = MaterialTheme.typography.titleSmall, color = BColors.Violet)
                        }
                    }
                }

                if (favourites.isNotEmpty()) {
                    item(key = "fav-title") { SectionTitle("Favourites") }
                    item(key = "fav-chips") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            favourites.forEach { f ->
                                FavouriteChip(f, selected = f.id in picked, onBoard = isOnBoard(f), onClick = { toggle(f) }, onLongClick = { editing = f })
                            }
                        }
                    }
                }

                if (others.isNotEmpty()) {
                    item(key = "all-title") { SectionTitle(if (query.isBlank()) "People you split with" else "Matches") }
                    items(others, key = { it.id }) { f ->
                        FriendRow(
                            friend = f,
                            selected = f.id in picked,
                            onBoard = isOnBoard(f),
                            onClick = { toggle(f) },
                            onLongClick = { editing = f },
                            onStar = { vm.toggleFavourite(f) },
                        )
                    }
                }

                if (friends.isEmpty() && !canAddTyped) {
                    item(key = "empty") {
                        Text(
                            "Type a name above, or pick from your contacts. Everyone you add is saved here, and a star pins your regulars to the top.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BColors.Muted,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                if (friends.isNotEmpty() && query.isBlank()) {
                    item(key = "hint") {
                        Text(
                            "Tap the star to make someone a favourite. Hold a name to add their number or edit it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BColors.Faint,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    vm.addFriends(friends.filter { it.id in picked })
                    onDismiss()
                },
                enabled = picked.isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding().height(56.dp),
            ) {
                Text(
                    when (picked.size) {
                        0 -> "Pick people to add"
                        1 -> "Add 1 person"
                        else -> "Add ${picked.size} people"
                    },
                )
            }
        }
    }

    editing?.let { f -> key(f.id) {
        var error by remember { mutableStateOf<String?>(null) }
        PersonFormDialog(
            title = "Edit ${f.name}",
            initialName = f.name,
            initialPhone = PhoneNumbers.pretty(f.phone) ?: "",
            confirm = "Save",
            error = error,
            onDismiss = { editing = null },
            onConfirm = { name, phone ->
                vm.editFriend(f, name, phone) { ok -> if (ok) editing = null else error = "Someone called $name is already saved." }
            },
            removeLabel = "Forget",
            onRemove = {
                vm.deleteFriend(f)
                picked = picked - f.id
                editing = null
            },
        )
    } }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = BColors.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

private fun FriendEntity.asPerson() = Person(id = id, name = name, colorIndex = colorIndex, friendId = id, phone = phone)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteChip(friend: FriendEntity, selected: Boolean, onBoard: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .alpha(if (onBoard) 0.45f else 1f)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) BColors.Violet else BColors.Lavender)
            .border(2.dp, if (selected) BColors.Violet else BColors.Border, RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(friend.asPerson(), 36.dp)
            if (selected || onBoard) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(BColors.Ink.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = BColors.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(friend.name, style = MaterialTheme.typography.titleSmall, color = if (selected) BColors.White else BColors.Ink, maxLines = 1)
        Icon(Icons.Rounded.Star, contentDescription = null, tint = BColors.Yellow, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendRow(
    friend: FriendEntity,
    selected: Boolean,
    onBoard: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStar: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) BColors.VioletSoft else BColors.White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(friend.asPerson(), 44.dp, modifier = Modifier.alpha(if (onBoard) 0.45f else 1f))
        Column(Modifier.weight(1f)) {
            Text(friend.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = buildList {
                add(if (onBoard) "On this bill" else PhoneNumbers.pretty(friend.phone) ?: "No number yet")
                if (friend.timesSplit > 0) add("${friend.timesSplit} split${if (friend.timesSplit == 1) "" else "s"}")
            }.joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodySmall, color = BColors.Muted, maxLines = 1)
        }
        IconButton(onClick = onStar) {
            Icon(
                if (friend.favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (friend.favourite) "Remove ${friend.name} from favourites" else "Make ${friend.name} a favourite",
                tint = if (friend.favourite) BColors.Yellow else BColors.Faint,
            )
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected || onBoard) BColors.Violet else BColors.White)
                .border(2.dp, if (selected || onBoard) BColors.Violet else BColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected || onBoard) Icon(Icons.Rounded.Check, contentDescription = null, tint = BColors.White, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * Name + optional phone form, used for editing a friend, a person on the bill, or adding a number
 * before sending. Phone is checked as you type so a wrong number never reaches WhatsApp.
 */
@Composable
internal fun PersonFormDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String?) -> Unit,
    showName: Boolean = true,
    showPhone: Boolean = true,
    error: String? = null,
    message: String? = null,
    removeLabel: String = "Remove",
    removeIsDanger: Boolean = true,
    onRemove: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    val phoneOk = phone.isBlank() || PhoneNumbers.normalize(phone) != null
    val phoneRequired = !showName // "Add number" mode: the number is the whole point
    val canSave = name.isNotBlank() && phoneOk && (!phoneRequired || phone.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = BColors.Muted)
                if (showName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                if (showPhone) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { v -> phone = v.filter { it.isDigit() || it in "+ -" }.take(20) },
                        label = { Text(if (phoneRequired) "WhatsApp number" else "WhatsApp number (optional)") },
                        placeholder = { Text("012-345 6789") },
                        singleLine = true,
                        isError = !phoneOk,
                        supportingText = { if (!phoneOk) Text("That does not look like a phone number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = BColors.Danger)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), phone.trim().ifEmpty { null }) }, enabled = canSave) { Text(confirm) }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text(removeLabel, color = if (removeIsDanger) BColors.Danger else BColors.Muted) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Opens the phone's own contact picker on phone numbers. The picked row is readable without the
 * contacts permission, so Buyless never sees the rest of the address book.
 */
private class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

/** Name and number of the one picked contact, or null if it cannot be read. */
private fun readContact(context: Context, uri: Uri): Pair<String, String?>? = try {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null,
    )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "").take(20) to c.getString(1) else null }
        ?.takeIf { it.first.isNotBlank() }
} catch (e: SecurityException) {
    null
}
