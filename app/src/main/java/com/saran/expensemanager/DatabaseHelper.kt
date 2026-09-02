package com.saran.expensemanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext = context.applicationContext

    companion object {
        private const val DATABASE_NAME = "expense_manager.db"
        private const val DATABASE_VERSION = 5

        // Expenses table
        private const val TABLE = "expenses"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_AMOUNT = "amount"
        private const val COL_CATEGORY = "category"
        private const val COL_DATE = "date"
        private const val COL_NOTES = "notes"
        const val COL_SYNC_STATUS = "sync_status"
        const val SYNC_SYNCED = "synced"
        const val SYNC_PENDING = "pending"
        const val SYNC_FAILED = "failed"
        const val COL_PAYMENT_METHOD = "payment_method"

        // Income table
        private const val INCOME_TABLE = "income"
        private const val INC_ID = "id"
        private const val INC_TITLE = "title"
        private const val INC_AMOUNT = "amount"
        private const val INC_SOURCE = "source"
        private const val INC_DATE = "date"
        private const val INC_NOTES = "notes"
        const val INC_TYPE = "type"
        const val INCOME_TYPE_INCOME = "income"
        const val INCOME_TYPE_TRANSFER = "transfer"

        // Recurring table
        private const val RECURRING_TABLE = "recurring"
        private const val REC_ID = "id"
        private const val REC_TITLE = "title"
        private const val REC_AMOUNT = "amount"
        private const val REC_CATEGORY = "category"
        private const val REC_DAY = "day_of_month"
        private const val REC_NOTES = "notes"
        const val REC_SUBSCRIPTION = "is_subscription"

        // Goals table
        private const val GOALS_TABLE = "goals"
        private const val GOAL_ID = "id"
        private const val GOAL_NAME = "name"
        private const val GOAL_TARGET = "target_amount"
        private const val GOAL_SAVED = "saved_amount"
        private const val GOAL_COLOR = "color"
        private const val GOAL_DEADLINE = "deadline"

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val createExpensesTable = """
        CREATE TABLE $TABLE (
            $COL_ID           INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_TITLE        TEXT    NOT NULL,
            $COL_AMOUNT       REAL    NOT NULL,
            $COL_CATEGORY     TEXT    NOT NULL,
            $COL_DATE         TEXT    NOT NULL,
            $COL_NOTES        TEXT    DEFAULT '',
            $COL_SYNC_STATUS  TEXT    DEFAULT '$SYNC_PENDING',
            $COL_PAYMENT_METHOD TEXT  DEFAULT ''
        )""".trimIndent()

    private val createIncomeTable = """
        CREATE TABLE $INCOME_TABLE (
            $INC_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
            $INC_TITLE  TEXT    NOT NULL,
            $INC_AMOUNT REAL    NOT NULL,
            $INC_SOURCE TEXT    NOT NULL,
            $INC_DATE   TEXT    NOT NULL,
            $INC_NOTES  TEXT    DEFAULT '',
            $INC_TYPE   TEXT    DEFAULT '$INCOME_TYPE_INCOME'
        )""".trimIndent()

    private val createRecurringTable = """
        CREATE TABLE $RECURRING_TABLE (
            $REC_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
            $REC_TITLE    TEXT    NOT NULL,
            $REC_AMOUNT   REAL    NOT NULL,
            $REC_CATEGORY TEXT    NOT NULL,
            $REC_DAY      INTEGER NOT NULL,
            $REC_NOTES    TEXT    DEFAULT '',
            $REC_SUBSCRIPTION INTEGER DEFAULT 0
        )""".trimIndent()

    private val createGoalsTable = """
        CREATE TABLE $GOALS_TABLE (
            $GOAL_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
            $GOAL_NAME     TEXT    NOT NULL,
            $GOAL_TARGET   REAL    NOT NULL,
            $GOAL_SAVED    REAL    DEFAULT 0,
            $GOAL_COLOR    TEXT    DEFAULT '#22C55E',
            $GOAL_DEADLINE TEXT    DEFAULT ''
        )""".trimIndent()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(createExpensesTable)
        db.execSQL(createIncomeTable)
        db.execSQL(createRecurringTable)
        db.execSQL(createGoalsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(createIncomeTable)
            db.execSQL(createRecurringTable)
        }
        if (oldVersion < 3) {
            db.execSQL(createGoalsTable)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_SYNC_STATUS TEXT DEFAULT '$SYNC_PENDING'")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_PAYMENT_METHOD TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE $INCOME_TABLE ADD COLUMN $INC_TYPE TEXT DEFAULT '$INCOME_TYPE_INCOME'")
            db.execSQL("ALTER TABLE $RECURRING_TABLE ADD COLUMN $REC_SUBSCRIPTION INTEGER DEFAULT 0")
        }
    }

    // ── Expense CRUD ─────────────────────────────────────────────────────────

    fun addExpense(expense: Expense): Long {
        val cv = ContentValues().apply {
            put(COL_TITLE, expense.title)
            put(COL_AMOUNT, expense.amount)
            put(COL_CATEGORY, expense.category)
            put(COL_DATE, expense.date)
            put(COL_NOTES, expense.notes)
            put(COL_PAYMENT_METHOD, expense.paymentMethod)
        }
        val id = writableDatabase.insert(TABLE, null, cv)
        if (id > 0) notifyWidgets()
        return id
    }

    /** Every write funnels through here, so the home-screen widget refreshes no matter the caller. */
    private fun notifyWidgets() = ExpenseWidgetProvider.updateAll(appContext)

    fun getAllExpenses(): List<Expense> {
        val list = mutableListOf<Expense>()
        readableDatabase.query(TABLE, null, null, null, null, null, "$COL_DATE DESC, $COL_ID DESC")
            .use { c -> while (c.moveToNext()) list += c.toExpense() }
        return list
    }

    /** Resets sync_status to pending — edited content needs to be re-pushed to Google Sheets. */
    fun updateExpense(expense: Expense): Int {
        val cv = ContentValues().apply {
            put(COL_TITLE, expense.title)
            put(COL_AMOUNT, expense.amount)
            put(COL_CATEGORY, expense.category)
            put(COL_DATE, expense.date)
            put(COL_NOTES, expense.notes)
            put(COL_PAYMENT_METHOD, expense.paymentMethod)
            put(COL_SYNC_STATUS, SYNC_PENDING)
        }
        val rows = writableDatabase.update(TABLE, cv, "$COL_ID = ?", arrayOf(expense.id.toString()))
        if (rows > 0) notifyWidgets()
        return rows
    }

    fun deleteExpense(id: Long): Int {
        val rows = writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
        if (rows > 0) notifyWidgets()
        return rows
    }

    fun getTodayTotal(): Double {
        val today = "%04d-%02d-%02d".format(
            Calendar.getInstance()[Calendar.YEAR],
            Calendar.getInstance()[Calendar.MONTH] + 1,
            Calendar.getInstance()[Calendar.DAY_OF_MONTH],
        )
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE WHERE $COL_DATE = ?", arrayOf(today)
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    // ── Google Sheets sync ───────────────────────────────────────────────────

    fun getPendingSyncExpenses(): List<Expense> {
        val list = mutableListOf<Expense>()
        readableDatabase.query(
            TABLE, null, "$COL_SYNC_STATUS != ?", arrayOf(SYNC_SYNCED), null, null,
            "$COL_DATE DESC, $COL_ID DESC"
        ).use { c -> while (c.moveToNext()) list += c.toExpense() }
        return list
    }

    fun setExpenseSyncStatus(id: Long, status: String) {
        writableDatabase.update(
            TABLE, ContentValues().apply { put(COL_SYNC_STATUS, status) },
            "$COL_ID = ?", arrayOf(id.toString())
        )
    }

    private fun android.database.Cursor.toExpense() = Expense(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        amount = getDouble(getColumnIndexOrThrow(COL_AMOUNT)),
        category = getString(getColumnIndexOrThrow(COL_CATEGORY)),
        date = getString(getColumnIndexOrThrow(COL_DATE)),
        notes = getString(getColumnIndexOrThrow(COL_NOTES)) ?: "",
        syncStatus = getString(getColumnIndexOrThrow(COL_SYNC_STATUS)) ?: SYNC_PENDING,
        paymentMethod = getString(getColumnIndexOrThrow(COL_PAYMENT_METHOD)) ?: "",
    )

    // ── Expense Queries ───────────────────────────────────────────────────────

    fun getTotalAmount(): Double {
        readableDatabase.rawQuery("SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE", null)
            .use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getTotalCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
            .use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getCurrentMonthTotal(): Double {
        val cal = Calendar.getInstance()
        val prefix = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE WHERE $COL_DATE LIKE ?",
            arrayOf("$prefix%")
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getLastMonthTotal(): Double {
        val cal = Calendar.getInstance().also { it.add(Calendar.MONTH, -1) }
        val prefix = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE WHERE $COL_DATE LIKE ?",
            arrayOf("$prefix%")
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getLastMonthCategoryTotals(): Map<String, Double> {
        val cal = Calendar.getInstance().also { it.add(Calendar.MONTH, -1) }
        val prefix = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        val map = LinkedHashMap<String, Double>()
        readableDatabase.rawQuery(
            "SELECT $COL_CATEGORY, SUM($COL_AMOUNT) FROM $TABLE WHERE $COL_DATE LIKE ? " +
                    "GROUP BY $COL_CATEGORY ORDER BY SUM($COL_AMOUNT) DESC",
            arrayOf("$prefix%")
        ).use { c -> while (c.moveToNext()) map[c.getString(0)] = c.getDouble(1) }
        return map
    }

    fun getCurrentMonthCategoryTotals(): Map<String, Double> {
        val cal = Calendar.getInstance()
        val prefix = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        val map = LinkedHashMap<String, Double>()
        readableDatabase.rawQuery(
            "SELECT $COL_CATEGORY, SUM($COL_AMOUNT) FROM $TABLE WHERE $COL_DATE LIKE ? " +
                    "GROUP BY $COL_CATEGORY ORDER BY SUM($COL_AMOUNT) DESC",
            arrayOf("$prefix%")
        ).use { c -> while (c.moveToNext()) map[c.getString(0)] = c.getDouble(1) }
        return map
    }

    fun getCategoryTotals(): Map<String, Double> {
        val map = LinkedHashMap<String, Double>()
        readableDatabase.rawQuery(
            "SELECT $COL_CATEGORY, SUM($COL_AMOUNT) FROM $TABLE " +
                    "GROUP BY $COL_CATEGORY ORDER BY SUM($COL_AMOUNT) DESC",
            null
        ).use { c -> while (c.moveToNext()) map[c.getString(0)] = c.getDouble(1) }
        return map
    }

    fun getWeekTotal(): Double {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val weekStart = "%04d-%02d-%02d".format(
            cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH]
        )
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE WHERE $COL_DATE >= ?",
            arrayOf(weekStart)
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    /** 12 entries (Jan..Dec) of that year's total spend, for a yearly report. */
    fun getMonthlyTotalsForYear(year: Int): List<Double> =
        (1..12).map { month ->
            val prefix = "%04d-%02d".format(year, month)
            readableDatabase.rawQuery(
                "SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE WHERE $COL_DATE LIKE ?", arrayOf("$prefix%")
            ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
        }

    fun getYearTotal(): Double {
        val year = "%04d".format(Calendar.getInstance()[Calendar.YEAR])
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE WHERE $COL_DATE LIKE ?",
            arrayOf("$year%")
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getAverageDailySpend(): Double {
        readableDatabase.rawQuery(
            "SELECT MIN($COL_DATE), MAX($COL_DATE), COALESCE(SUM($COL_AMOUNT), 0) FROM $TABLE", null
        ).use { c ->
            if (!c.moveToFirst()) return 0.0
            val minDate = c.getString(0) ?: return 0.0
            val maxDate = c.getString(1) ?: return 0.0
            val total = c.getDouble(2)
            if (minDate == maxDate) return total
            return try {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val diff = (fmt.parse(maxDate)!!.time - fmt.parse(minDate)!!.time) / 86_400_000L + 1
                if (diff > 0) total / diff else total
            } catch (_: Exception) { total }
        }
    }

    fun getRecentExpenses(limit: Int = 5): List<Expense> {
        val list = mutableListOf<Expense>()
        readableDatabase.query(
            TABLE, null, null, null, null, null,
            "$COL_DATE DESC, $COL_ID DESC", limit.toString()
        ).use { c -> while (c.moveToNext()) list += c.toExpense() }
        return list
    }

    fun hasExpenseForMonth(title: String, yearMonth: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE $COL_TITLE = ? AND $COL_DATE LIKE ?",
            arrayOf(title, "$yearMonth%")
        ).use { return it.moveToFirst() && it.getInt(0) > 0 }
    }

    // ── Income CRUD ───────────────────────────────────────────────────────────

    fun addIncome(income: Income): Long {
        val cv = ContentValues().apply {
            put(INC_TITLE, income.title)
            put(INC_AMOUNT, income.amount)
            put(INC_SOURCE, income.source)
            put(INC_DATE, income.date)
            put(INC_NOTES, income.notes)
            put(INC_TYPE, income.type)
        }
        return writableDatabase.insert(INCOME_TABLE, null, cv)
    }

    fun getAllIncome(): List<Income> {
        val list = mutableListOf<Income>()
        readableDatabase.query(
            INCOME_TABLE, null, null, null, null, null, "$INC_DATE DESC, $INC_ID DESC"
        ).use { c -> while (c.moveToNext()) list += c.toIncome() }
        return list
    }

    private fun android.database.Cursor.toIncome() = Income(
        id = getLong(getColumnIndexOrThrow(INC_ID)),
        title = getString(getColumnIndexOrThrow(INC_TITLE)),
        amount = getDouble(getColumnIndexOrThrow(INC_AMOUNT)),
        source = getString(getColumnIndexOrThrow(INC_SOURCE)),
        date = getString(getColumnIndexOrThrow(INC_DATE)),
        notes = getString(getColumnIndexOrThrow(INC_NOTES)) ?: "",
        type = getString(getColumnIndexOrThrow(INC_TYPE)) ?: INCOME_TYPE_INCOME,
    )

    fun deleteIncome(id: Long): Int =
        writableDatabase.delete(INCOME_TABLE, "$INC_ID = ?", arrayOf(id.toString()))

    fun updateIncome(income: Income): Int {
        val cv = ContentValues().apply {
            put(INC_TITLE, income.title)
            put(INC_AMOUNT, income.amount)
            put(INC_SOURCE, income.source)
            put(INC_DATE, income.date)
            put(INC_NOTES, income.notes)
            put(INC_TYPE, income.type)
        }
        return writableDatabase.update(INCOME_TABLE, cv, "$INC_ID = ?", arrayOf(income.id.toString()))
    }

    /** Excludes transfers — those move money between your own accounts, they aren't earnings. */
    fun getTotalIncome(): Double {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($INC_AMOUNT), 0) FROM $INCOME_TABLE WHERE $INC_TYPE = ?",
            arrayOf(INCOME_TYPE_INCOME)
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getLastMonthIncome(): Double {
        val cal = Calendar.getInstance().also { it.add(Calendar.MONTH, -1) }
        val prefix = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($INC_AMOUNT), 0) FROM $INCOME_TABLE WHERE $INC_DATE LIKE ? AND $INC_TYPE = ?",
            arrayOf("$prefix%", INCOME_TYPE_INCOME)
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getIncomeBySource(): Map<String, Double> {
        val map = LinkedHashMap<String, Double>()
        readableDatabase.rawQuery(
            "SELECT $INC_SOURCE, SUM($INC_AMOUNT) FROM $INCOME_TABLE " +
                    "GROUP BY $INC_SOURCE ORDER BY SUM($INC_AMOUNT) DESC",
            null
        ).use { c -> while (c.moveToNext()) map[c.getString(0)] = c.getDouble(1) }
        return map
    }

    fun getDailySpending(days: Int): List<Pair<String, Double>> {
        val today = Calendar.getInstance()
        val startCal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_MONTH, -(days - 1)) }
        val startDate = "%04d-%02d-%02d".format(
            startCal[Calendar.YEAR], startCal[Calendar.MONTH] + 1, startCal[Calendar.DAY_OF_MONTH]
        )

        // Single query to fetch all days at once
        val dbMap = mutableMapOf<String, Double>()
        readableDatabase.rawQuery(
            "SELECT $COL_DATE, SUM($COL_AMOUNT) FROM $TABLE WHERE $COL_DATE >= ? GROUP BY $COL_DATE",
            arrayOf(startDate)
        ).use { c ->
            while (c.moveToNext()) dbMap[c.getString(0)] = c.getDouble(1)
        }

        // Build full list filling 0 for days with no spending
        val result = mutableListOf<Pair<String, Double>>()
        for (i in days - 1 downTo 0) {
            val cal = Calendar.getInstance().also { it.time = today.time; it.add(Calendar.DAY_OF_MONTH, -i) }
            val dateStr = "%04d-%02d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
            result.add(dateStr to (dbMap[dateStr] ?: 0.0))
        }
        return result
    }

    fun getCurrentMonthIncome(): Double {
        val cal = Calendar.getInstance()
        val prefix = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM($INC_AMOUNT), 0) FROM $INCOME_TABLE WHERE $INC_DATE LIKE ? AND $INC_TYPE = ?",
            arrayOf("$prefix%", INCOME_TYPE_INCOME)
        ).use { return if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    // ── Recurring CRUD ────────────────────────────────────────────────────────

    fun addRecurring(recurring: Recurring): Long {
        val cv = ContentValues().apply {
            put(REC_TITLE, recurring.title)
            put(REC_AMOUNT, recurring.amount)
            put(REC_CATEGORY, recurring.category)
            put(REC_DAY, recurring.dayOfMonth)
            put(REC_NOTES, recurring.notes)
            put(REC_SUBSCRIPTION, if (recurring.isSubscription) 1 else 0)
        }
        return writableDatabase.insert(RECURRING_TABLE, null, cv)
    }

    fun getAllRecurring(): List<Recurring> {
        val list = mutableListOf<Recurring>()
        readableDatabase.query(
            RECURRING_TABLE, null, null, null, null, null, "$REC_TITLE ASC"
        ).use { c ->
            while (c.moveToNext()) {
                list += Recurring(
                    id = c.getLong(c.getColumnIndexOrThrow(REC_ID)),
                    title = c.getString(c.getColumnIndexOrThrow(REC_TITLE)),
                    amount = c.getDouble(c.getColumnIndexOrThrow(REC_AMOUNT)),
                    category = c.getString(c.getColumnIndexOrThrow(REC_CATEGORY)),
                    dayOfMonth = c.getInt(c.getColumnIndexOrThrow(REC_DAY)),
                    notes = c.getString(c.getColumnIndexOrThrow(REC_NOTES)) ?: "",
                    isSubscription = c.getInt(c.getColumnIndexOrThrow(REC_SUBSCRIPTION)) != 0,
                )
            }
        }
        return list
    }

    fun deleteRecurring(id: Long): Int =
        writableDatabase.delete(RECURRING_TABLE, "$REC_ID = ?", arrayOf(id.toString()))

    // ── Goals CRUD ────────────────────────────────────────────────────────────

    fun addGoal(goal: Goal): Long {
        val cv = ContentValues().apply {
            put(GOAL_NAME, goal.name)
            put(GOAL_TARGET, goal.targetAmount)
            put(GOAL_SAVED, goal.savedAmount)
            put(GOAL_COLOR, goal.color)
            put(GOAL_DEADLINE, goal.deadline)
        }
        return writableDatabase.insert(GOALS_TABLE, null, cv)
    }

    fun getAllGoals(): List<Goal> {
        val list = mutableListOf<Goal>()
        readableDatabase.query(GOALS_TABLE, null, null, null, null, null, "$GOAL_ID ASC")
            .use { c ->
                while (c.moveToNext()) {
                    list += Goal(
                        id = c.getLong(c.getColumnIndexOrThrow(GOAL_ID)),
                        name = c.getString(c.getColumnIndexOrThrow(GOAL_NAME)),
                        targetAmount = c.getDouble(c.getColumnIndexOrThrow(GOAL_TARGET)),
                        savedAmount = c.getDouble(c.getColumnIndexOrThrow(GOAL_SAVED)),
                        color = c.getString(c.getColumnIndexOrThrow(GOAL_COLOR)),
                        deadline = c.getString(c.getColumnIndexOrThrow(GOAL_DEADLINE)) ?: ""
                    )
                }
            }
        return list
    }

    fun updateGoal(goal: Goal): Int {
        val cv = ContentValues().apply {
            put(GOAL_NAME, goal.name)
            put(GOAL_TARGET, goal.targetAmount)
            put(GOAL_SAVED, goal.savedAmount)
            put(GOAL_COLOR, goal.color)
            put(GOAL_DEADLINE, goal.deadline)
        }
        return writableDatabase.update(GOALS_TABLE, cv, "$GOAL_ID = ?", arrayOf(goal.id.toString()))
    }

    fun deleteGoal(id: Long): Int =
        writableDatabase.delete(GOALS_TABLE, "$GOAL_ID = ?", arrayOf(id.toString()))

    // ── Utility ───────────────────────────────────────────────────────────────

    fun clearAllData() {
        writableDatabase.apply {
            delete(TABLE, null, null)
            delete(INCOME_TABLE, null, null)
            delete(RECURRING_TABLE, null, null)
            delete(GOALS_TABLE, null, null)
        }
        notifyWidgets()
    }
}
