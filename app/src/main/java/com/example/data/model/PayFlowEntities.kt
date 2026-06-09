package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val phone: String,
    val pin: String, // simple PIN security
    val isLoggedIn: Boolean = false,
    val avatarUrl: String = "",
    val isBiometricEnabled: Boolean = false,
    val referralCode: String = "",
    val referredBy: String = ""
)

@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val balanceUSD: Double = 1000.00, // starting funds
    val balanceEUR: Double = 500.00,
    val balanceGBP: Double = 300.00
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: Long,
    val senderName: String,
    val receiverId: Long,
    val receiverName: String,
    val amount: Double,
    val currency: String = "USD",
    val type: String, // "SEND", "RECEIVE", "ADD", "WITHDRAW", "REQUEST_SEND", "REQUEST_RECEIVE"
    val status: String = "SUCCESS", // "SUCCESS", "PENDING", "REJECTED"
    val timestamp: Long = System.currentTimeMillis(),
    val isFraudDetected: Boolean = false,
    val note: String = "",
    val scheduledDate: Long? = null // for scheduled payments
)

@Entity(tableName = "bank_accounts")
data class LinkedBankAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val bankName: String,
    val accountNumber: String,
    val routingNumber: String = "",
    val balance: Double = 5000.00
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "security_logs")
data class SecurityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val actionType: String, // "LOGIN", "TRANSACTION", "PIN_CHANGE", "BIOMETRIC_TOGGLE", "FRAUD_ALERT"
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "Pixel 8 Pro"
)
