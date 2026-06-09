package com.example.data.repository

import com.example.data.dao.PayFlowDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

class PayFlowRepository(private val dao: PayFlowDao) {

    // --- User Session Repository ---
    fun getCurrentUserFlow(userId: Long): Flow<User?> = dao.getUserFlow(userId)

    suspend fun getUserById(userId: Long): User? = dao.getUserById(userId)

    suspend fun getUserByEmail(email: String): User? = dao.getUserByEmail(email)

    suspend fun registerUser(name: String, email: String, phone: String, pin: String, referralCode: String = ""): Long {
        val finalCode = "FLOW" + (1000..9999).random()
        val user = User(
            name = name,
            email = email,
            phone = phone,
            pin = pin,
            referralCode = finalCode,
            referredBy = referralCode,
            isLoggedIn = true
        )
        val newUserId = dao.insertUser(user)

        // Initialize wallet
        var bonusUSD = 100.0 // default signup reward
        if (referralCode.isNotBlank()) {
            // Give $20 referral bonus if referral code used
            bonusUSD += 20.0
            
            // Log security warning / notice
            dao.insertSecurityLog(
                SecurityLog(
                    userId = newUserId,
                    actionType = "REFERRAL",
                    description = "Signed up using referral code: $referralCode. Earned $20.00 USD bonus."
                )
            )
            // Add notification
            dao.insertNotification(
                Notification(
                    userId = newUserId,
                    title = "Referral Bonus Claimed!",
                    message = "You received an extra $20.00 USD for joining via referral!"
                )
            )
        }

        dao.insertWallet(
            Wallet(
                userId = newUserId,
                balanceUSD = bonusUSD,
                balanceEUR = 50.0,
                balanceGBP = 20.0
            )
        )

        // Security log
        dao.insertSecurityLog(
            SecurityLog(
                userId = newUserId,
                actionType = "REGISTER",
                description = "Account registered and digital wallet initiated."
            )
        )

        // Notification
        dao.insertNotification(
            Notification(
                userId = newUserId,
                title = "Wallet Activated",
                message = "Welcome $name! Your default wallet has been loaded with $${String.format("%.2f", bonusUSD)} USD. Security protocols activated."
            )
        )

        return newUserId
    }

    suspend fun loginWithCredentials(email: String, pin: String): User? {
        val user = dao.getUserByEmail(email)
        return if (user != null && user.pin == pin) {
            val updatedUser = user.copy(isLoggedIn = true)
            dao.updateUser(updatedUser)
            // Log success
            dao.insertSecurityLog(
                SecurityLog(
                    userId = user.id,
                    actionType = "LOGIN",
                    description = "Successful login via secure PIN."
                )
            )
            updatedUser
        } else {
            null
        }
    }

    suspend fun logoutUser(userId: Long) {
        val user = dao.getUserById(userId)
        if (user != null) {
            dao.updateUser(user.copy(isLoggedIn = false))
            dao.insertSecurityLog(
                SecurityLog(
                    userId = userId,
                    actionType = "LOGOUT",
                    description = "User logged out securely."
                )
            )
        }
    }

    suspend fun updateBiometricSetting(userId: Long, enabled: Boolean) {
        val user = dao.getUserById(userId)
        if (user != null) {
            dao.updateUser(user.copy(isBiometricEnabled = enabled))
            dao.insertSecurityLog(
                SecurityLog(
                    userId = userId,
                    actionType = "BIOMETRIC_TOGGLE",
                    description = "Biometrics status changed to: " + if(enabled) "Enabled" else "Disabled"
                )
            )
        }
    }

    suspend fun updateSecurityPin(userId: Long, newPin: String) {
        val user = dao.getUserById(userId)
        if (user != null) {
            dao.updateUser(user.copy(pin = newPin))
            dao.insertSecurityLog(
                SecurityLog(
                    userId = userId,
                    actionType = "PIN_CHANGE",
                    description = "Security PIN changed."
                )
            )
        }
    }

    // --- Contacts List ---
    fun getSavedContactsFlow(): Flow<List<User>> = dao.getAllUsersFlow()
    suspend fun getAllUsers(): List<User> = dao.getAllUsers()

    // --- Wallet Balances ---
    fun getWalletFlow(userId: Long): Flow<Wallet?> = dao.getWalletFlowByUserId(userId)

    // --- Bank Account Linking ---
    fun getBankAccountsFlow(userId: Long): Flow<List<LinkedBankAccount>> = dao.getBankAccountsFlow(userId)

