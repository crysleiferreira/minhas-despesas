package com.example.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.example.data.Expense
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfHelper {
    fun exportExpensesToPdf(context: Context, expenses: List<Expense>) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("Relatório Mensal de Despesas", 40f, 60f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        var yPosition = 120f
        
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        var total = 0.0

        if (expenses.isEmpty()) {
            canvas.drawText("Nenhuma despesa para o período.", 40f, yPosition, paint)
        } else {
            expenses.forEach { expense ->
                val status = if (expense.isPaid) "Paga" else "Pendente"
                val line = "- ${expense.title} | ${expense.category}"
                canvas.drawText(line, 40f, yPosition, paint)
                
                val details = "   Valor: ${currencyFormat.format(expense.amount)} | Venc: ${dateFormat.format(Date(expense.dueDate))} | Status: $status"
                yPosition += 20f
                canvas.drawText(details, 40f, yPosition, paint)
                yPosition += 30f
                
                total += expense.amount

                if (yPosition > 800f) {
                    canvas.drawText("... continua ...", 40f, yPosition, paint)
                    return@forEach
                }
            }
            
            yPosition += 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Total: ${currencyFormat.format(total)}", 40f, yPosition, paint)
        }

        pdfDocument.finishPage(page)

        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "Despesas_${System.currentTimeMillis()}.pdf")
        
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(context, "Salvo em Downloads como PDF", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to internal storage if permission fails
            try {
                val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Relatorio.pdf")
                pdfDocument.writeTo(FileOutputStream(fallbackFile))
                Toast.makeText(context, "Salvo em Documentos", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(context, "Erro ao exportar PDF", Toast.LENGTH_SHORT).show()
            }
        } finally {
            pdfDocument.close()
        }
    }
}
