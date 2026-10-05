package com.mohaseb.app.data.model

import java.io.Serializable

data class Customer(
    val id: Long = 0,
    val name: String,
    val gsm: String = "",
    val cusTypeId: Int = 0, // 0: عميل, 1: مورد, 4: صندوق, 5: مصروفات
    val address: String = "",
    val remarks: String = "",
    var balance: Double = 0.0
) : Serializable {
    val typeName: String
        get() = when (cusTypeId) {
            0 -> "عميل"
            1 -> "مورد"
            4 -> "صندوق"
            5 -> "مصروفات"
            else -> "حساب"
        }
}

data class ItemType(
    val id: Long = 0,
    val name: String,
    val remarks: String = ""
) : Serializable

data class Item(
    val id: Long = 0,
    val name: String,
    val itemTypeId: Long = 0,
    val typeName: String = "",
    val barcode: String = "",
    val price: Double = 0.0,       // سعر البيع
    val cost: Double = 0.0,        // سعر التكلفة/الشراء
    val qty: Double = 0.0,         // الكمية الحالية
    val remarks: String = "",
    val isActive: Boolean = true
) : Serializable

data class Bill(
    val id: Long = 0,
    val billNo: String = "",
    val date: String = "",
    val trType: Int = 1,          // 1: مبيعات, 2: مشتريات, 3: مردود مبيعات, 4: مردود مشتريات
    val billType: Int = 1,        // 1: نقدي, 2: آجل
    val cusId: Long = 0,
    var cusName: String = "",
    val amount: Double = 0.0,     // المجموع قبل الخصم والضريبة
    val discount: Double = 0.0,   // الخصم
    val taxAmount: Double = 0.0,  // الضريبة
    val netAmount: Double = 0.0,  // الصافي النهائي
    val remarks: String = "",
    val items: MutableList<BillItem> = mutableListOf()
) : Serializable {
    val trTypeName: String
        get() = when (trType) {
            1 -> "فاتورة مبيعات"
            2 -> "فاتورة مشتريات"
            3 -> "مردود مبيعات"
            4 -> "مردود مشتريات"
            else -> "فاتورة"
        }

    val billTypeName: String
        get() = if (billType == 1) "نقدي" else "آجل"
}

data class BillItem(
    val id: Long = 0,
    val billId: Long = 0,
    val itemId: Long,
    val itemName: String,
    val qty: Double,
    val price: Double,
    val cost: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = (qty * price) - discount,
    val remark: String = ""
) : Serializable

data class Transaction(
    val id: Long = 0,
    val cusId: Long = 0,
    val cusName: String = "",
    val inAmount: Double = 0.0,   // داخل / قبض / مدين
    val outAmount: Double = 0.0,  // خارج / صرف / دائن
    val date: String = "",
    val remarks: String = "",
    val billId: Long = 0,
    val trType: String = ""       // "سند قبض", "سند صرف", "فاتورة", "قيد"
) : Serializable

data class DashboardStats(
    val totalSales: Double = 0.0,
    val totalPurchases: Double = 0.0,
    val cashBalance: Double = 0.0,
    val totalReceivables: Double = 0.0, // ديون العملاء
    val totalPayables: Double = 0.0,    // مستحقات الموردين
    val todaySales: Double = 0.0,
    val todayInvoicesCount: Int = 0,
    val lowStockCount: Int = 0
) : Serializable

data class StoreSettings(
    var storeName: String = "سماء المحاسبين الشامل",
    var storePhone: String = "",
    var storeAddress: String = "",
    var taxNumber: String = "",
    var taxRate: Double = 15.0,
    var currency: String = "ر.س",
    var invoiceFooter: String = "شكراً لتعاملكم معنا",
    var cloudSyncUrl: String = "",
    var cloudSyncApiKey: String = ""
) : Serializable

data class SyncLog(
    val id: Long = 0,
    val syncTime: String,
    val status: String,
    val message: String,
    val itemsSynced: Int = 0
) : Serializable

data class CloudSyncPayload(
    val storeSettings: StoreSettings,
    val customers: List<Customer>,
    val itemTypes: List<ItemType>,
    val items: List<Item>,
    val bills: List<Bill>,
    val transactions: List<Transaction>,
    val exportDate: String,
    val appVersion: String = "1.0.0"
) : Serializable
