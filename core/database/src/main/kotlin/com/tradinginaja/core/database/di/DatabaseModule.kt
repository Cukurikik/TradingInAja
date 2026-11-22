package com.tradinginaja.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tradinginaja.core.database.DatabaseTransactionRunner
import com.tradinginaja.core.database.RoomTransactionRunner
import com.tradinginaja.core.database.TradingDatabase
import com.tradinginaja.core.database.dao.EquitySnapshotDao
import com.tradinginaja.core.database.dao.LiquidationEventDao
import com.tradinginaja.core.database.dao.OrderDao
import com.tradinginaja.core.database.dao.PositionDao
import com.tradinginaja.core.database.dao.TradeFillDao
import com.tradinginaja.core.database.dao.VirtualAccountDao
import com.tradinginaja.core.database.dao.WatchlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `watchlist_items` (
                    `symbol` TEXT NOT NULL,
                    `addedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`symbol`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `positions` (
                    `symbol` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `costBasisMinorUnits` INTEGER NOT NULL,
                    PRIMARY KEY(`symbol`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `orders` (
                    `orderId` TEXT NOT NULL,
                    `symbol` TEXT NOT NULL,
                    `side` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `limitPriceMinorUnits` INTEGER,
                    `reservedCashMinorUnits` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdAtTick` INTEGER NOT NULL,
                    `createdAtEpochMillis` INTEGER NOT NULL,
                    `updatedAtTick` INTEGER NOT NULL,
                    `rejectReason` TEXT,
                    PRIMARY KEY(`orderId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trade_fills` (
                    `fillId` TEXT NOT NULL,
                    `orderId` TEXT NOT NULL,
                    `symbol` TEXT NOT NULL,
                    `side` TEXT NOT NULL,
                    `orderType` TEXT NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `executionPriceMinorUnits` INTEGER NOT NULL,
                    `grossNotionalMinorUnits` INTEGER NOT NULL,
                    `commissionMinorUnits` INTEGER NOT NULL,
                    `slippageBps` INTEGER NOT NULL,
                    `realizedPnlMinorUnits` INTEGER NOT NULL,
                    `tickIndex` INTEGER NOT NULL,
                    `executedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`fillId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `equity_snapshots` (
                    `tickIndex` INTEGER NOT NULL,
                    `equityMinorUnits` INTEGER NOT NULL,
                    `cashMinorUnits` INTEGER NOT NULL,
                    `positionsValueMinorUnits` INTEGER NOT NULL,
                    `recordedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`tickIndex`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `virtual_accounts` ADD COLUMN `careerLevel` INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "ALTER TABLE `virtual_accounts` ADD COLUMN `totalBorrowFeesMinorUnits` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `virtual_accounts` ADD COLUMN `insolvencyCoverageMinorUnits` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE `positions` ADD COLUMN `marginLockedMinorUnits` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `positions` SET `marginLockedMinorUnits` = `costBasisMinorUnits`")
            db.execSQL("ALTER TABLE `positions` ADD COLUMN `leverage` INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "ALTER TABLE `positions` ADD COLUMN `accumulatedBorrowFeeMinorUnits` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE `orders` ADD COLUMN `triggerPriceMinorUnits` INTEGER")
            db.execSQL("ALTER TABLE `orders` ADD COLUMN `leverage` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `orders` ADD COLUMN `takeProfitPriceMinorUnits` INTEGER")
            db.execSQL("ALTER TABLE `orders` ADD COLUMN `stopLossPriceMinorUnits` INTEGER")
            db.execSQL("ALTER TABLE `orders` ADD COLUMN `ocoGroupId` TEXT")
            db.execSQL("ALTER TABLE `trade_fills` ADD COLUMN `isLiquidation` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `liquidation_events` (
                    `eventId` TEXT NOT NULL,
                    `symbol` TEXT NOT NULL,
                    `closedSide` TEXT NOT NULL,
                    `closedQuantity` INTEGER NOT NULL,
                    `liquidationPriceMinorUnits` INTEGER NOT NULL,
                    `realizedPnlMinorUnits` INTEGER NOT NULL,
                    `marginLevelBeforePct` REAL NOT NULL,
                    `tickIndex` INTEGER NOT NULL,
                    `occurredAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`eventId`)
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideTradingDatabase(@ApplicationContext context: Context): TradingDatabase = Room.databaseBuilder(
        context,
        TradingDatabase::class.java,
        "tradingin_aja.db"
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    @Provides
    @Singleton
    fun provideDatabaseTransactionRunner(database: TradingDatabase): DatabaseTransactionRunner =
        RoomTransactionRunner(database)

    @Provides
    fun provideVirtualAccountDao(database: TradingDatabase): VirtualAccountDao = database.virtualAccountDao()

    @Provides
    fun provideWatchlistDao(database: TradingDatabase): WatchlistDao = database.watchlistDao()

    @Provides
    fun providePositionDao(database: TradingDatabase): PositionDao = database.positionDao()

    @Provides
    fun provideOrderDao(database: TradingDatabase): OrderDao = database.orderDao()

    @Provides
    fun provideTradeFillDao(database: TradingDatabase): TradeFillDao = database.tradeFillDao()

    @Provides
    fun provideEquitySnapshotDao(database: TradingDatabase): EquitySnapshotDao = database.equitySnapshotDao()

    @Provides
    fun provideLiquidationEventDao(database: TradingDatabase): LiquidationEventDao = database.liquidationEventDao()
}
