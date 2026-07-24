package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "decision_logs")
data class DecisionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val simulationTitle: String,
    val question: String,
    val choice: String,
    val sentiment: String,
    val reflection: String,
    val aiAnalysis: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decision_logs ORDER BY timestamp DESC")
    fun getAllDecisions(): Flow<List<DecisionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: DecisionLog)

    @Query("DELETE FROM decision_logs WHERE id = :id")
    suspend fun deleteDecisionById(id: Int)

    @Query("DELETE FROM decision_logs")
    suspend fun clearAllDecisions()
}

@Database(entities = [DecisionLog::class], version = 1, exportSchema = false)
abstract class DecisionDatabase : RoomDatabase() {
    abstract fun decisionDao(): DecisionDao

    companion object {
        @Volatile
        private var INSTANCE: DecisionDatabase? = null

        fun getDatabase(context: Context): DecisionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DecisionDatabase::class.java,
                    "gordian_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class DecisionRepository(private val decisionDao: DecisionDao) {
    val allDecisions: Flow<List<DecisionLog>> = decisionDao.getAllDecisions()

    suspend fun insert(decision: DecisionLog) {
        decisionDao.insertDecision(decision)
    }

    suspend fun deleteById(id: Int) {
        decisionDao.deleteDecisionById(id)
    }

    suspend fun clearAll() {
        decisionDao.clearAllDecisions()
    }
}