    suspend fun linkBankAccount(userId: Long, bankName: String, accountNumber: String, routingNumber: String) {
        dao.insertBankAccount(
            LinkedBankAccount(
                userId = userId,
                bankName = bankName,
                accountNumber = "•••• " + accountNumber.takeLast(4),
                routingNumber = routingNumber,
                balance = (2000..80000).random().toDouble()
            )
        )
        dao.insertSecurityLog(
            SecurityLog(
                userId = userId,
                actionType = "BANK_LINK",
                description = "Linked bank account ending in " + accountNumber.takeLast(4)
            )
        )
        dao.insertNotification(
            Notification(
                userId = userId,
                title = "Bank Account Linked",
                message = "Your checking account at $bankName has been linked successfully."
            )
        )
    }

    suspend fun removeBankAccount(userId: Long, accountId: Long) {
        dao.deleteBankAccount(accountId)
        dao.insertSecurityLog(
            SecurityLog(
                userId = userId,
                actionType = "BANK_UNLINK",
                description = "Unlinked bank account (ID: $accountId)"
            )
        )
    }

    // --- Multi-Currency Wallet Transactions ---
    suspend fun depositFunds(userId: Long, bankAccountId: Long, amount: Double, currency: String): Boolean {
        // Find wallet
        val wallet = dao.getWalletByUserId(userId) ?: return false
        val updatedWallet = when(currency) {
            "USD" -> wallet.copy(balanceUSD = wallet.balanceUSD + amount)
            "EUR" -> wallet.copy(balanceEUR = wallet.balanceEUR + amount)
            "GBP" -> wallet.copy(balanceGBP = wallet.balanceGBP + amount)
            else -> wallet
        }
        dao.updateWallet(updatedWallet)

        // Save transaction
        dao.insertTransaction(
            Transaction(
                senderId = userId,
                senderName = "Linked Bank Account",
                receiverId = userId,
                receiverName = "My Wallet",
                amount = amount,
                currency = currency,
                type = "ADD",
                status = "SUCCESS",
                note = "Deposited funds to digital wallet."
            )
        )

        dao.insertNotification(
            Notification(
                userId = userId,
                title = "Funds Deposited",
                message = "Deposited ${String.format("%.2f", amount)} $currency from your bank account."
            )
        )

        return true
    }

    suspend fun withdrawFunds(userId: Long, bankAccountId: Long, amount: Double, currency: String): Boolean {
        // Find wallet
        val wallet = dao.getWalletByUserId(userId) ?: return false

        // Check sufficient state
        val hasSufficient = when(currency) {
            "USD" -> wallet.balanceUSD >= amount
            "EUR" -> wallet.balanceEUR >= amount
            "GBP" -> wallet.balanceGBP >= amount
            else -> false
        }
        if (!hasSufficient) return false

        val updatedWallet = when(currency) {
            "USD" -> wallet.copy(balanceUSD = wallet.balanceUSD - amount)
            "EUR" -> wallet.copy(balanceEUR = wallet.balanceEUR - amount)
            "GBP" -> wallet.copy(balanceGBP = wallet.balanceGBP - amount)
            else -> wallet
        }
        dao.updateWallet(updatedWallet)

        // Save transaction
        dao.insertTransaction(
            Transaction(
                senderId = userId,
                senderName = "My Wallet",
                receiverId = userId,
                receiverName = "Linked Bank Account",
                amount = amount,
                currency = currency,
                type = "WITHDRAW",
                status = "SUCCESS",
                note = "Withdrew funds to bank checking."
            )
        )

        dao.insertNotification(
            Notification(
                userId = userId,
                title = "Withdrawal Executed",
                message = "Withdrew ${String.format("%.2f", amount)} $currency to checking account."
            )
        )

        return true
    }

