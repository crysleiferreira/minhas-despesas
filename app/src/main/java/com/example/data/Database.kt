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
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val dueDate: Long,
    val category: String,
    val isPaid: Boolean = false,
    val frequency: String = "NONE",
    val recurringId: String? = null
)

@Entity(tableName = "config")
data class Config(
    @PrimaryKey val id: Int = 1,
    val monthlyIncome: Double = 0.0,
    val notificationDaysBefore: Int = 3,
    val isDarkTheme: Boolean = true
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY dueDate ASC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Int)

    @Query("SELECT * FROM expenses WHERE isPaid = 0")
    suspend fun getPendingExpensesSync(): List<Expense>
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config WHERE id = 1")
    fun getConfig(): Flow<Config?>

    @Query("SELECT * FROM config WHERE id = 1")
    suspend fun getConfigSync(): Config?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: Config)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Database(entities = [Expense::class, Config::class, Category::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun configDao(): ConfigDao
    abstract fun categoryDao(): CategoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expenses_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AppRepository(private val db: AppDatabase) {
    val allExpenses: Flow<List<Expense>> = db.expenseDao().getAllExpenses()
    val config: Flow<Config?> = db.configDao().getConfig()
    val allCategories: Flow<List<Category>> = db.categoryDao().getAllCategories()

    suspend fun insertExpense(expense: Expense) = db.expenseDao().insertExpense(expense)
    suspend fun updateExpense(expense: Expense) = db.expenseDao().updateExpense(expense)
    suspend fun deleteExpense(id: Int) = db.expenseDao().deleteExpense(id)
    suspend fun saveConfig(config: Config) = db.configDao().saveConfig(config)
    
    suspend fun insertCategory(category: Category) = db.categoryDao().insertCategory(category)
    suspend fun updateCategory(category: Category) = db.categoryDao().updateCategory(category)
    suspend fun deleteCategory(category: Category) = db.categoryDao().deleteCategory(category)
}
