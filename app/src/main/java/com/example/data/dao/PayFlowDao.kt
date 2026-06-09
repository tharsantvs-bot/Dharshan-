package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PayFlowDao {

    // --- Users ---
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserFlow(userId: Long): Flow<User?>

    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAllUsers(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    // --- Wallets ---
    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    suspend fun getWalletByUserId(userId: Long): Wallet?

    @Query("SELECT * FROM wallets WHERE userId = :userId LIMIT 1")
    fun getWalletFlowByUserId(userId: Long): Flow<Wallet?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: Wallet)

    @Update
    suspend fun updateWallet(wallet: Wallet)

    // --- Transactions ---
    @Query("SELECT * FROM transactions WHERE senderId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    fun getTransactionsFlow(userId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE (senderId = :userId OR receiverId = :userId) AND (senderName LIKE '%' || :query || '%' OR receiverName LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchTransactionsFlow(userId: Long, query: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE scheduledDate IS NOT NULL AND status = 'PENDING'")
    suspend fun getPendingScheduledTransactions(): List<Transaction>

    // --- Linked Bank Accounts ---
    @Query("SELECT * FROM bank_accounts WHERE userId = :userId")
    fun getBankAccountsFlow(userId: Long): Flow<List<LinkedBankAccount>>

    @Query("SELECT * FROM bank_accounts WHERE userId = :userId")
    suspend fun getBankAccounts(userId: Long): List<LinkedBankAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankAccount(bankAccount: LinkedBankAccount)

    @Query("DELETE FROM bank_accounts WHERE id = :accountId")
    suspend fun deleteBankAccount(accountId: Long)

    // --- Notifications ---
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestamp DESC")
    fun getNotificationsFlow(userId: Long): Flow<List<Notification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllNotificationsAsRead(userId: Long)

    // --- Security Logs ---
    @Query("SELECT * FROM security_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getSecurityLogsFlow(userId: Long): Flow<List<SecurityLog>>

    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC")
    fun getAllSecurityLogsFlow(): Flow<List<SecurityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityLog(securityLog: SecurityLog)
}
