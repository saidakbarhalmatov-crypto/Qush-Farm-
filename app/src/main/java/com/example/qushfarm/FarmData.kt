package com.example.qushfarm

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@Entity(tableName = "egg_collections")
data class EggCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inputAmount: Int,
    val unit: String,              // "Флейки" (30 шт.) или "Штук"
    val totalEggs: Int,
    val estimatedValue: Double,
    val dateStr: String,
    val timeStr: String,
    val timestamp: Long,
    val addedBy: String
)

@Entity(tableName = "egg_sales")
data class EggSaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inputAmount: Int,
    val unit: String,
    val totalEggsSold: Int,
    val pricePerEgg: Double,
    val totalAmountKgs: Double,
    val dateStr: String,
    val timeStr: String,
    val timestamp: Long,
    val addedBy: String
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,              // "INCOME" или "EXPENSE"
    val category: String,
    val description: String,
    val amountKgs: Double,
    val dateStr: String,
    val timeStr: String,
    val timestamp: Long,
    val addedBy: String
)

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val quantity: Double,
    val remainingQuantity: Double,
    val unit: String,              // "кг" или "г"
    val costKgs: Double,
    val dateStr: String,
    val timestamp: Long,
    val addedBy: String
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,              // "FEED", "EGGS", "CLEANING"
    val title: String,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int?,           // null = ежедневно, 1..7 = раз в неделю
    val isEnabled: Boolean = true
)

@Dao
interface FarmDao {
    @Query("SELECT * FROM egg_collections ORDER BY timestamp DESC")
    fun getAllCollections(): Flow<List<EggCollectionEntity>>

    @Insert
    suspend fun insertCollection(item: EggCollectionEntity)

    @Delete
    suspend fun deleteCollection(item: EggCollectionEntity)

    @Query("SELECT * FROM egg_sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<EggSaleEntity>>

    @Insert
    suspend fun insertSale(item: EggSaleEntity)

    @Delete
    suspend fun deleteSale(item: EggSaleEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(item: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(item: TransactionEntity)

    @Query("SELECT * FROM feeds ORDER BY timestamp DESC")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Insert
    suspend fun insertFeed(item: FeedEntity)

    @Update
    suspend fun updateFeed(item: FeedEntity)

    @Delete
    suspend fun deleteFeed(item: FeedEntity)

    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isEnabled = 1")
    suspend fun getEnabledRemindersSync(): List<ReminderEntity>

    @Insert
    suspend fun insertReminder(item: ReminderEntity): Long

    @Update
    suspend fun updateReminder(item: ReminderEntity)

    @Delete
    suspend fun deleteReminder(item: ReminderEntity)
}

@Database(
    entities = [
        EggCollectionEntity::class,
        EggSaleEntity::class,
        TransactionEntity::class,
        FeedEntity::class,
        ReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FarmDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao

    companion object {
        @Volatile
        private var INSTANCE: FarmDatabase? = null

        fun getDatabase(context: Context): FarmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FarmDatabase::class.java,
                    "qush_farm_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class FarmPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("qush_farm_prefs", Context.MODE_PRIVATE)

    fun getUserName(): String = prefs.getString("user_name", "") ?: ""

    fun setUserName(name: String) {
        prefs.edit().putString("user_name", name.trim()).apply()
    }

    fun getEggPrice(): Double = prefs.getFloat("egg_price_kgs", 12.0f).toDouble()

    fun setEggPrice(price: Double) {
        prefs.edit().putFloat("egg_price_kgs", price.toFloat()).apply()
    }
}

object DateUtils {
    fun todayDateStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    fun currentTimeStr(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    fun formatDisplayDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = parser.parse(dateStr)
            if (date != null) formatter.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    fun isWithinLastDays(dateStr: String, days: Int): Boolean {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = parser.parse(dateStr) ?: return false
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -days)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            !date.before(cal.time)
        } catch (e: Exception) {
            false
        }
    }

    fun dayOfWeekName(day: Int?): String {
        return when (day) {
            Calendar.MONDAY -> "Понедельник"
            Calendar.TUESDAY -> "Вторник"
            Calendar.WEDNESDAY -> "Среда"
            Calendar.THURSDAY -> "Четверг"
            Calendar.FRIDAY -> "Пятница"
            Calendar.SATURDAY -> "Суббота"
            Calendar.SUNDAY -> "Воскресенье"
            else -> "Каждый день"
        }
    }
}
