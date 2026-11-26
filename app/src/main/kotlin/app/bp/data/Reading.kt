package app.bp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One blood pressure measurement. [ts] is epoch millis at the moment of save. */
@Entity(tableName = "readings")
data class Reading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val sys: Int,
    val dia: Int,
    val hr: Int,
)

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(reading: Reading)

    @Query("DELETE FROM readings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM readings ORDER BY ts DESC")
    fun all(): Flow<List<Reading>>
}

@Database(entities = [Reading::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bp.db",
                ).build().also { instance = it }
            }
    }
}