    // --- P2P Payments with Security and Fraud Detection ---
    suspend fun executeP2PPayment(
        senderId: Long,
        receiverId: Long,
        amount: Double,
        currency: String,
        note: String
    ): Pair<Boolean, String> {
        val senderWallet = dao.getWalletByUserId(senderId) ?: return Pair(false, "Sender wallet not found")
        val receiverWallet = dao.getWalletByUserId(receiverId) ?: return Pair(false, "Receiver wallet not found")
        val senderUser = dao.getUserById(senderId) ?: return Pair(false, "Sender user profile error")
        val receiverUser = dao.getUserById(receiverId) ?: return Pair(false, "Recipient profile not found")

        // 1. Core Rule Check: Balance Sufficiency
        val senderHold = when (currency) {
            "USD" -> senderWallet.balanceUSD
            "EUR" -> senderWallet.balanceEUR
            "GBP" -> senderWallet.balanceGBP
            else -> 0.0
        }
        if (senderHold < amount) {
            return Pair(false, "Insufficient balance in selected currency ($currency)")
        }

        // 2. Real-time FRAUD DETECTION Trigger:
        // Set triggers: Extreme value (> $1,200) or suspicious keyword velocity trigger
        val isExtremelyHighValue = (currency == "USD" && amount >= 1200.0) ||
                (currency == "EUR" && amount >= 1100.0) ||
                (currency == "GBP" && amount >= 1000.0)

        val isSuspiciousKeyword = note.lowercase(Locale.ROOT).contains("leak") ||
                note.lowercase(Locale.ROOT).contains("hack") ||
                note.lowercase(Locale.ROOT).contains("crypto shadow")

        val detectFraud = isExtremelyHighValue || isSuspiciousKeyword

        // Update sender wallet
        val updatedSenderWallet = when (currency) {
            "USD" -> senderWallet.copy(balanceUSD = senderWallet.balanceUSD - amount)
            "EUR" -> senderWallet.copy(balanceEUR = senderWallet.balanceEUR - amount)
            "GBP" -> senderWallet.copy(balanceGBP = senderWallet.balanceGBP - amount)
            else -> senderWallet
        }

        // Update receiver wallet
        val updatedReceiverWallet = when (currency) {
            "USD" -> receiverWallet.copy(balanceUSD = receiverWallet.balanceUSD + amount)
            "EUR" -> receiverWallet.copy(balanceEUR = receiverWallet.balanceEUR + amount)
            "GBP" -> receiverWallet.copy(balanceGBP = receiverWallet.balanceGBP + amount)
            else -> receiverWallet
        }

        dao.updateWallet(updatedSenderWallet)
        dao.updateWallet(updatedReceiverWallet)

        // Store transaction log
        val transactionId = dao.insertTransaction(
            Transaction(
                senderId = senderId,
                senderName = senderUser.name,
                receiverId = receiverId,
                receiverName = receiverUser.name,
                amount = amount,
                currency = currency,
                type = "SEND",
                status = if (detectFraud) "SUCCESS" else "SUCCESS", // we let it pass, but raise security logging & trigger alarm notices
                isFraudDetected = detectFraud,
                note = note
            )
        )

        // Insert notification for Sender
        dao.insertNotification(
            Notification(
                userId = senderId,
                title = if (detectFraud) "⚠️ High-Value Security Alert" else "Payment Sent",
                message = if (detectFraud) {
                    "Your payment of ${String.format("%.2f", amount)} $currency to ${receiverUser.name} was completed, but triggered standard audits. Monitor your account."
                } else {
                    "Sent ${String.format("%.2f", amount)} $currency to ${receiverUser.name}."
                }
            )
        )

        // Insert notification for Receiver
        dao.insertNotification(
            Notification(
                userId = receiverId,
                title = "Payment Received",
                message = "${senderUser.name} sent you ${String.format("%.2f", amount)} $currency."
            )
        )

        // Log security audit for fraud detection
        if (detectFraud) {
            dao.insertSecurityLog(
                SecurityLog(
                    userId = senderId,
                    actionType = "FRAUD_ALERT",
                    description = "Alert flagged for sending transaction. Amount/Keywords triggered safety parameters: [Amount: $amount $currency, Note: \"$note\"]"
                )
            )
        } else {
            dao.insertSecurityLog(
                SecurityLog(
                    userId = senderId,
                    actionType = "TRANSACTION",
                    description = "Sent $amount $currency to ${receiverUser.name} (Transaction ID: $transactionId)."
                )
            )
        }

        return if (detectFraud) {
            Pair(true, "Completed with Security Warning: standard analytics scan triggered.")
        } else {
            Pair(true, "Payment sent successfully.")
        }
    }

    // --- Schedule a Transaction ---
    suspend fun schedulePayment(
        senderId: Long,
        receiverId: Long,
        amount: Double,
        currency: String,
        note: String,
        deliveryTime: Long
    ) {
        val senderUser = dao.getUserById(senderId) ?: return
        val receiverUser = dao.getUserById(receiverId) ?: return

        dao.insertTransaction(
            Transaction(
                senderId = senderId,
                senderName = senderUser.name,
                receiverId = receiverId,
                receiverName = receiverUser.name,
                amount = amount,
                currency = currency,
                type = "SEND",
                status = "PENDING",
                note = "[Scheduled] $note",
                scheduledDate = deliveryTime
            )
        )

        dao.insertNotification(
            Notification(
                userId = senderId,
                title = "Payment Scheduled",
                message = "Payment of ${String.format("%.2f", amount)} $currency to ${receiverUser.name} scheduled successfully."
            )
        )

        dao.insertSecurityLog(
            SecurityLog(
                userId = senderId,
                actionType = "TRANSACTION",
                description = "Customer scheduled future payment to ${receiverUser.name} for transfer."
            )
        )
    }

