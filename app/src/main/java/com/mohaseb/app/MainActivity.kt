package com.mohaseb.app

import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mohaseb.app.data.db.DatabaseHelper
import com.mohaseb.app.data.firebase.FirestoreSyncManager
import com.mohaseb.app.data.model.Bill
import com.mohaseb.app.data.model.BillItem
import com.mohaseb.app.data.model.Customer
import com.mohaseb.app.data.model.Item
import com.mohaseb.app.data.model.Transaction
import com.mohaseb.app.databinding.ActivityMainBinding
import com.mohaseb.app.util.FormatUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var syncManager: FirestoreSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper.getInstance(this)
        syncManager = FirestoreSyncManager(this)

        setupToolbar()
        setupListeners()
        loadDashboardData()
    }

    override fun onResume() {
        super.onResume()
        loadDashboardData()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.action_sync -> {
                    triggerCloudSync()
                    true
                }
                R.id.action_backup -> {
                    showBackupDialog()
                    true
                }
                R.id.action_settings -> {
                    showSettingsDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupListeners() {
        binding.btnQuickSync.setOnClickListener {
            triggerCloudSync()
        }

        binding.btnNewSale.setOnClickListener {
            showNewBillDialog(trType = 1) // 1: مبيعات
        }

        binding.btnNewPurchase.setOnClickListener {
            showNewBillDialog(trType = 2) // 2: مشتريات
        }

        binding.btnReceiptVoucher.setOnClickListener {
            showVoucherDialog(isReceipt = true)
        }

        binding.btnPaymentVoucher.setOnClickListener {
            showVoucherDialog(isReceipt = false)
        }

        binding.btnCustomers.setOnClickListener {
            showCustomersDialog()
        }

        binding.btnInventory.setOnClickListener {
            showInventoryDialog()
        }

        binding.btnReports.setOnClickListener {
            showReportsDialog()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.fabAdd.setOnClickListener {
            showQuickAddDialog()
        }
    }

    private fun loadDashboardData() {
        val stats = dbHelper.getDashboardStats()
        val settings = dbHelper.getStoreSettings()

        binding.tvStoreName.text = settings.storeName.ifBlank { getString(R.string.app_name) }
        binding.tvTotalSales.text = FormatUtils.formatMoney(stats.totalSales, settings.currency)
        binding.tvTotalPurchases.text = FormatUtils.formatMoney(stats.totalPurchases, settings.currency)
        binding.tvCashBalance.text = FormatUtils.formatMoney(stats.cashBalance, settings.currency)
        binding.tvReceivables.text = FormatUtils.formatMoney(stats.totalReceivables, settings.currency)
    }

    private fun triggerCloudSync() {
        binding.tvCloudStatus.text = "المزامنة السحابية: جاري المزامنة..."
        binding.btnQuickSync.isEnabled = false

        lifecycleScope.launch {
            val result = syncManager.syncLocalToCloud()
            binding.btnQuickSync.isEnabled = true
            if (result.isSuccess) {
                binding.tvCloudStatus.text = "المزامنة السحابية: متصلة (تمت المزامنة بنجاح)"
                Toast.makeText(this@MainActivity, result.getOrNull() ?: "تمت المزامنة بنجاح", Toast.LENGTH_LONG).show()
            } else {
                binding.tvCloudStatus.text = "المزامنة السحابية: في انتظار تسجيل الدخول أو إعداد Firebase"
                Toast.makeText(this@MainActivity, result.exceptionOrNull()?.message ?: "خطأ في المزامنة", Toast.LENGTH_LONG).show()
            }
            loadDashboardData()
        }
    }

    private fun showNewBillDialog(trType: Int) {
        val title = if (trType == 1) "فاتورة مبيعات جديدة" else "فاتورة مشتريات جديدة"
        val items = dbHelper.getAllItems()
        val customers = dbHelper.getAllCustomers()

        if (items.isEmpty()) {
            Toast.makeText(this, "يرجى إضافة أصناف أولاً قبل إنشاء الفاتورة", Toast.LENGTH_SHORT).show()
            showAddItemDialog()
            return
        }

        val itemNames = items.map { "${it.name} (${it.price} ر.س) [المخزون: ${it.qty}]" }.toTypedArray()
        var selectedItemIndex = 0

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etQty = EditText(this).apply {
            hint = "الكمية"
            setText("1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        dialogView.addView(etQty)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(itemNames, 0) { _, which ->
                selectedItemIndex = which
            }
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val selectedItem = items[selectedItemIndex]
                val qty = etQty.text.toString().toDoubleOrNull() ?: 1.0
                val price = if (trType == 1) selectedItem.price else selectedItem.cost
                val subtotal = qty * price
                val defaultCus = customers.firstOrNull() ?: Customer(id = 1, name = "عميل نقدي")

                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val billNo = dbHelper.generateNextBillNo(trType)

                val bill = Bill(
                    billNo = billNo,
                    date = dateStr,
                    trType = trType,
                    billType = 1,
                    cusId = defaultCus.id,
                    cusName = defaultCus.name,
                    amount = subtotal,
                    discount = 0.0,
                    taxAmount = 0.0,
                    netAmount = subtotal,
                    remarks = "فاتورة سريعة من التطبيق"
                )

                val billItem = BillItem(
                    itemId = selectedItem.id,
                    itemName = selectedItem.name,
                    qty = qty,
                    price = price,
                    cost = selectedItem.cost,
                    discount = 0.0,
                    total = subtotal
                )
                bill.items.add(billItem)

                val billId = dbHelper.insertBill(bill)
                if (billId > 0) {
                    Toast.makeText(this, "تم إنشاء الفاتورة رقم $billNo بنجاح", Toast.LENGTH_SHORT).show()
                    loadDashboardData()
                } else {
                    Toast.makeText(this, "حدث خطأ أثناء حفظ الفاتورة", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showVoucherDialog(isReceipt: Boolean) {
        val title = if (isReceipt) "سند قبض نقدي" else "سند صرف نقدي"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etCustomer = EditText(this).apply { hint = "اسم الحساب / العميل" }
        val etAmount = EditText(this).apply {
            hint = "المبلغ (ر.س)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etNotes = EditText(this).apply { hint = "البيان / ملاحظات" }

        layout.addView(etCustomer)
        layout.addView(etAmount)
        layout.addView(etNotes)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val cusName = etCustomer.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val notes = etNotes.text.toString().trim()

                if (cusName.isEmpty() || amount <= 0.0) {
                    Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                var customer = dbHelper.searchCustomers(cusName).firstOrNull()
                val cusId = customer?.id ?: dbHelper.insertCustomer(Customer(name = cusName))

                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val tx = Transaction(
                    cusId = cusId,
                    cusName = cusName,
                    inAmount = if (isReceipt) amount else 0.0,
                    outAmount = if (isReceipt) 0.0 else amount,
                    date = dateStr,
                    remarks = notes.ifBlank { title },
                    trType = if (isReceipt) "سند قبض" else "سند صرف"
                )

                dbHelper.insertTransaction(tx)
                Toast.makeText(this, R.string.success_save, Toast.LENGTH_SHORT).show()
                loadDashboardData()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddItemDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etName = EditText(this).apply { hint = "اسم الصنف *" }
        val etBarcode = EditText(this).apply { hint = "الباركود" }
        val etPrice = EditText(this).apply {
            hint = "سعر البيع *"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etCost = EditText(this).apply {
            hint = "سعر التكلفة"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etQty = EditText(this).apply {
            hint = "الكمية الأولية"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        layout.addView(etName)
        layout.addView(etBarcode)
        layout.addView(etPrice)
        layout.addView(etCost)
        layout.addView(etQty)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_item)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = etName.text.toString().trim()
                val barcode = etBarcode.text.toString().trim()
                val price = etPrice.text.toString().toDoubleOrNull() ?: 0.0
                val cost = etCost.text.toString().toDoubleOrNull() ?: 0.0
                val qty = etQty.text.toString().toDoubleOrNull() ?: 0.0

                if (name.isEmpty() || price <= 0.0) {
                    Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val item = Item(
                    name = name,
                    barcode = barcode,
                    price = price,
                    cost = cost,
                    qty = qty
                )
                dbHelper.insertItem(item)
                Toast.makeText(this, R.string.success_save, Toast.LENGTH_SHORT).show()
                loadDashboardData()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddCustomerDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etName = EditText(this).apply { hint = "الاسم *" }
        val etPhone = EditText(this).apply {
            hint = "رقم الهاتف"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val etAddress = EditText(this).apply { hint = "العنوان" }

        layout.addView(etName)
        layout.addView(etPhone)
        layout.addView(etAddress)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_customer)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val address = etAddress.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.error_fill_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val cus = Customer(name = name, gsm = phone, address = address)
                dbHelper.insertCustomer(cus)
                Toast.makeText(this, R.string.success_save, Toast.LENGTH_SHORT).show()
                loadDashboardData()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showInventoryDialog() {
        val items = dbHelper.getAllItems()
        val itemsSummary = if (items.isEmpty()) {
            "لا توجد أصناف حالياً."
        } else {
            items.joinToString("\n") {
                "• ${it.name} | السعر: ${it.price} | التكلفة: ${it.cost} | المخزون: ${it.qty}"
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.inventory)
            .setMessage(itemsSummary)
            .setPositiveButton("إضافة صنف جديد") { _, _ -> showAddItemDialog() }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showCustomersDialog() {
        val customers = dbHelper.getAllCustomers()
        val summary = if (customers.isEmpty()) {
            "لا توجد حسابات أو عملاء مسجلين."
        } else {
            customers.joinToString("\n") {
                "• ${it.name} (${it.typeName}) | هاتف: ${it.gsm.ifBlank { "-" }} | الرصيد: ${it.balance} ر.س"
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.customers_suppliers)
            .setMessage(summary)
            .setPositiveButton("إضافة عميل / مورد") { _, _ -> showAddCustomerDialog() }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showReportsDialog() {
        val (sales, purchases, profit) = dbHelper.getProfitAndLoss()
        val (valuationCost, valuationSale) = dbHelper.getInventoryValuation()
        val stats = dbHelper.getDashboardStats()

        val reportText = """
            تقرير الأرباح والمركز المالي:
            ───────────────────────
            • إجمالي المبيعات: ${FormatUtils.formatMoney(sales)}
            • إجمالي المشتريات: ${FormatUtils.formatMoney(purchases)}
            • صافي الربح التقديري: ${FormatUtils.formatMoney(profit)}
            ───────────────────────
            • تقييم المخزون بالتكلفة: ${FormatUtils.formatMoney(valuationCost)}
            • تقييم المخزون بالبيع: ${FormatUtils.formatMoney(valuationSale)}
            ───────────────────────
            • ديون العملاء (مستحقات لنا): ${FormatUtils.formatMoney(stats.totalReceivables)}
            • ديون الموردين (التزامات علينا): ${FormatUtils.formatMoney(stats.totalPayables)}
            • رصيد الصندوق النقدي: ${FormatUtils.formatMoney(stats.cashBalance)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(R.string.reports)
            .setMessage(reportText)
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun showSettingsDialog() {
        val settings = dbHelper.getStoreSettings()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etStoreName = EditText(this).apply {
            hint = "اسم المنشأة"
            setText(settings.storeName)
        }
        val etPhone = EditText(this).apply {
            hint = "رقم الهاتف"
            setText(settings.storePhone)
        }
        val etTax = EditText(this).apply {
            hint = "الرقم الضريبي"
            setText(settings.taxNumber)
        }

        layout.addView(etStoreName)
        layout.addView(etPhone)
        layout.addView(etTax)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                settings.storeName = etStoreName.text.toString().trim()
                settings.storePhone = etPhone.text.toString().trim()
                settings.taxNumber = etTax.text.toString().trim()
                dbHelper.saveStoreSettings(settings)
                Toast.makeText(this, R.string.success_save, Toast.LENGTH_SHORT).show()
                loadDashboardData()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBackupDialog() {
        val payload = dbHelper.exportAllData()
        val summary = """
            ملخص النسخة الاحتياطية:
            • العملاء: ${payload.customers.size}
            • الأصناف: ${payload.items.size}
            • الفواتير: ${payload.bills.size}
            • القيود والعمليات: ${payload.transactions.size}
            • التاريخ: ${payload.exportDate}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(R.string.backup_data)
            .setMessage(summary)
            .setPositiveButton("مزامنة إلى السحابة") { _, _ -> triggerCloudSync() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showQuickAddDialog() {
        val options = arrayOf(
            "فاتورة مبيعات جديدة",
            "فاتورة مشتريات جديدة",
            "سند قبض",
            "سند صرف",
            "إضافة صنف جديد",
            "إضافة عميل / مورد جديد"
        )

        AlertDialog.Builder(this)
            .setTitle("إضافة سريعة")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showNewBillDialog(trType = 1)
                    1 -> showNewBillDialog(trType = 2)
                    2 -> showVoucherDialog(isReceipt = true)
                    3 -> showVoucherDialog(isReceipt = false)
                    4 -> showAddItemDialog()
                    5 -> showAddCustomerDialog()
                }
            }
            .show()
    }
}
