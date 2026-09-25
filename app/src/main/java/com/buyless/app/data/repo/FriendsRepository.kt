package com.buyless.app.data.repo

import com.buyless.app.data.db.FriendDao
import com.buyless.app.data.db.FriendEntity
import com.buyless.app.data.db.SplitBillDao
import com.buyless.app.share.FriendSearch
import com.buyless.app.share.PhoneNumbers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Saved friends for bill splitting. Everyone who joins a bill is saved here automatically, so the
 * people drawer builds itself; the user only has to star favourites and add phone numbers.
 */
class FriendsRepository(private val dao: FriendDao, private val bills: SplitBillDao) {

    /** Serialises "find or create" so two quick adds of the same name make one friend, not two. */
    private val lock = Mutex()

    fun observeRanked(): Flow<List<FriendEntity>> = dao.observeRanked().distinctUntilChanged()

    /**
     * Returns the saved friend with this name, creating them if needed. A phone number given here
     * fills an empty one but never overwrites a number already saved.
     */
    suspend fun ensure(name: String, phone: String? = null, colorIndex: Int): FriendEntity = lock.withLock {
        val clean = name.trim()
        val key = FriendSearch.key(clean)
        val normalized = PhoneNumbers.normalize(phone)
        dao.findByKey(key)?.let { existing ->
            if (existing.phone == null && normalized != null) {
                dao.setPhone(existing.id, normalized)
                return@withLock existing.copy(phone = normalized)
            }
            return@withLock existing
        }
        val fresh = FriendEntity(name = clean, nameKey = key, phone = normalized, colorIndex = colorIndex, createdAt = System.currentTimeMillis())
        val id = dao.insert(fresh)
        if (id > 0) fresh.copy(id = id) else dao.findByKey(key) ?: fresh
    }

    suspend fun get(id: Long): FriendEntity? = dao.get(id)

    /**
     * Saves a new name and phone. Returns false when the new name belongs to another saved friend,
     * so the caller can say so instead of silently merging two people.
     */
    suspend fun edit(id: Long, name: String, phone: String?): Boolean = lock.withLock {
        val current = dao.get(id) ?: return@withLock false
        val key = FriendSearch.key(name)
        val clash = dao.findByKey(key)
        if (clash != null && clash.id != id) return@withLock false
        dao.update(current.copy(name = name.trim(), nameKey = key, phone = PhoneNumbers.normalize(phone)))
        true
    }

    suspend fun setPhone(id: Long, phone: String?) = dao.setPhone(id, PhoneNumbers.normalize(phone))

    suspend fun setFavourite(id: Long, favourite: Boolean) = dao.setFavourite(id, favourite)

    suspend fun delete(id: Long) = dao.delete(id)

    /** Called once per bill when its totals are first shown, so the drawer ranks regulars first. */
    suspend fun recordSplit(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.recordSplit(ids.distinct(), System.currentTimeMillis())
    }

    /**
     * One-off: fills the friends list from splits saved before this feature existed, so the drawer
     * is useful straight after the update. Runs only while the table is empty.
     */
    suspend fun backfillFromHistory() {
        if (dao.count() > 0) return
        val seen = HashMap<String, Int>()
        for (json in bills.allStates()) {
            val snapshot = runCatching { SplitHistoryRepository.decode(json) }.getOrNull() ?: continue
            for (p in snapshot.people) {
                if (p.id == 0L) continue // "Me"
                val key = FriendSearch.key(p.name)
                if (key.isEmpty()) continue
                seen[key] = (seen[key] ?: 0) + 1
                if (seen[key] == 1) ensure(p.name, p.phone, p.colorIndex)
            }
        }
        for ((key, times) in seen) {
            val friend = dao.findByKey(key) ?: continue
            repeat(times) { dao.recordSplit(listOf(friend.id), System.currentTimeMillis()) }
        }
    }
}