    // --- Request Money Feature ---
    suspend fun requestPayment(senderId: Long, requesterId: Long, amount: Double, currency: String, note: String) {
        val senderUser = dao.getUserById(senderId) ?: return // sender is the one who owes money (target)
        val receiverUser = dao.getUserById(requesterId) ?: return // receiver is the requester (will receive money)

        dao.insertTransaction(
            Transaction(
                senderId = requesterId, // requester initiates
                senderName = receiverUser.name,
                receiverId = senderId, // target owes
                receiverName = senderUser.name,
                amount = amount,
                currency = currency,
                type = "REQUEST_RECEIVE", // target sees request pending
                status = "PENDING",
                note = note
            )
        )

        // Notify target (senderId) of active request
        dao.insertNotification(
            Notification(
                userId = senderId,
                title = "Payment Requested",
                message = "${receiverUser.name} requested ${String.format("%.2f", amount)} $currency. Tap to pay now."
            )
        )
    }

    suspend fun acceptPaymentRequest(transactionId: Long, senderId: Long): Pair<Boolean, String> {
        // Target (sender) decides to approve and pay the request
        return Pair(true, "Completed")
    }

    // --- Notifications Feed ---
    fun getNotificationsFlow(userId: Long): Flow<List<Notification>> = dao.getNotificationsFlow(userId)

    suspend fun clearNotifications(userId: Long) {
        dao.markAllNotificationsAsRead(userId)
    }

    // --- Transactions Logs with Search Filter ---
    fun searchTransactions(userId: Long, query: String): Flow<List<Transaction>> {
        return if (query.isBlank()) {
            dao.getTransactionsFlow(userId)
        } else {
            dao.searchTransactionsFlow(userId, query)
        }
    }

    // --- Overall System Data (For Admin Dashboard) ---
    fun getAdminAllTransactions(): Flow<List<Transaction>> = dao.getAllTransactionsFlow()
    fun getAdminSecurityLogs(): Flow<List<SecurityLog>> = dao.getAllSecurityLogsFlow()
    fun getAdminAllUsers(): Flow<List<User>> = dao.getAllUsersFlow()

    suspend fun executeMobileRecharge(
        userId: Long,
        phoneNumber: String,
        carrier: String,
        originalAmount: Double,
        discountedAmount: Double,
        currency: String,
        usdDebitAmount: Double,
        couponApplied: String
    ): Pair<Boolean, String> {
        val wallet = dao.getWalletByUserId(userId) ?: return Pair(false, "Wallet not found")
        val user = dao.getUserById(userId) ?: return Pair(false, "Profile error")

        if (wallet.balanceUSD < usdDebitAmount) {
            return Pair(false, "Insufficient balance: need $${String.format("%.2f", usdDebitAmount)} USD equivalent")
        }

        // Deduct from primary reserve USD
        val updatedWallet = wallet.copy(balanceUSD = wallet.balanceUSD - usdDebitAmount)
        dao.updateWallet(updatedWallet)

        // Store log
        dao.insertTransaction(
            Transaction(
                senderId = userId,
                senderName = user.name,
                receiverId = -1L,
                receiverName = "$carrier ($phoneNumber)",
                amount = discountedAmount,
                currency = currency,
                type = "WITHDRAW",
                status = "SUCCESS",
                note = "Mobile Recharge ($originalAmount $currency value, Coupon $couponApplied applied)"
            )
        )

        // Notification
        dao.insertNotification(
            Notification(
                userId = userId,
                title = "Mobile Recharge Success",
                message = "Successfully recharged $phoneNumber ($carrier) with $currency ${String.format("%.2f", originalAmount)}. Charged $currency ${String.format("%.2f", discountedAmount)} (Promo $couponApplied applied)."
            )
        )

        dao.insertSecurityLog(
            SecurityLog(
                userId = userId,
                actionType = "TRANSACTION",
                description = "Recharged mobile phone $phoneNumber via $carrier. Amount: $currency $originalAmount (Paid: $discountedAmount / USD $usdDebitAmount equivalent)"
            )
        )

        return Pair(true, "Recharged successfully!")
    }

