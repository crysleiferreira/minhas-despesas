package com.example

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.Expense
import com.example.ui.ExpensesPieChart
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.PdfHelper
import com.example.worker.ExpenseWorker
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            ExpenseWorker.schedule(this) // Schedule daily backup/notification checks
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val config by viewModel.config.collectAsState()
            val isDarkTheme = config?.isDarkTheme ?: true

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

fun parseDoubleSafely(text: String): Double {
    val clean = text.replace(Regex("[^0-9.,]"), "")
    val hasComma = clean.contains(",")
    if (hasComma) {
        return clean.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    }
    return clean.toDoubleOrNull() ?: 0.0
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    
    val startDestination = if (viewModel.termsAccepted) "dashboard" else "terms"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("dashboard") { DashboardScreen(navController, viewModel) }
        composable("terms") { TermsScreen(navController, viewModel) }
        composable(
            "add_expense?expenseId={expenseId}",
            arguments = listOf(androidx.navigation.navArgument("expenseId") { 
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getString("expenseId")?.toIntOrNull()
            AddExpenseScreen(navController, viewModel, expenseId)
        }
        composable("reports") { ReportsScreen(navController, viewModel) }
        composable("config") { ConfigScreen(navController, viewModel) }
        composable("categories") { CategoriesScreen(navController, viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: MainViewModel) {
    val expenses by viewModel.filteredExpenses.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val config by viewModel.config.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    
    val income = config?.monthlyIncome ?: 0.0
    // calculation based on ALL expenses for balance? Or just current month?
    // "o aplicativo atualiza automaticamente o meu saldo mensal disponível"
    // Income is typical per month. So we subtract current month expenses.
    val paidTotal = expenses.filter { it.isPaid }.sumOf { it.amount }
    val pendingTotal = expenses.filter { !it.isPaid }.sumOf { it.amount }
    val currentBalance = income - paidTotal - pendingTotal

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = { viewModel.setDarkTheme(!(config?.isDarkTheme ?: true)) }) {
                        Icon(
                            if (config?.isDarkTheme == true) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Alternar Tema"
                        )
                    }
                    IconButton(onClick = { navController.navigate("terms") }) {
                        Icon(Icons.Default.Info, contentDescription = "Termos de Uso e Privacidade")
                    }
                    IconButton(onClick = { navController.navigate("config") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_expense") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Despesa", modifier = Modifier.size(30.dp))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val isDarkTheme = config?.isDarkTheme ?: true
            BalanceCard(income, currentBalance, paidTotal, pendingTotal, isDarkTheme, onReportClick = { navController.navigate("reports") })
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Default.ChevronLeft, "Mês anterior")
                }
                Text(
                    text = monthFormat.format(currentMonth.time).replaceFirstChar { it.uppercase() }, 
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Default.ChevronRight, "Próximo mês")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (expenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma conta cadastrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                    val sortedExpenses = expenses.sortedWith(compareBy({ it.isPaid }, { it.dueDate }))
                    items(sortedExpenses) { expense ->
                        ExpenseItem(
                            expense = expense, 
                            onToggle = { viewModel.toggleExpensePaidStatus(it) },
                            onDelete = { viewModel.deleteExpense(it.id) },
                            onEdit = { navController.navigate("add_expense?expenseId=${it.id}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceCard(income: Double, balance: Double, paid: Double, pending: Double, isDarkTheme: Boolean, onReportClick: () -> Unit) {
    val border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF81D4FA))

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Saldo Disponível", style = MaterialTheme.typography.labelLarge, modifier = Modifier.alpha(0.8f))
                IconButton(onClick = onReportClick, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.List, contentDescription = "Relatório Mensal")
                }
            }
            Text(currencyFormatter.format(balance), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Receita", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.7f))
                    Text(currencyFormatter.format(income), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Pago", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.7f))
                    Text(currencyFormatter.format(paid), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
                Column {
                    Text("Pendente", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.7f))
                    Text(currencyFormatter.format(pending), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                }
            }
        }
    }
}

@Composable
fun ExpenseItem(expense: Expense, onToggle: (Expense) -> Unit, onDelete: (Expense) -> Unit, onEdit: (Expense) -> Unit) {
    val borderColor = if (expense.isPaid) com.example.ui.theme.GreenIndicator else com.example.ui.theme.RedIndicator

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (expense.isPaid) 0.5f else 1f)
            .clickable { onEdit(expense) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(70.dp).background(borderColor))
            
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 16.dp, bottom = 16.dp)) {
                Text(
                    text = expense.title, 
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (expense.isPaid) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                val statusText = if (expense.isPaid) "Pago" else "Pendente • Venc: ${dateFormatter.format(Date(expense.dueDate))}"
                Text(
                    text = "${expense.category} • $statusText", 
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expense.isPaid) com.example.ui.theme.GreenIndicator else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = currencyFormatter.format(expense.amount), 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onDelete(expense) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onToggle(expense) }, modifier = Modifier.size(32.dp)) {
                        if (expense.isPaid) {
                            Box(modifier = Modifier.size(28.dp).background(com.example.ui.theme.GreenIndicator, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Pago", tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Box(modifier = Modifier.size(28.dp).border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Marcar como pago", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(navController: NavController, viewModel: MainViewModel, expenseId: Int? = null) {
    val allExpenses by viewModel.allExpenses.collectAsState()
    val expenseToEdit = remember(expenseId, allExpenses) { allExpenses.find { it.id == expenseId } }

    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val categories by viewModel.categories.collectAsState()
    var category by remember { mutableStateOf("Geral") }
    var expandedCategory by remember { mutableStateOf(false) }

    val frequencies = listOf("NONE" to "Uma vez", "WEEKLY" to "Semanal", "MONTHLY" to "Mensal", "YEARLY" to "Anual")
    var frequency by remember { mutableStateOf(frequencies[0].first) }
    var expandedFrequency by remember { mutableStateOf(false) }

    var occurrencesText by remember { mutableStateOf("1") }

    LaunchedEffect(expenseToEdit, categories) {
        if (expenseToEdit != null) {
            title = expenseToEdit.title
            amountText = expenseToEdit.amount.toString().replace(".", ",")
            dueDate = expenseToEdit.dueDate
            category = expenseToEdit.category
            frequency = expenseToEdit.frequency
        } else if (categories.isNotEmpty() && category == "Geral") {
            category = categories.first().name
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseToEdit != null) "Editar Despesa" else "Nova Despesa") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nome da Conta") },
                modifier = Modifier.fillMaxWidth().testTag("title_input")
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Valor (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("amount_input")
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Categoria") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                category = cat.name
                                expandedCategory = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            ExposedDropdownMenuBox(
                expanded = expandedFrequency,
                onExpandedChange = { expandedFrequency = !expandedFrequency },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = frequencies.find { it.first == frequency }?.second ?: "Uma vez",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Repetir") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrequency) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedFrequency,
                    onDismissRequest = { expandedFrequency = false }
                ) {
                    frequencies.forEach { freq ->
                        DropdownMenuItem(
                            text = { Text(freq.second) },
                            onClick = {
                                frequency = freq.first
                                expandedFrequency = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (frequency != "NONE") {
                OutlinedTextField(
                    value = occurrencesText,
                    onValueChange = { occurrencesText = it },
                    label = { Text("Quantas vezes repetir?") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Text("Vencimento: ${dateFormatter.format(Date(dueDate))}", modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        cal.set(year, month, day)
                        dueDate = cal.timeInMillis
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) {
                Text("Selecionar Data")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val amt = parseDoubleSafely(amountText)
                    if (title.isNotBlank() && amt > 0) {
                        if (expenseToEdit != null) {
                            viewModel.updateExpense(
                                expenseToEdit.copy(
                                    title = title,
                                    amount = amt,
                                    category = category,
                                    dueDate = dueDate,
                                    frequency = frequency
                                )
                            )
                        } else {
                            val occurences = if (frequency == "NONE") 1 else occurrencesText.toIntOrNull() ?: 1
                            viewModel.addExpense(Expense(title = title, amount = amt, category = category, dueDate = dueDate, frequency = frequency), occurences)
                        }
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("save_expense_button")
            ) {
                Text("Salvar Despesa")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController, viewModel: MainViewModel) {
    val expenses by viewModel.filteredExpenses.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val context = LocalContext.current
    
    val monthFormat = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("pt", "BR")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatório Mensal - ${monthFormat.format(currentMonth.time).replaceFirstChar { it.uppercase() }}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = "Despesas por Categoria", 
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            
            ExpensesPieChart(
                expenses = expenses, 
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { PdfHelper.exportExpensesToPdf(context, expenses) },
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("export_pdf_button")
            ) {
                Text("Exportar PDF")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(navController: NavController, viewModel: MainViewModel) {
    val config by viewModel.config.collectAsState()
    var incomeText by remember { mutableStateOf("") }
    
    val notifyOptions = listOf(1, 3, 7, 15)
    var selectedNotifyDays by remember { mutableStateOf(3) }
    var expandedNotify by remember { mutableStateOf(false) }
    
    // update text when config loads
    LaunchedEffect(config) {
        if (incomeText.isEmpty() && (config?.monthlyIncome ?: 0.0) > 0) {
            incomeText = String.format(Locale("pt", "BR"), "%.2f", config?.monthlyIncome)
        }
        config?.notificationDaysBefore?.let {
            if (it != selectedNotifyDays) selectedNotifyDays = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("categories") }) {
                        Icon(Icons.Default.List, "Gerenciar Categorias")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Text("Defina sua Receita Mensal para calcular o Saldo Disponível.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = incomeText,
                onValueChange = { incomeText = it },
                label = { Text("Receita Mensal (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("income_input")
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Notificações de Vencimento", style = MaterialTheme.typography.titleMedium)
            Text("Avisar quantos dias antes do vencimento?", style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.7f).padding(bottom = 8.dp))
            
            ExposedDropdownMenuBox(
                expanded = expandedNotify,
                onExpandedChange = { expandedNotify = !expandedNotify },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = "$selectedNotifyDays dias antes",
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedNotify) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedNotify,
                    onDismissRequest = { expandedNotify = false }
                ) {
                    notifyOptions.forEach { days ->
                        DropdownMenuItem(
                            text = { Text("$days dias antes") },
                            onClick = {
                                selectedNotifyDays = days
                                expandedNotify = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    val inc = parseDoubleSafely(incomeText)
                    viewModel.updateConfig(inc, selectedNotifyDays)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().testTag("save_config_button")
            ) {
                Text("Salvar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(navController: NavController, viewModel: MainViewModel) {
    val categories by viewModel.categories.collectAsState()
    var newCategoryName by remember { mutableStateOf("") }
    
    var editingCategory by remember { mutableStateOf<com.example.data.Category?>(null) }
    var editCategoryName by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorias") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Nova Categoria") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        viewModel.addCategory(newCategoryName)
                        newCategoryName = ""
                    }
                }) {
                    Text("Adicionar")
                }
            }
            
            LazyColumn {
                items(categories) { cat ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        if (editingCategory?.id == cat.id) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = editCategoryName,
                                    onValueChange = { editCategoryName = it },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = {
                                    if (editCategoryName.isNotBlank()) {
                                        viewModel.updateCategory(cat.copy(name = editCategoryName))
                                        editingCategory = null
                                    }
                                }) {
                                    Icon(Icons.Default.CheckCircle, "Salvar", tint = com.example.ui.theme.GreenIndicator)
                                }
                                IconButton(onClick = { editingCategory = null }) {
                                    Icon(Icons.Default.Close, "Cancelar", tint = com.example.ui.theme.RedIndicator)
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = { 
                                        editingCategory = cat
                                        editCategoryName = cat.name 
                                    }) {
                                        Icon(Icons.Default.Edit, "Editar Categoria", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { viewModel.deleteCategory(cat) }) {
                                        Icon(Icons.Default.Delete, "Excluir Categoria", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(navController: NavController, viewModel: MainViewModel) {
    var accepted by androidx.compose.runtime.remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termos de Uso") },
                navigationIcon = {
                    if (viewModel.termsAccepted) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val scrollState = androidx.compose.foundation.rememberScrollState()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Termos de Uso e Política de Privacidade\n\n" +
                            "Bem-vindo(a)! Antes de começar a usar nosso aplicativo, pedimos que leia atentamente os termos abaixo. Ao utilizar o app, você concorda com as condições descritas.\n\n" +
                            "1. Privacidade e Proteção de Dados (LGPD)\n" +
                            "Em total conformidade com a Lei Geral de Proteção de Dados Pessoais (LGPD - Lei nº 13.709/2018), nosso compromisso com a sua privacidade é absoluto.\n\n" +
                            "Zero Coleta Externa: Este aplicativo não coleta, não rastreia, não transmite e não compartilha nenhuma informação pessoal ou financeira com servidores externos, empresas terceiras ou com os desenvolvedores.\n\n" +
                            "Armazenamento 100% Local: Todos os dados que você insere no aplicativo são salvos exclusivamente no armazenamento interno do seu próprio dispositivo. Suas finanças pertencem somente a você.\n\n" +
                            "2. Risco e Perda de Dados\n" +
                            "Como o aplicativo funciona de forma totalmente off-line e autônoma, sem sincronização em nuvem da nossa parte:\n\n" +
                            "Não nos responsabilizamos por eventuais perdas de dados armazenados.\n\n" +
                            "A exclusão do aplicativo, a limpeza de dados (cache/armazenamento) nas configurações do sistema, a formatação do aparelho, bem como roubo, perda ou danos ao dispositivo, resultarão na perda permanente das informações registradas.\n\n" +
                            "É de inteira responsabilidade do usuário realizar backups próprios de seu aparelho, caso deseje proteger suas informações.\n\n" +
                            "3. Responsabilidade de Uso\n" +
                            "O aplicativo é fornecido como uma ferramenta facilitadora para o controle de despesas pessoais, \"no estado em que se encontra\".\n\n" +
                            "O uso desta ferramenta, bem como a precisão dos dados nela inseridos e as decisões financeiras tomadas a partir deles, são de total e exclusiva responsabilidade do usuário.\n\n" +
                            "Os desenvolvedores do aplicativo não se responsabilizam por quaisquer danos diretos, indiretos, incidentais ou consequenciais resultantes do uso ou da incapacidade de uso do aplicativo.\n\n" +
                            "4. Aceitação dos Termos\n" +
                            "Ao continuar a usar o aplicativo, você declara ter lido, compreendido e concordado expressamente com todos os Termos de Uso e com a Política de Privacidade aqui estabelecidos.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            if (!viewModel.termsAccepted) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { accepted = !accepted }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = accepted,
                                onCheckedChange = { accepted = it }
                            )
                            Text("Li e aceito os Termos de Uso e LGPD", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                        Button(
                            onClick = {
                                viewModel.acceptTerms()
                                navController.navigate("dashboard") {
                                    popUpTo("terms") { inclusive = true }
                                }
                            },
                            enabled = accepted,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Continuar")
                        }
                    }
                }
            }
        }
    }
}

