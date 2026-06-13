package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.Category
import com.example.data.Config
import com.example.data.Expense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(db)
    
    private val prefs = application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)

    var termsAccepted by androidx.compose.runtime.mutableStateOf(prefs.getBoolean("terms_accepted", false))
        private set

    fun acceptTerms() {
        prefs.edit().putBoolean("terms_accepted", true).apply()
        termsAccepted = true
    }

    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val config: StateFlow<Config?> = repository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _currentMonth = MutableStateFlow(Calendar.getInstance())
    val currentMonth: StateFlow<Calendar> = _currentMonth.asStateFlow()

    val filteredExpenses: StateFlow<List<Expense>> = combine(allExpenses, currentMonth) { expenses, cal ->
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        expenses.filter { exp ->
            val expCal = Calendar.getInstance().apply { timeInMillis = exp.dueDate }
            expCal.get(Calendar.MONTH) == month && expCal.get(Calendar.YEAR) == year
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.allCategories.collect { cats ->
                if (cats.isEmpty()) {
                    val defaultCategories = listOf("Moradia", "Alimentação", "Lazer", "Transporte", "Saúde", "Outros")
                    defaultCategories.forEach { name ->
                        repository.insertCategory(Category(name = name))
                    }
                }
            }
        }
    }

    fun nextMonth() {
        val next = _currentMonth.value.clone() as Calendar
        next.add(Calendar.MONTH, 1)
        _currentMonth.value = next
    }

    fun previousMonth() {
        val prev = _currentMonth.value.clone() as Calendar
        prev.add(Calendar.MONTH, -1)
        _currentMonth.value = prev
    }

    fun addExpense(expense: Expense, occurrences: Int = 1) {
        viewModelScope.launch {
            if (expense.frequency == "NONE" || occurrences <= 1) {
                repository.insertExpense(expense)
            } else {
                val recId = UUID.randomUUID().toString()
                val cal = Calendar.getInstance()
                cal.timeInMillis = expense.dueDate
                for (i in 0 until occurrences) {
                    val newExp = expense.copy(id = 0, dueDate = cal.timeInMillis, recurringId = recId)
                    repository.insertExpense(newExp)

                    when (expense.frequency) {
                        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                        "YEARLY" -> cal.add(Calendar.YEAR, 1)
                    }
                }
            }
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            repository.deleteExpense(id)
        }
    }

    fun updateConfig(income: Double, notifyDays: Int) {
        viewModelScope.launch {
            val currentConfig = config.value ?: Config()
            repository.saveConfig(currentConfig.copy(monthlyIncome = income, notificationDaysBefore = notifyDays))
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            val currentConfig = config.value ?: Config()
            repository.saveConfig(currentConfig.copy(isDarkTheme = isDark))
        }
    }

    fun toggleExpensePaidStatus(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense.copy(isPaid = !expense.isPaid))
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }
}
