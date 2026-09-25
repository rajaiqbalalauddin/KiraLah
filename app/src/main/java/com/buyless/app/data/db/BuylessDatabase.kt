package com.buyless.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Single on-device database. Nothing is synced anywhere, which is the privacy promise in Setup.
 * WAL journaling lets the listener write while the UI reads without blocking each other.
 */
@Database(
    entities = [TransactionEntity::class, WatchedAppEntity::class, PendingEntity::class, PaymentQrEntity::class, RawNotificationEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class BuylessDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun watchedAppDao(): WatchedAppDao
    abstract fun pendingDao(): PendingDao
    abstract fun paymentQrDao(): PaymentQrDao
    abstract fun rawNotificationDao(): RawNotificationDao

    companion object {
        fun build(context: Context): BuylessDatabase =
            Room.databaseBuilder(context, BuylessDatabase::class.java, "buyless.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2 adds saved payment QRs for bill splitting. Existing transactions are untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payment_qr` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, `filePath` TEXT NOT NULL, `addedAt` INTEGER NOT NULL)",
                )
            }
        }

        /** v3 adds the notification sample log used to build new bank templates. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `raw_notifications` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourcePackage` TEXT NOT NULL, " +
                        "`sourceLabel` TEXT NOT NULL, `title` TEXT, `body` TEXT NOT NULL, " +
                        "`postedAt` INTEGER NOT NULL, `matched` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_notifications_postedAt` ON `raw_notifications` (`postedAt`)")
            }
        }
    }
}
