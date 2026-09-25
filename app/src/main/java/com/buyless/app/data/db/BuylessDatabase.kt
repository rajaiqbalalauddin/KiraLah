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
    entities = [TransactionEntity::class, WatchedAppEntity::class, PendingEntity::class, PaymentQrEntity::class, RawNotificationEntity::class, SplitBillEntity::class, FriendEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class BuylessDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun watchedAppDao(): WatchedAppDao
    abstract fun pendingDao(): PendingDao
    abstract fun paymentQrDao(): PaymentQrDao
    abstract fun rawNotificationDao(): RawNotificationDao
    abstract fun splitBillDao(): SplitBillDao
    abstract fun friendDao(): FriendDao

    companion object {
        fun build(context: Context): BuylessDatabase =
            Room.databaseBuilder(context, BuylessDatabase::class.java, "buyless.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

        /** v4 adds a starting balance per app, so balances can be set without a real transfer. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watched_apps` ADD COLUMN `balanceSen` INTEGER")
                db.execSQL("ALTER TABLE `watched_apps` ADD COLUMN `balanceSetAt` INTEGER")
            }
        }

        /** v5 adds split history, so past bills and their QR screens can be reopened. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `split_bills` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `totalSen` INTEGER NOT NULL, " +
                        "`peopleCount` INTEGER NOT NULL, `paidCount` INTEGER NOT NULL, `receiptPath` TEXT, " +
                        "`stateJson` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_bills_updatedAt` ON `split_bills` (`updatedAt`)")
            }
        }

        /** v6 adds saved friends (name, phone, favourite) for the people drawer and WhatsApp. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `friends` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, " +
                        "`phone` TEXT, `colorIndex` INTEGER NOT NULL, `favourite` INTEGER NOT NULL, " +
                        "`timesSplit` INTEGER NOT NULL, `lastSplitAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_friends_nameKey` ON `friends` (`nameKey`)")
            }
        }
    }
}
