package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.PayFlowDao
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class,
        Wallet::class,
        Transaction::class,
        LinkedBankAccount::class,
        Notification::class,
        SecurityLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PayFlowDatabase : RoomDatabase() {

    abstract fun payFlowDao(): PayFlowDao

    companion object {
        @Volatile
        private var INSTANCE: PayFlowDatabase? = null

        fun getDatabase(context: Context): PayFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PayFlowDatabase::class.java,
                    "payflow_database"
                )
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Prepopulate database in background list
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.payFlowDao()

                        // 1. Create a Primary Demo User (already logged in initially)
                        val primaryUserId = dao.insertUser(
                            User(
                                name = "Alex Mercer",
                                email = "alex@payflow.com",
                                phone = "+1 (555) 123-4567",
                                pin = "1234",
                                isLoggedIn = true,
                                avatarUrl = "demo_alex",
                                isBiometricEnabled = true,
                                referralCode = "FLOW99"
                            )
                        )

                        // 2. Prep default Wallet for primary user
                        dao.insertWallet(
                            Wallet(
                                userId = primaryUserId,
                                balanceUSD = 1530.45,
                                balanceEUR = 420.00,
                                balanceGBP = 280.00
                            )
                        )

                        // 3. Insert other Demo Users (Saved Contacts)
                        val u1Id = dao.insertUser(User(name = "Sarah Jenkins", email = "sarah.j@gmail.com", phone = "+1 (555) 987-6543", pin = "0000", avatarUrl = "sarah_j"))
                        val u2Id = dao.insertUser(User(name = "Michael Chen", email = "m.chen@tech.org", phone = "+1 (555) 443-2211", pin = "0000", avatarUrl = "michael_c"))
                        val u3Id = dao.insertUser(User(name = "Sophia Patel", email = "patel.sophia@pay.co", phone = "+1 (555) 776-8899", pin = "0000", avatarUrl = "sophia_p"))

                        // Create wallets for other users
                        dao.insertWallet(Wallet(userId = u1Id, balanceUSD = 450.00, balanceEUR = 210.00))
                        dao.insertWallet(Wallet(userId = u2Id, balanceUSD = 1290.50, balanceEUR = 880.00))
                        dao.insertWallet(Wallet(userId = u3Id, balanceUSD = 89.20, balanceEUR = 50.00))

                        // 4. Populate Linked Bank Accounts
                        dao.insertBankAccount(
                            LinkedBankAccount(
                                userId = primaryUserId,
                                bankName = "Chase Premium Checking",
                                accountNumber = "•••• 8904",
                                routingNumber = "021000021",
                                balance = 12450.00
                            )
                        )
                        dao.insertBankAccount(
                            LinkedBankAccount(
                                userId = primaryUserId,
                                bankName = "Fidelity Investment Savings",
                                accountNumber = "•••• 4412",
                                routingNumber = "031101211",
                                balance = 43200.50
                            )
                        )

                        // 5. Populate Sample Transaction Records
                        dao.insertTransaction(
                            Transaction(
                                senderId = primaryUserId,
                                senderName = "Alex Mercer",
                                receiverId = u1Id,
                                receiverName = "Sarah Jenkins",
                                amount = 45.00,
                                currency = "USD",
                                type = "SEND",
                                status = "SUCCESS",
                                timestamp = System.currentTimeMillis() - 3600000 * 2, // 2 hours ago
                                note = "Dinner bill split"
                            )
                        )
                        dao.insertTransaction(
                            Transaction(
                                senderId = u2Id,
                                senderName = "Michael Chen",
                                receiverId = primaryUserId,
                                receiverName = "Alex Mercer",
                                amount = 150.00,
                                currency = "USD",
                                type = "RECEIVE",
                                status = "SUCCESS",
                                timestamp = System.currentTimeMillis() - 3600000 * 12, // 12 hours ago
                                note = "Freelance UX Work"
                            )
                        )
                        dao.insertTransaction(
                            Transaction(
                                senderId = primaryUserId,
                                senderName = "Alex Mercer",
                                receiverId = primaryUserId,
                                receiverName = "Alex Mercer",
                                amount = 500.00,
                                currency = "USD",
                                type = "ADD",
                                status = "SUCCESS",
                                timestamp = System.currentTimeMillis() - 3600000 * 24, // 1 day ago
                                note = "Deposit from Chase Checking"
                            )
                        )

                        // Add a scheduled payment example
                        dao.insertTransaction(
                            Transaction(
                                senderId = primaryUserId,
                                senderName = "Alex Mercer",
                                receiverId = u3Id,
                                receiverName = "Sophia Patel",
                                amount = 95.00,
                                currency = "USD",
                                type = "SEND",
                                status = "PENDING",
                                timestamp = System.currentTimeMillis() + 86400000 * 2, // 2 days in future
                                note = "Monthly Shared Subs",
                                scheduledDate = System.currentTimeMillis() + 86400000 * 2
                            )
                        )

                        // Add a money request example
                        dao.insertTransaction(
                            Transaction(
                                senderId = u1Id,
                                senderName = "Sarah Jenkins",
                                receiverId = primaryUserId,
                                receiverName = "Alex Mercer",
                                amount = 32.50,
                                currency = "USD",
                                type = "REQUEST_RECEIVE", // Primary user receives request, Sarah wants to get paid
                                status = "PENDING",
                                timestamp = System.currentTimeMillis() - 3600000 * 5, // 5 hours ago
                                note = "Concert Ticket reimbursement"
                            )
                        )

                        // 6. Security log
                        dao.insertSecurityLog(
                            SecurityLog(
                                userId = primaryUserId,
                                actionType = "LOGIN",
                                description = "User Alex Mercer logged in successfully. Biometrics verified.",
                                deviceId = "Pixel 8 Pro"
                            )
                        )
                        dao.insertSecurityLog(
                            SecurityLog(
                                userId = primaryUserId,
                                actionType = "BIOMETRIC_TOGGLE",
                                description = "Biometric authentication enrolled.",
                                deviceId = "Pixel 8 Pro"
                            )
                        )

                        // 7. Initial notifications
                        dao.insertNotification(
                            Notification(
                                userId = primaryUserId,
                                title = "Welcome to PayFlow",
                                message = "Secure, swift and smart digital payments. Verify your wallet and link checking accounts to get started instantly!"
                            )
                        )
                        dao.insertNotification(
                            Notification(
                                userId = primaryUserId,
                                title = "Michael Chen paid you",
                                message = "Received $150.00 USD for 'Freelance UX Work'. Your wallet was credited instantly."
                            )
                        )
                    }
                }
            }
        }
    }
}
