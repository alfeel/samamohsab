package com.mohaseb.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mohaseb.app.data.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "mohaseb.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. جدول الحسابات والعملاء والموردين
        db.execSQL("""
            CREATE TABLE customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                gsm TEXT DEFAULT '',
                cus_type_id INTEGER DEFAULT 0,
                address TEXT DEFAULT '',
                remarks TEXT DEFAULT ''
            )
        """.trimIndent())

        // 2. جدول مجموعات/فئات الأصناف
        db.execSQL("""
            CREATE TABLE item_type (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                remarks TEXT DEFAULT ''
            )
        """.trimIndent())

        // 3. جدول الأصناف والمخزون
        db.execSQL("""
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                item_type_id INTEGER DEFAULT 1,
                barcode TEXT DEFAULT '',
                price REAL DEFAULT 0.0,
                cost REAL DEFAULT 0.0,
                qty REAL DEFAULT 0.0,
                remarks TEXT DEFAULT '',
                is_active INTEGER DEFAULT 1
            )
        """.trimIndent())

        // 4. جدول الفواتير
        db.execSQL("""
            CREATE TABLE bills (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bill_no TEXT,
                date_ TEXT,
                tr_type INTEGER,
                bill_type INTEGER,
                cus_id INTEGER,
                amount REAL DEFAULT 0.0,
                discount REAL DEFAULT 0.0,
                tax_amount REAL DEFAULT 0.0,
                net_amount REAL DEFAULT 0.0,
                remarks TEXT DEFAULT ''
            )
        """.trimIndent())

        // 5. جدول تفاصيل الفواتير
        db.execSQL("""
            CREATE TABLE bill_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                bill_id INTEGER,
                item_id INTEGER,
                qty REAL DEFAULT 1.0,
                cost_price REAL DEFAULT 0.0,
                sls_u_price REAL DEFAULT 0.0,
                discount REAL DEFAULT 0.0,
                total REAL DEFAULT 0.0,
                remark TEXT DEFAULT '',
                FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // 6. جدول القيود والعمليات المالية اليومية
        db.execSQL("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cus_id INTEGER,
                in_amount REAL DEFAULT 0.0,
                out_amount REAL DEFAULT 0.0,
                date_ TEXT,
                remarks TEXT DEFAULT '',
                bill_id INTEGER DEFAULT 0,
                tr_type TEXT DEFAULT ''
            )
        """.trimIndent())

        // 7. جدول الإعدادات
        db.execSQL("""
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT
            )
        """.trimIndent())

        // 8. جدول سجل المزامنة السحابية
        db.execSQL("""
            CREATE TABLE sync_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sync_time TEXT,
                status TEXT,
                message TEXT,
                items_synced INTEGER DEFAULT 0
            )
        """.trimIndent())

        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Upgrade logic if schema changes
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        // الحسابات الافتراضية
        db.execSQL("INSERT INTO customers (id, name, cus_type_id, remarks) VALUES (1, 'الصندوق الرئيسي (النقدية)', 4, 'حساب الصندوق النقدي الأساسي')")
        db.execSQL("INSERT INTO customers (id, name, cus_type_id, remarks) VALUES (2, 'عميل نقدي عام', 0, 'مبيعات نقدية للعملاء العاديين')")
        db.execSQL("INSERT INTO customers (id, name, cus_type_id, remarks) VALUES (3, 'مورد نقدي عام', 1, 'مشتريات نقدية عامة')")
        db.execSQL("INSERT INTO customers (id, name, cus_type_id, remarks) VALUES (4, 'مصروفات عامة وإدارية', 5, 'حساب المصروفات العامة')")

        // فئات الأصناف الافتراضية
        db.execSQL("INSERT INTO item_type (id, name, remarks) VALUES (1, 'عام', 'الفئة العامة للأصناف')")
        db.execSQL("INSERT INTO item_type (id, name, remarks) VALUES (2, 'مواد استهلاكية', 'بضائع ومواد استهلاكية')")
        db.execSQL("INSERT INTO item_type (id, name, remarks) VALUES (3, 'خدمات', 'خدمات مقدمة للعملاء')")

        // أصناف تجريبية
        db.execSQL("INSERT INTO items (name, item_type_id, barcode, price, cost, qty, remarks) VALUES ('خدمة استشارة محاسبية', 3, '1001', 150.0, 50.0, 100.0, 'خدمة')")
        db.execSQL("INSERT INTO items (name, item_type_id, barcode, price, cost, qty, remarks) VALUES ('صنف تجريبي 1', 1, '2001', 25.0, 15.0, 50.0, 'صنف افتراضي')")

        // إعدادات المتجر الافتراضية
        saveSettingInternal(db, "store_name", "سماء المحاسبين الشامل")
        saveSettingInternal(db, "store_phone", "0500000000")
        saveSettingInternal(db, "store_address", "المملكة العربية السعودية")
        saveSettingInternal(db, "tax_number", "300000000000003")
        saveSettingInternal(db, "tax_rate", "15.0")
        saveSettingInternal(db, "currency", "ر.س")
        saveSettingInternal(db, "invoice_footer", "شكراً لتعاملكم معنا")
    }

    private fun saveSettingInternal(db: SQLiteDatabase, key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ==========================================
    // إدارة العملاء والموردين والحسابات
    // ==========================================

    fun insertCustomer(customer: Customer): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", customer.name)
            put("gsm", customer.gsm)
            put("cus_type_id", customer.cusTypeId)
            put("address", customer.address)
            put("remarks", customer.remarks)
        }
        return db.insert("customers", null, cv)
    }

    fun updateCustomer(customer: Customer): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", customer.name)
            put("gsm", customer.gsm)
            put("cus_type_id", customer.cusTypeId)
            put("address", customer.address)
            put("remarks", customer.remarks)
        }
        return db.update("customers", cv, "id = ?", arrayOf(customer.id.toString())) > 0
    }

    fun deleteCustomer(id: Long): Boolean {
        val db = writableDatabase
        // منع حذف الصندوق الرئيسي (id=1)
        if (id == 1L) return false
        return db.delete("customers", "id = ?", arrayOf(id.toString())) > 0
    }

    fun getCustomer(id: Long): Customer? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, name, gsm, cus_type_id, address, remarks FROM customers WHERE id = ?", arrayOf(id.toString()))
        return if (cursor.moveToFirst()) {
            val c = cursorToCustomer(cursor)
            cursor.close()
            c.balance = getCustomerBalance(c.id)
            c
        } else {
            cursor.close()
            null
        }
    }

    fun getAllCustomers(cusTypeId: Int? = null): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = readableDatabase
        val query = if (cusTypeId != null) {
            "SELECT id, name, gsm, cus_type_id, address, remarks FROM customers WHERE cus_type_id = ? ORDER BY name ASC"
        } else {
            "SELECT id, name, gsm, cus_type_id, address, remarks FROM customers ORDER BY name ASC"
        }
        val args = if (cusTypeId != null) arrayOf(cusTypeId.toString()) else null
        val cursor = db.rawQuery(query, args)
        if (cursor.moveToFirst()) {
            do {
                val c = cursorToCustomer(cursor)
                c.balance = getCustomerBalance(c.id)
                list.add(c)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun searchCustomers(keyword: String, cusTypeId: Int? = null): List<Customer> {
        val list = mutableListOf<Customer>()
        val db = readableDatabase
        val query = if (cusTypeId != null) {
            "SELECT id, name, gsm, cus_type_id, address, remarks FROM customers WHERE cus_type_id = ? AND (name LIKE ? OR gsm LIKE ?) ORDER BY name ASC"
        } else {
            "SELECT id, name, gsm, cus_type_id, address, remarks FROM customers WHERE name LIKE ? OR gsm LIKE ? ORDER BY name ASC"
        }
        val likeStr = "%$keyword%"
        val args = if (cusTypeId != null) arrayOf(cusTypeId.toString(), likeStr, likeStr) else arrayOf(likeStr, likeStr)
        val cursor = db.rawQuery(query, args)
        if (cursor.moveToFirst()) {
            do {
                val c = cursorToCustomer(cursor)
                c.balance = getCustomerBalance(c.id)
                list.add(c)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getCustomerBalance(cusId: Long): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(in_amount), SUM(out_amount) FROM transactions WHERE cus_id = ?", arrayOf(cusId.toString()))
        var balance = 0.0
        if (cursor.moveToFirst()) {
            val totalIn = cursor.getDouble(0)
            val totalOut = cursor.getDouble(1)
            balance = totalIn - totalOut
        }
        cursor.close()
        return balance
    }

    private fun cursorToCustomer(cursor: Cursor): Customer {
        return Customer(
            id = cursor.getLong(0),
            name = cursor.getString(1),
            gsm = cursor.getString(2) ?: "",
            cusTypeId = cursor.getInt(3),
            address = cursor.getString(4) ?: "",
            remarks = cursor.getString(5) ?: ""
        )
    }

    // ==========================================
    // إدارة الأصناف والمخزون
    // ==========================================

    fun insertItem(item: Item): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", item.name)
            put("item_type_id", item.itemTypeId)
            put("barcode", item.barcode)
            put("price", item.price)
            put("cost", item.cost)
            put("qty", item.qty)
            put("remarks", item.remarks)
            put("is_active", if (item.isActive) 1 else 0)
        }
        return db.insert("items", null, cv)
    }

    fun updateItem(item: Item): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", item.name)
            put("item_type_id", item.itemTypeId)
            put("barcode", item.barcode)
            put("price", item.price)
            put("cost", item.cost)
            put("qty", item.qty)
            put("remarks", item.remarks)
            put("is_active", if (item.isActive) 1 else 0)
        }
        return db.update("items", cv, "id = ?", arrayOf(item.id.toString())) > 0
    }

    fun deleteItem(id: Long): Boolean {
        val db = writableDatabase
        return db.delete("items", "id = ?", arrayOf(id.toString())) > 0
    }

    fun getItem(id: Long): Item? {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT i.id, i.name, i.item_type_id, COALESCE(t.name, ''), i.barcode, i.price, i.cost, i.qty, i.remarks, i.is_active
            FROM items i
            LEFT JOIN item_type t ON i.item_type_id = t.id
            WHERE i.id = ?
        """.trimIndent(), arrayOf(id.toString()))
        return if (cursor.moveToFirst()) {
            val item = cursorToItem(cursor)
            cursor.close()
            item
        } else {
            cursor.close()
            null
        }
    }

    fun findItemByBarcode(barcode: String): Item? {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT i.id, i.name, i.item_type_id, COALESCE(t.name, ''), i.barcode, i.price, i.cost, i.qty, i.remarks, i.is_active
            FROM items i
            LEFT JOIN item_type t ON i.item_type_id = t.id
            WHERE i.barcode = ? AND i.is_active = 1
        """.trimIndent(), arrayOf(barcode))
        return if (cursor.moveToFirst()) {
            val item = cursorToItem(cursor)
            cursor.close()
            item
        } else {
            cursor.close()
            null
        }
    }

    fun getAllItems(): List<Item> {
        val list = mutableListOf<Item>()
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT i.id, i.name, i.item_type_id, COALESCE(t.name, ''), i.barcode, i.price, i.cost, i.qty, i.remarks, i.is_active
            FROM items i
            LEFT JOIN item_type t ON i.item_type_id = t.id
            ORDER BY i.name ASC
        """.trimIndent(), null)
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToItem(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun searchItems(query: String): List<Item> {
        val list = mutableListOf<Item>()
        val db = readableDatabase
        val like = "%$query%"
        val cursor = db.rawQuery("""
            SELECT i.id, i.name, i.item_type_id, COALESCE(t.name, ''), i.barcode, i.price, i.cost, i.qty, i.remarks, i.is_active
            FROM items i
            LEFT JOIN item_type t ON i.item_type_id = t.id
            WHERE i.name LIKE ? OR i.barcode LIKE ?
            ORDER BY i.name ASC
        """.trimIndent(), arrayOf(like, like))
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToItem(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getLowStockItems(threshold: Double = 5.0): List<Item> {
        val list = mutableListOf<Item>()
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT i.id, i.name, i.item_type_id, COALESCE(t.name, ''), i.barcode, i.price, i.cost, i.qty, i.remarks, i.is_active
            FROM items i
            LEFT JOIN item_type t ON i.item_type_id = t.id
            WHERE i.qty <= ? AND i.is_active = 1
            ORDER BY i.qty ASC
        """.trimIndent(), arrayOf(threshold.toString()))
        if (cursor.moveToFirst()) {
            do {
                list.add(cursorToItem(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    private fun updateItemStockInternal(db: SQLiteDatabase, itemId: Long, delta: Double) {
        db.execSQL("UPDATE items SET qty = qty + ? WHERE id = ?", arrayOf(delta, itemId))
    }

    private fun cursorToItem(cursor: Cursor): Item {
        return Item(
            id = cursor.getLong(0),
            name = cursor.getString(1),
            itemTypeId = cursor.getLong(2),
            typeName = cursor.getString(3) ?: "",
            barcode = cursor.getString(4) ?: "",
            price = cursor.getDouble(5),
            cost = cursor.getDouble(6),
            qty = cursor.getDouble(7),
            remarks = cursor.getString(8) ?: "",
            isActive = cursor.getInt(9) == 1
        )
    }

    // ==========================================
    // فئات الأصناف
    // ==========================================

    fun getAllItemTypes(): List<ItemType> {
        val list = mutableListOf<ItemType>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, name, remarks FROM item_type ORDER BY name ASC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(ItemType(cursor.getLong(0), cursor.getString(1), cursor.getString(2) ?: ""))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun insertItemType(type: ItemType): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", type.name)
            put("remarks", type.remarks)
        }
        return db.insert("item_type", null, cv)
    }

    // ==========================================
    // إدارة الفواتير والعمليات المحاسبية
    // ==========================================

    fun generateNextBillNo(trType: Int): String {
        val prefix = when (trType) {
            1 -> "INV"
            2 -> "PUR"
            3 -> "RSAL"
            4 -> "RPUR"
            else -> "BILL"
        }
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM bills WHERE tr_type = ?", arrayOf(trType.toString()))
        val count = if (cursor.moveToFirst()) cursor.getInt(0) + 1 else 1
        cursor.close()
        val dateStr = SimpleDateFormat("yyMM", Locale.ENGLISH).format(Date())
        return "$prefix-$dateStr-${String.format(Locale.ENGLISH, "%04d", count)}"
    }

    fun insertBill(bill: Bill): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val billNo = if (bill.billNo.isBlank()) generateNextBillNo(bill.trType) else bill.billNo
            val date = if (bill.date.isBlank()) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date()) else bill.date

            val cv = ContentValues().apply {
                put("bill_no", billNo)
                put("date_", date)
                put("tr_type", bill.trType)
                put("bill_type", bill.billType)
                put("cus_id", bill.cusId)
                put("amount", bill.amount)
                put("discount", bill.discount)
                put("tax_amount", bill.taxAmount)
                put("net_amount", bill.netAmount)
                put("remarks", bill.remarks)
            }
            val billId = db.insert("bills", null, cv)
            if (billId <= 0) {
                db.endTransaction()
                return -1
            }

            // إدخال تفاصيل الأصناف وتحديث المخزون
            for (item in bill.items) {
                val itemCv = ContentValues().apply {
                    put("bill_id", billId)
                    put("item_id", item.itemId)
                    put("qty", item.qty)
                    put("cost_price", item.cost)
                    put("sls_u_price", item.price)
                    put("discount", item.discount)
                    put("total", item.total)
                    put("remark", item.remark)
                }
                db.insert("bill_transactions", null, itemCv)

                // تحديث رصيد المخزون
                val stockDelta = when (bill.trType) {
                    1 -> -item.qty // بيع: ينقص المخزون
                    2 -> item.qty  // شراء: يزيد المخزون
                    3 -> item.qty  // مردود بيع: يزيد المخزون
                    4 -> -item.qty // مردود شراء: ينقص المخزون
                    else -> 0.0
                }
                if (stockDelta != 0.0) {
                    updateItemStockInternal(db, item.itemId, stockDelta)
                }
            }

            // إدخال القيود المحاسبية التلقائية
            recordBillAccountingEntry(db, billId, bill.copy(id = billId, billNo = billNo, date = date))

            db.setTransactionSuccessful()
            return billId
        } finally {
            db.endTransaction()
        }
    }

    private fun recordBillAccountingEntry(db: SQLiteDatabase, billId: Long, bill: Bill) {
        val net = bill.netAmount
        val note = "${bill.trTypeName} رقم ${bill.billNo}"
        val date = bill.date

        // الصندوق الرئيسي id = 1
        when (bill.trType) {
            1 -> { // مبيعات
                if (bill.billType == 1) { // نقدي
                    // الصندوق مدين (قبض نقدية)
                    insertTransactionInternal(db, 1L, inAmount = net, outAmount = 0.0, date = date, remarks = "$note (نقداً)", billId = billId, trType = "فاتورة مبيعات")
                } else { // آجل
                    // العميل مدين
                    insertTransactionInternal(db, bill.cusId, inAmount = net, outAmount = 0.0, date = date, remarks = "$note (آجل)", billId = billId, trType = "فاتورة مبيعات")
                }
            }
            2 -> { // مشتريات
                if (bill.billType == 1) { // نقدي
                    // الصندوق دائن (صرف نقدية)
                    insertTransactionInternal(db, 1L, inAmount = 0.0, outAmount = net, date = date, remarks = "$note (نقداً)", billId = billId, trType = "فاتورة مشتريات")
                } else { // آجل
                    // المورد دائن
                    insertTransactionInternal(db, bill.cusId, inAmount = 0.0, outAmount = net, date = date, remarks = "$note (آجل)", billId = billId, trType = "فاتورة مشتريات")
                }
            }
            3 -> { // مردود مبيعات
                if (bill.billType == 1) {
                    insertTransactionInternal(db, 1L, inAmount = 0.0, outAmount = net, date = date, remarks = "$note (استرجاع نقدي)", billId = billId, trType = "مردود مبيعات")
                } else {
                    insertTransactionInternal(db, bill.cusId, inAmount = 0.0, outAmount = net, date = date, remarks = "$note (إشعار دائن)", billId = billId, trType = "مردود مبيعات")
                }
            }
            4 -> { // مردود مشتريات
                if (bill.billType == 1) {
                    insertTransactionInternal(db, 1L, inAmount = net, outAmount = 0.0, date = date, remarks = "$note (استرداد نقدي)", billId = billId, trType = "مردود مشتريات")
                } else {
                    insertTransactionInternal(db, bill.cusId, inAmount = net, outAmount = 0.0, date = date, remarks = "$note (إشعار مدين)", billId = billId, trType = "مردود مشتريات")
                }
            }
        }
    }

    private fun insertTransactionInternal(db: SQLiteDatabase, cusId: Long, inAmount: Double, outAmount: Double, date: String, remarks: String, billId: Long, trType: String) {
        val cv = ContentValues().apply {
            put("cus_id", cusId)
            put("in_amount", inAmount)
            put("out_amount", outAmount)
            put("date_", date)
            put("remarks", remarks)
            put("bill_id", billId)
            put("tr_type", trType)
        }
        db.insert("transactions", null, cv)
    }

    fun deleteBill(billId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val bill = getBill(billId) ?: return false

            // عكس المخزون
            for (item in bill.items) {
                val stockDelta = when (bill.trType) {
                    1 -> item.qty   // نعيد الكميات المباعة للمخزن
                    2 -> -item.qty  // نخصم الكميات المشتراة من المخزن
                    3 -> -item.qty  // نخصم المردودات
                    4 -> item.qty
                    else -> 0.0
                }
                if (stockDelta != 0.0) {
                    updateItemStockInternal(db, item.itemId, stockDelta)
                }
            }

            // حذف القيود المالية المرتبطة بالفاتورة
            db.delete("transactions", "bill_id = ?", arrayOf(billId.toString()))
            // حذف تفاصيل الفاتورة
            db.delete("bill_transactions", "bill_id = ?", arrayOf(billId.toString()))
            // حذف رأس الفاتورة
            db.delete("bills", "id = ?", arrayOf(billId.toString()))

            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun getBill(id: Long): Bill? {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT b.id, b.bill_no, b.date_, b.tr_type, b.bill_type, b.cus_id, COALESCE(c.name, ''), b.amount, b.discount, b.tax_amount, b.net_amount, b.remarks
            FROM bills b
            LEFT JOIN customers c ON b.cus_id = c.id
            WHERE b.id = ?
        """.trimIndent(), arrayOf(id.toString()))

        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }

        val bill = Bill(
            id = cursor.getLong(0),
            billNo = cursor.getString(1) ?: "",
            date = cursor.getString(2) ?: "",
            trType = cursor.getInt(3),
            billType = cursor.getInt(4),
            cusId = cursor.getLong(5),
            cusName = cursor.getString(6) ?: "",
            amount = cursor.getDouble(7),
            discount = cursor.getDouble(8),
            taxAmount = cursor.getDouble(9),
            netAmount = cursor.getDouble(10),
            remarks = cursor.getString(11) ?: ""
        )
        cursor.close()

        // جلب تفاصيل الأصناف
        val itemCursor = db.rawQuery("""
            SELECT bt.id, bt.bill_id, bt.item_id, COALESCE(i.name, ''), bt.qty, bt.sls_u_price, bt.cost_price, bt.discount, bt.total, bt.remark
            FROM bill_transactions bt
            LEFT JOIN items i ON bt.item_id = i.id
            WHERE bt.bill_id = ?
        """.trimIndent(), arrayOf(id.toString()))

        if (itemCursor.moveToFirst()) {
            do {
                bill.items.add(BillItem(
                    id = itemCursor.getLong(0),
                    billId = itemCursor.getLong(1),
                    itemId = itemCursor.getLong(2),
                    itemName = itemCursor.getString(3) ?: "",
                    qty = itemCursor.getDouble(4),
                    price = itemCursor.getDouble(5),
                    cost = itemCursor.getDouble(6),
                    discount = itemCursor.getDouble(7),
                    total = itemCursor.getDouble(8),
                    remark = itemCursor.getString(9) ?: ""
                ))
            } while (itemCursor.moveToNext())
        }
        itemCursor.close()

        return bill
    }

    fun getAllBills(trType: Int? = null): List<Bill> {
        val list = mutableListOf<Bill>()
        val db = readableDatabase
        val query = if (trType != null) {
            """
                SELECT b.id, b.bill_no, b.date_, b.tr_type, b.bill_type, b.cus_id, COALESCE(c.name, ''), b.amount, b.discount, b.tax_amount, b.net_amount, b.remarks
                FROM bills b
                LEFT JOIN customers c ON b.cus_id = c.id
                WHERE b.tr_type = ?
                ORDER BY b.id DESC
            """.trimIndent()
        } else {
            """
                SELECT b.id, b.bill_no, b.date_, b.tr_type, b.bill_type, b.cus_id, COALESCE(c.name, ''), b.amount, b.discount, b.tax_amount, b.net_amount, b.remarks
                FROM bills b
                LEFT JOIN customers c ON b.cus_id = c.id
                ORDER BY b.id DESC
            """.trimIndent()
        }
        val args = if (trType != null) arrayOf(trType.toString()) else null
        val cursor = db.rawQuery(query, args)
        if (cursor.moveToFirst()) {
            do {
                list.add(Bill(
                    id = cursor.getLong(0),
                    billNo = cursor.getString(1) ?: "",
                    date = cursor.getString(2) ?: "",
                    trType = cursor.getInt(3),
                    billType = cursor.getInt(4),
                    cusId = cursor.getLong(5),
                    cusName = cursor.getString(6) ?: "",
                    amount = cursor.getDouble(7),
                    discount = cursor.getDouble(8),
                    taxAmount = cursor.getDouble(9),
                    netAmount = cursor.getDouble(10),
                    remarks = cursor.getString(11) ?: ""
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // ==========================================
    // السندات وحركات الصندوق والقيود اليومية
    // ==========================================

    fun insertTransaction(tx: Transaction): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val date = if (tx.date.isBlank()) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date()) else tx.date

            // القيد الأساسي للحساب
            val cv = ContentValues().apply {
                put("cus_id", tx.cusId)
                put("in_amount", tx.inAmount)
                put("out_amount", tx.outAmount)
                put("date_", date)
                put("remarks", tx.remarks)
                put("bill_id", tx.billId)
                put("tr_type", tx.trType)
            }
            val id = db.insert("transactions", null, cv)

            // القيد المقابل في الصندوق الرئيسي (id=1)
            // إذا كان سند قبض من عميل: العميل دائن (outAmount) والصندوق مدين (inAmount)
            // إذا كان سند صرف لمورد: المورد مدين (inAmount) والصندوق دائن (outAmount)
            if (tx.cusId != 1L) {
                val cashIn = tx.outAmount
                val cashOut = tx.inAmount
                val cashNote = "${tx.trType} - الحساب: ${tx.cusName} - ${tx.remarks}"
                val cashCv = ContentValues().apply {
                    put("cus_id", 1L)
                    put("in_amount", cashIn)
                    put("out_amount", cashOut)
                    put("date_", date)
                    put("remarks", cashNote)
                    put("bill_id", tx.billId)
                    put("tr_type", tx.trType)
                }
                db.insert("transactions", null, cashCv)
            }

            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun getAllTransactions(): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT t.id, t.cus_id, COALESCE(c.name, ''), t.in_amount, t.out_amount, t.date_, t.remarks, t.bill_id, t.tr_type
            FROM transactions t
            LEFT JOIN customers c ON t.cus_id = c.id
            ORDER BY t.id DESC
        """.trimIndent(), null)
        if (cursor.moveToFirst()) {
            do {
                list.add(Transaction(
                    id = cursor.getLong(0),
                    cusId = cursor.getLong(1),
                    cusName = cursor.getString(2) ?: "",
                    inAmount = cursor.getDouble(3),
                    outAmount = cursor.getDouble(4),
                    date = cursor.getString(5) ?: "",
                    remarks = cursor.getString(6) ?: "",
                    billId = cursor.getLong(7),
                    trType = cursor.getString(8) ?: ""
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun getCustomerStatement(cusId: Long, startDate: String = "", endDate: String = ""): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = readableDatabase
        val query = StringBuilder("""
            SELECT t.id, t.cus_id, COALESCE(c.name, ''), t.in_amount, t.out_amount, t.date_, t.remarks, t.bill_id, t.tr_type
            FROM transactions t
            LEFT JOIN customers c ON t.cus_id = c.id
            WHERE t.cus_id = ?
        """.trimIndent())

        val args = mutableListOf(cusId.toString())
        if (startDate.isNotBlank()) {
            query.append(" AND t.date_ >= ?")
            args.add(startDate)
        }
        if (endDate.isNotBlank()) {
            query.append(" AND t.date_ <= ?")
            args.add(endDate + " 23:59:59")
        }
        query.append(" ORDER BY t.date_ ASC, t.id ASC")

        val cursor = db.rawQuery(query.toString(), args.toTypedArray())
        if (cursor.moveToFirst()) {
            do {
                list.add(Transaction(
                    id = cursor.getLong(0),
                    cusId = cursor.getLong(1),
                    cusName = cursor.getString(2) ?: "",
                    inAmount = cursor.getDouble(3),
                    outAmount = cursor.getDouble(4),
                    date = cursor.getString(5) ?: "",
                    remarks = cursor.getString(6) ?: "",
                    billId = cursor.getLong(7),
                    trType = cursor.getString(8) ?: ""
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // ==========================================
    // الإحصائيات ولوحة التحكم Dashboard
    // ==========================================

    fun getDashboardStats(): DashboardStats {
        val db = readableDatabase
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

        // 1. إجمالي المبيعات
        var totalSales = 0.0
        var todaySales = 0.0
        var todayInvoicesCount = 0
        val salesCursor = db.rawQuery("SELECT SUM(net_amount), date_ FROM bills WHERE tr_type = 1 GROUP BY date_ LIKE ?", arrayOf("$todayStr%"))
        // query for today specifically
        val todaySalesCursor = db.rawQuery("SELECT COUNT(*), SUM(net_amount) FROM bills WHERE tr_type = 1 AND date_ LIKE ?", arrayOf("$todayStr%"))
        if (todaySalesCursor.moveToFirst()) {
            todayInvoicesCount = todaySalesCursor.getInt(0)
            todaySales = todaySalesCursor.getDouble(1)
        }
        todaySalesCursor.close()

        val allSalesCursor = db.rawQuery("SELECT SUM(net_amount) FROM bills WHERE tr_type = 1", null)
        if (allSalesCursor.moveToFirst()) {
            totalSales = allSalesCursor.getDouble(0)
        }
        allSalesCursor.close()

        // 2. إجمالي المشتريات
        var totalPurchases = 0.0
        val purchasesCursor = db.rawQuery("SELECT SUM(net_amount) FROM bills WHERE tr_type = 2", null)
        if (purchasesCursor.moveToFirst()) {
            totalPurchases = purchasesCursor.getDouble(0)
        }
        purchasesCursor.close()

        // 3. رصيد الصندوق النقدي (id=1)
        val cashBalance = getCustomerBalance(1L)

        // 4. ديون العملاء (cus_type_id = 0, حيث الرصيد > 0)
        var receivables = 0.0
        val cusList = getAllCustomers(cusTypeId = 0)
        for (c in cusList) {
            if (c.balance > 0) receivables += c.balance
        }

        // 5. مستحقات الموردين (cus_type_id = 1, حيث الرصيد < 0)
        var payables = 0.0
        val suppList = getAllCustomers(cusTypeId = 1)
        for (s in suppList) {
            if (s.balance < 0) payables += kotlin.math.abs(s.balance)
        }

        // 6. أصناف شحيحة بالمخزون
        var lowStock = 0
        val lowCursor = db.rawQuery("SELECT COUNT(*) FROM items WHERE qty <= 5 AND is_active = 1", null)
        if (lowCursor.moveToFirst()) {
            lowStock = lowCursor.getInt(0)
        }
        lowCursor.close()

        return DashboardStats(
            totalSales = totalSales,
            totalPurchases = totalPurchases,
            cashBalance = cashBalance,
            totalReceivables = receivables,
            totalPayables = payables,
            todaySales = todaySales,
            todayInvoicesCount = todayInvoicesCount,
            lowStockCount = lowStock
        )
    }

    // ==========================================
    // التقارير المالية
    // ==========================================

    fun getProfitAndLoss(startDate: String = "", endDate: String = ""): Triple<Double, Double, Double> {
        val db = readableDatabase
        val args = mutableListOf<String>()
        val dateFilterBills = StringBuilder()
        val dateFilterExp = StringBuilder()

        if (startDate.isNotBlank()) {
            dateFilterBills.append(" AND b.date_ >= ?")
            dateFilterExp.append(" AND t.date_ >= ?")
            args.add(startDate)
        }
        if (endDate.isNotBlank()) {
            dateFilterBills.append(" AND b.date_ <= ?")
            dateFilterExp.append(" AND t.date_ <= ?")
            args.add(endDate + " 23:59:59")
        }

        // المبيعات وتكلفة البضاعة المباعة
        val query = """
            SELECT SUM(bt.total), SUM(bt.cost_price * bt.qty)
            FROM bill_transactions bt
            JOIN bills b ON bt.bill_id = b.id
            WHERE b.tr_type = 1 $dateFilterBills
        """.trimIndent()

        var totalRevenue = 0.0
        var totalCost = 0.0

        val cursor = db.rawQuery(query, if (args.isNotEmpty()) args.toTypedArray() else null)
        if (cursor.moveToFirst()) {
            totalRevenue = cursor.getDouble(0)
            totalCost = cursor.getDouble(1)
        }
        cursor.close()

        // المصروفات من جدول الحركات المرتبطة بحسابات المصروفات (cus_type_id = 5)
        val expQuery = """
            SELECT SUM(t.in_amount)
            FROM transactions t
            JOIN customers c ON t.cus_id = c.id
            WHERE c.cus_type_id = 5 $dateFilterExp
        """.trimIndent()

        var totalExpenses = 0.0
        val expCursor = db.rawQuery(expQuery, if (args.isNotEmpty()) args.toTypedArray() else null)
        if (expCursor.moveToFirst()) {
            totalExpenses = expCursor.getDouble(0)
        }
        expCursor.close()

        val netProfit = totalRevenue - totalCost - totalExpenses
        return Triple(totalRevenue, totalCost + totalExpenses, netProfit)
    }

    fun getInventoryValuation(): Pair<Double, Double> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(qty * cost), SUM(qty * price) FROM items WHERE is_active = 1", null)
        var totalCost = 0.0
        var totalSaleValue = 0.0
        if (cursor.moveToFirst()) {
            totalCost = cursor.getDouble(0)
            totalSaleValue = cursor.getDouble(1)
        }
        cursor.close()
        return Pair(totalCost, totalSaleValue)
    }

    // ==========================================
    // إدارة الإعدادات
    // ==========================================

    fun getSetting(key: String, default: String = ""): String {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key))
        val value = if (cursor.moveToFirst()) cursor.getString(0) ?: default else default
        cursor.close()
        return value
    }

    fun saveSetting(key: String, value: String) {
        val db = writableDatabase
        saveSettingInternal(db, key, value)
    }

    fun getStoreSettings(): StoreSettings {
        return StoreSettings(
            storeName = getSetting("store_name", "محاسب سوفت"),
            storePhone = getSetting("store_phone", ""),
            storeAddress = getSetting("store_address", ""),
            taxNumber = getSetting("tax_number", ""),
            taxRate = getSetting("tax_rate", "15.0").toDoubleOrNull() ?: 15.0,
            currency = getSetting("currency", "ر.س"),
            invoiceFooter = getSetting("invoice_footer", "شكراً لتعاملكم معنا"),
            cloudSyncUrl = getSetting("cloud_sync_url", ""),
            cloudSyncApiKey = getSetting("cloud_sync_api_key", "")
        )
    }

    fun saveStoreSettings(settings: StoreSettings) {
        saveSetting("store_name", settings.storeName)
        saveSetting("store_phone", settings.storePhone)
        saveSetting("store_address", settings.storeAddress)
        saveSetting("tax_number", settings.taxNumber)
        saveSetting("tax_rate", settings.taxRate.toString())
        saveSetting("currency", settings.currency)
        saveSetting("invoice_footer", settings.invoiceFooter)
        saveSetting("cloud_sync_url", settings.cloudSyncUrl)
        saveSetting("cloud_sync_api_key", settings.cloudSyncApiKey)
    }

    // ==========================================
    // سجل المزامنة السحابية
    // ==========================================

    fun insertSyncLog(status: String, message: String, itemsSynced: Int = 0): Long {
        val db = writableDatabase
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        val cv = ContentValues().apply {
            put("sync_time", timeStr)
            put("status", status)
            put("message", message)
            put("items_synced", itemsSynced)
        }
        return db.insert("sync_logs", null, cv)
    }

    fun getAllSyncLogs(): List<SyncLog> {
        val list = mutableListOf<SyncLog>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, sync_time, status, message, items_synced FROM sync_logs ORDER BY id DESC LIMIT 50", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(SyncLog(
                    id = cursor.getLong(0),
                    syncTime = cursor.getString(1) ?: "",
                    status = cursor.getString(2) ?: "",
                    message = cursor.getString(3) ?: "",
                    itemsSynced = cursor.getInt(4)
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    // ==========================================
    // التصدير والاستيراد للمزامنة السحابية
    // ==========================================

    fun exportAllData(): CloudSyncPayload {
        val settings = getStoreSettings()
        val customers = getAllCustomers()
        val itemTypes = getAllItemTypes()
        val items = getAllItems()
        val bills = mutableListOf<Bill>()
        for (b in getAllBills()) {
            val fullBill = getBill(b.id)
            if (fullBill != null) bills.add(fullBill)
        }
        val transactions = getAllTransactions()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())

        return CloudSyncPayload(
            storeSettings = settings,
            customers = customers,
            itemTypes = itemTypes,
            items = items,
            bills = bills,
            transactions = transactions,
            exportDate = now
        )
    }

    fun importAllData(payload: CloudSyncPayload) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            saveStoreSettings(payload.storeSettings)

            // استيراد الحسابات
            for (c in payload.customers) {
                val cv = ContentValues().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("gsm", c.gsm)
                    put("cus_type_id", c.cusTypeId)
                    put("address", c.address)
                    put("remarks", c.remarks)
                }
                db.insertWithOnConflict("customers", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            // استيراد فئات الأصناف
            for (t in payload.itemTypes) {
                val cv = ContentValues().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("remarks", t.remarks)
                }
                db.insertWithOnConflict("item_type", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            // استيراد الأصناف
            for (i in payload.items) {
                val cv = ContentValues().apply {
                    put("id", i.id)
                    put("name", i.name)
                    put("item_type_id", i.itemTypeId)
                    put("barcode", i.barcode)
                    put("price", i.price)
                    put("cost", i.cost)
                    put("qty", i.qty)
                    put("remarks", i.remarks)
                    put("is_active", if (i.isActive) 1 else 0)
                }
                db.insertWithOnConflict("items", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            // استيراد الفواتير وتفاصيلها
            for (b in payload.bills) {
                val cv = ContentValues().apply {
                    put("id", b.id)
                    put("bill_no", b.billNo)
                    put("date_", b.date)
                    put("tr_type", b.trType)
                    put("bill_type", b.billType)
                    put("cus_id", b.cusId)
                    put("amount", b.amount)
                    put("discount", b.discount)
                    put("tax_amount", b.taxAmount)
                    put("net_amount", b.netAmount)
                    put("remarks", b.remarks)
                }
                db.insertWithOnConflict("bills", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                for (bi in b.items) {
                    val itemCv = ContentValues().apply {
                        if (bi.id > 0) put("id", bi.id)
                        put("bill_id", b.id)
                        put("item_id", bi.itemId)
                        put("qty", bi.qty)
                        put("cost_price", bi.cost)
                        put("sls_u_price", bi.price)
                        put("discount", bi.discount)
                        put("total", bi.total)
                        put("remark", bi.remark)
                    }
                    db.insertWithOnConflict("bill_transactions", null, itemCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // استيراد القيود اليومية
            for (tx in payload.transactions) {
                val cv = ContentValues().apply {
                    put("id", tx.id)
                    put("cus_id", tx.cusId)
                    put("in_amount", tx.inAmount)
                    put("out_amount", tx.outAmount)
                    put("date_", tx.date)
                    put("remarks", tx.remarks)
                    put("bill_id", tx.billId)
                    put("tr_type", tx.trType)
                }
                db.insertWithOnConflict("transactions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
