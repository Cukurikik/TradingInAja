package com.tradinginaja.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tradinginaja.core.database.dao.EquitySnapshotDao
import com.tradinginaja.core.database.dao.LiquidationEventDao
import com.tradinginaja.core.database.dao.OrderDao
import com.tradinginaja.core.database.dao.PositionDao
import com.tradinginaja.core.database.dao.TradeFillDao
import com.tradinginaja.core.database.dao.VirtualAccountDao
import com.tradinginaja.core.database.dao.WatchlistDao
import com.tradinginaja.core.database.entity.EquitySnapshotEntity
import com.tradinginaja.core.database.entity.LiquidationEventEntity
import com.tradinginaja.core.database.entity.OrderEntity
import com.tradinginaja.core.database.entity.PositionEntity
import com.tradinginaja.core.database.entity.TradeFillEntity
import com.tradinginaja.core.database.entity.VirtualAccountEntity
import com.tradinginaja.core.database.entity.WatchlistItemEntity

@Database(
    entities = [
        VirtualAccountEntity::class,
        WatchlistItemEntity::class,
        PositionEntity::class,
        OrderEntity::class,
        TradeFillEntity::class,
        EquitySnapshotEntity::class,
        LiquidationEventEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class TradingDatabase : RoomDatabase() {
    abstract fun virtualAccountDao(): VirtualAccountDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun positionDao(): PositionDao
    abstract fun orderDao(): OrderDao
    abstract fun tradeFillDao(): TradeFillDao
    abstract fun equitySnapshotDao(): EquitySnapshotDao
    abstract fun liquidationEventDao(): LiquidationEventDao
}