    suspend fun executeSuperServicePayment(
        userId: Long,
        serviceType: String,
        details: String,
        amountLKR: Double,
        currencyCode: String,
        usdDebitAmount: Double
    ): Pair<Boolean, String> {
        val wallet = dao.getWalletByUserId(userId) ?: return Pair(false, "Wallet not found")
        val user = dao.getUserById(userId) ?: return Pair(false, "Profile error")

        if (wallet.balanceUSD < usdDebitAmount) {
            return Pair(false, "Insufficient balance: need $${String.format("%.2f", usdDebitAmount)} USD equivalent")
        }

        // Deduct from primary reserve USD
        val updatedWallet = wallet.copy(balanceUSD = wallet.balanceUSD - usdDebitAmount)
        dao.updateWallet(updatedWallet)

        // Store log
        dao.insertTransaction(
            Transaction(
                senderId = userId,
                senderName = user.name,
                receiverId = -2L,
                receiverName = "$serviceType ($details)",
                amount = amountLKR,
                currency = currencyCode,
                type = "WITHDRAW",
                status = "SUCCESS",
                note = "Super App Payment: $serviceType - $details"
            )
        )

        // Notification
        dao.insertNotification(
            Notification(
                userId = userId,
                title = "$serviceType Confirmed",
                message = "Charged $currencyCode ${String.format("%.2f", amountLKR)} (USD ${String.format("%.2f", usdDebitAmount)} equivalent) for $details."
            )
        )

        dao.insertSecurityLog(
            SecurityLog(
                userId = userId,
                actionType = "TRANSACTION",
                description = "Paid $amountLKR $currencyCode for SuperApp Service: $serviceType ($details). USD Charged: $usdDebitAmount"
            )
        )

        return Pair(true, "Transaction processed successfully!")
    }

    suspend fun executeMerchantSettlement(
        userId: Long,
        amountLKR: Double,
        currencyCode: String,
        usdCreditAmount: Double,
        bankName: String
    ): Pair<Boolean, String> {
        val wallet = dao.getWalletByUserId(userId) ?: return Pair(false, "Wallet not found")
        val user = dao.getUserById(userId) ?: return Pair(false, "Profile error")

        // Add to primary reserve USD (Since we are settling back to checking account, let's treat it as a bank transfer withdraw or deposit)
        // Let's deduct from wallet, and simulate direct bank payout
        if (wallet.balanceUSD < usdCreditAmount) {
            return Pair(false, "Insufficient wallet balance to transfer $${String.format("%.2f", usdCreditAmount)} USD equivalent to $bankName")
        }

        val updatedWallet = wallet.copy(balanceUSD = wallet.balanceUSD - usdCreditAmount)
        dao.updateWallet(updatedWallet)

        dao.insertTransaction(
            Transaction(
                senderId = userId,
                senderName = "DH Merchant Wallet",
                receiverId = userId,
                receiverName = bankName,
                amount = amountLKR,
                currency = currencyCode,
                type = "WITHDRAW",
                status = "SUCCESS",
                note = "Merchant settlement payout cleared."
            )
        )

        dao.insertNotification(
            Notification(
                userId = userId,
                title = "Settlement Cleared",
                message = "Settled $currencyCode ${String.format("%.2f", amountLKR)} directly into your linked bank checking account at $bankName."
            )
        )

        dao.insertSecurityLog(
            SecurityLog(
                userId = userId,
                actionType = "MERCHANT_SETTLE",
                description = "Settled merchant wallet earnings. Amount: $currencyCode $amountLKR (USD $usdCreditAmount equivalent) to $bankName"
            )
        )

        return Pair(true, "Settlement Cleared!")
    }

    suspend fun executeMerchantDeposit(
        userId: Long,
        amountLKR: Double,
        currencyCode: String,
        usdCreditAmount: Double,
        payerName: String
    ): Pair<Boolean, String> {
        val wallet = dao.getWalletByUserId(userId) ?: return Pair(false, "Wallet not found")
        val user = dao.getUserById(userId) ?: return Pair(false, "Profile error")

        val updatedWallet = wallet.copy(balanceUSD = wallet.balanceUSD + usdCreditAmount)
        dao.updateWallet(updatedWallet)

        dao.insertTransaction(
            Transaction(
                senderId = -3L,
                senderName = payerName,
                receiverId = userId,
                receiverName = "DH Merchant Wallet",
                amount = amountLKR,
                currency = currencyCode,
                type = "RECEIVE",
                status = "SUCCESS",
                note = "Received customer payment via LANKAQR"
            )
        )

        dao.insertNotification(
            Notification(
                userId = userId,
                title = "Payment Received (LANKAQR)",
                message = "Credited $currencyCode ${String.format("%.2f", amountLKR)} to your wallet from client $payerName."
            )
        )

        return Pair(true, "Credited successfully!")
    }
}
