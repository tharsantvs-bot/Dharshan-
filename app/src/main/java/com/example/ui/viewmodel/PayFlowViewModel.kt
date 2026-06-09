package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.PayFlowDatabase
import com.example.data.model.*
import com.example.data.repository.PayFlowRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val rateToUSD: Double, // how many units of this currency equal 1 USD
    val country: String,
    val flag: String = ""
)

val SupportedCurrencies = listOf(
    CurrencyInfo("USD", "$", 1.0, "United States", "🇺🇸"),
    CurrencyInfo("EUR", "€", 0.92, "Europe Zone", "🇪🇺"),
    CurrencyInfo("GBP", "£", 0.78, "United Kingdom", "🇬🇧"),
    CurrencyInfo("LKR", "Rs.", 300.0, "Sri Lanka", "🇱🇰"),
    CurrencyInfo("INR", "₹", 83.3, "India", "🇮🇳"),
    CurrencyInfo("AUD", "A$", 1.50, "Australia", "🇦🇺"),
    CurrencyInfo("CAD", "C$", 1.37, "Canada", "🇨🇦"),
    CurrencyInfo("JPY", "¥", 156.5, "Japan", "🇯🇵"),
    CurrencyInfo("CNY", "¥", 7.24, "China", "🇨🇳"),
    CurrencyInfo("SGD", "S$", 1.35, "Singapore", "🇸🇬"),
    CurrencyInfo("AED", "Dhs", 3.67, "United Arab Emirates", "🇦🇪"),
    CurrencyInfo("SAR", "SR", 3.75, "Saudi Arabia", "🇸🇦"),
    CurrencyInfo("NZD", "NZ$", 1.63, "New Zealand", "🇳🇿"),
    CurrencyInfo("ZAR", "R", 18.2, "South Africa", "🇿🇦"),
    CurrencyInfo("BRL", "R$", 5.30, "Brazil", "🇧🇷"),
    CurrencyInfo("MXN", "$", 18.4, "Mexico", "🇲🇽"),
    CurrencyInfo("KRW", "₩", 1378.0, "South Korea", "🇰🇷"),
    CurrencyInfo("SEK", "kr", 10.45, "Sweden", "🇸🇪"),
    CurrencyInfo("HKD", "HK$", 7.80, "Hong Kong", "🇭🇰"),
    CurrencyInfo("MYR", "RM", 4.71, "Malaysia", "🇲🇾"),
    CurrencyInfo("PHP", "₱", 58.7, "Philippines", "🇵🇭"),
    CurrencyInfo("IDR", "Rp", 16290.0, "Indonesia", "🇮🇩"),
    CurrencyInfo("THB", "฿", 36.7, "Thailand", "🇹🇭"),
    CurrencyInfo("KWD", "KD", 0.31, "Kuwait", "🇰🇼"),
    CurrencyInfo("OMR", "RO", 0.38, "Oman", "🇴🇲"),
    CurrencyInfo("QAR", "QR", 3.64, "Qatar", "🇶🇦")
)

class PayFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PayFlowRepository(PayFlowDatabase.getDatabase(application).payFlowDao())
    
    // Auth and User States - using standard StateFlow
    private val _currentUserId = MutableStateFlow<Long?>(1L) // Alex Mercer is auto-logged in via DB Prep
    val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(SupportedCurrencies[0]) // default USD
    val selectedCurrency: StateFlow<CurrencyInfo> = _selectedCurrency.asStateFlow()

    fun setCurrency(code: String) {
        val found = SupportedCurrencies.find { it.code == code }
        if (found != null) {
            _selectedCurrency.value = found
            rechargeCurrency = found.code
        }
    }

    // Flows observed by UI
    val currentUserState: StateFlow<User?> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) repository.getCurrentUserFlow(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentWalletState: StateFlow<Wallet?> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) repository.getWalletFlow(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bankAccountsState: StateFlow<List<LinkedBankAccount>> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) repository.getBankAccountsFlow(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationsState: StateFlow<List<Notification>> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) repository.getNotificationsFlow(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Contacts
    val savedContactsState: StateFlow<List<User>> = repository.getSavedContactsFlow()
        .combine(_currentUserId) { list, activeId ->
            list.filter { it.id != activeId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live variables updated by Compose UI directly
    val searchQuery = MutableStateFlow("")
    val transactionFilterType = MutableStateFlow("ALL") // "ALL", "SEND", "RECEIVE", "PENDING"

    val filteredTransactionsState: StateFlow<List<Transaction>> = _currentUserId
        .combine(searchQuery) { id, query -> id to query }
        .flatMapLatest { (id, query) ->
            if (id != null) {
                repository.searchTransactions(id, query)
            } else {
                flowOf(emptyList())
            }
        }
        .combine(transactionFilterType) { list, filter ->
            when (filter) {
                "SEND" -> list.filter { it.type == "SEND" || it.type == "ADD" }
                "RECEIVE" -> list.filter { it.type == "RECEIVE" || it.type == "WITHDRAW" }
                "PENDING" -> list.filter { it.status == "PENDING" }
                else -> list
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin Dashboard Info
    val allSystemUsers: StateFlow<List<User>> = repository.getAdminAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSystemTransactions: StateFlow<List<Transaction>> = repository.getAdminAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSystemSecurityLogs: StateFlow<List<SecurityLog>> = repository.getAdminSecurityLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Registration UI fields state
    var regName = ""
    var regEmail = ""
    var regPhone = ""
    var regPin = ""
    var regReferralCode = ""
    var regError: String? = null

    // Login UI state
    var loginEmail = ""
    var loginPin = ""
    var loginError: String? = null

    // P2P Payment states
    var selectedContactForPayment: User? = null
    var paymentAmount = ""
    var paymentCurrency = "USD"
    var paymentNote = ""
    var paymentIsScheduled = false
    var paymentScheduledDays = "1" // Days in future
    var paymentStatusMessage: String? = null
    var paymentSuccess = false

    // Deposit/Withdraw states
    var selectedBankForTransfer: LinkedBankAccount? = null
    var transferAmount = ""
    var transferCurrency = "USD"
    var transferStatusMsg: String? = null

    // Bank link parameters
    var bankInputName = ""
    var bankInputAccount = ""
    var bankInputRouting = ""

    // Request money states
    var requestAmount = ""
    var requestNote = ""
    var requestStatusMessage: String? = null

    // Mobile recharge states
    var rechargePhone = ""
    var rechargeCarrier = "Verizon" // default selected
    var rechargeAmountVal = "15.00" // default selected pack size (e.g. $15, $25, $50, $100)
    var rechargeCurrency = "USD"
    var rechargeStatusMsg: String? = null
    var rechargeSuccess = false
    var rechargeDialCode = "+1"
    var rechargeCountry = "United States"
    var activeHairstyle = "Sri Lankan Slick Back Waves" // default custom hairstyle
    var rechargeCouponCode = "BENTO20" // default prefilled
    var rechargeDiscountPercent = 20.0 // default prefilled

    fun applyCoupon(code: String) {
        rechargeCouponCode = code.trim().uppercase()
        rechargeDiscountPercent = when (rechargeCouponCode) {
            "BENTO20" -> 20.0
            "LANKASUPER" -> 30.0
            "FIRST30" -> 30.0
            "HALFPRICE" -> 50.0
            else -> {
                if (rechargeCouponCode.isNotEmpty()) 15.0 else 0.0
            }
        }
    }

    // Pin confirmation screen state
    var userPinInput = ""
    var pinScreenPurpose = "AUTH" // "AUTH", "PAY", "CONFIRM"

    // Helper to calculate discounted price
    fun getRechargeDiscountedAmount(): Double {
        val original = rechargeAmountVal.toDoubleOrNull() ?: 0.0
        val discountMultiplier = (100.0 - rechargeDiscountPercent) / 100.0
        return original * discountMultiplier
    }

    init {
        // Scan for logged in user at startup
        viewModelScope.launch {
            val users = repository.getAllUsers()
            val loggedInUser = users.find { it.isLoggedIn }
            if (loggedInUser != null) {
                _currentUserId.value = loggedInUser.id
            } else if (users.isNotEmpty()) {
                // Default to Alex Mercer
                _currentUserId.value = users.first().id
            }
        }
    }

    // --- Action Handlers ---
    
    fun performRegister() {
        if (regName.isBlank() || regEmail.isBlank() || regPhone.isBlank() || regPin.length < 4) {
            regError = "Please fill in all details. PIN must be at least 4 digits."
            return
        }

        viewModelScope.launch {
            try {
                val existing = repository.getUserByEmail(regEmail)
                if (existing != null) {
                    regError = "Email already registered."
                    return@launch
                }

                val newId = repository.registerUser(
                    name = regName,
                    email = regEmail,
                    phone = regPhone,
                    pin = regPin,
                    referralCode = regReferralCode
                )
                _currentUserId.value = newId
                regError = null
                // clear registration fields
                regName = ""
                regEmail = ""
                regPhone = ""
                regPin = ""
                regReferralCode = ""
            } catch (e: Exception) {
                regError = "Registration failed: ${e.localizedMessage}"
            }
        }
    }

    fun performLogin() {
        if (loginEmail.isBlank() || loginPin.length < 4) {
            loginError = "Enter your email and 4-digit security PIN."
            return
        }

        viewModelScope.launch {
            val user = repository.loginWithCredentials(loginEmail, loginPin)
            if (user != null) {
                _currentUserId.value = user.id
                loginError = null
                loginEmail = ""
                loginPin = ""
            } else {
                loginError = "Invalid email or security PIN."
            }
        }
    }

    fun performLogout() {
        val id = _currentUserId.value
        if (id != null) {
            viewModelScope.launch {
                repository.logoutUser(id)
                _currentUserId.value = null
            }
        }
    }

    fun linkBank() {
        val id = _currentUserId.value
        if (id != null && bankInputName.isNotBlank() && bankInputAccount.length >= 8) {
            viewModelScope.launch {
                repository.linkBankAccount(
                    userId = id,
                    bankName = bankInputName,
                    accountNumber = bankInputAccount,
                    routingNumber = bankInputRouting
                )
                bankInputName = ""
                bankInputAccount = ""
                bankInputRouting = ""
            }
        }
    }

    fun unlinkBank(accountId: Long) {
        val id = _currentUserId.value
        if (id != null) {
            viewModelScope.launch {
                repository.removeBankAccount(id, accountId)
            }
        }
    }

    fun depositWallet() {
        val id = _currentUserId.value
        val bank = selectedBankForTransfer
        val amount = transferAmount.toDoubleOrNull()
        if (id != null && bank != null && amount != null && amount > 0) {
            viewModelScope.launch {
                val success = repository.depositFunds(id, bank.id, amount, transferCurrency)
                if (success) {
                    transferStatusMsg = "Successfully deposited ${String.format("%.2f", amount)} $transferCurrency"
                    transferAmount = ""
                } else {
                    transferStatusMsg = "Transfer failed."
                }
            }
        } else {
            transferStatusMsg = "Invalid input values."
        }
    }

    fun withdrawWallet() {
        val id = _currentUserId.value
        val bank = selectedBankForTransfer
        val amount = transferAmount.toDoubleOrNull()
        if (id != null && bank != null && amount != null && amount > 0) {
            viewModelScope.launch {
                val success = repository.withdrawFunds(id, bank.id, amount, transferCurrency)
                if (success) {
                    transferStatusMsg = "Withdrew ${String.format("%.2f", amount)} $transferCurrency securely"
                    transferAmount = ""
                } else {
                    transferStatusMsg = "Insufficient wallet funds in $transferCurrency"
                }
            }
        } else {
            transferStatusMsg = "Invalid withdrawal request parameters."
        }
    }

    fun executeP2PPayment() {
        val sId = _currentUserId.value ?: return
        val receiver = selectedContactForPayment ?: return
        val amount = paymentAmount.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            paymentStatusMessage = "Please enter a valid amount."
            paymentSuccess = false
            return
        }

        viewModelScope.launch {
            if (paymentIsScheduled) {
                val days = paymentScheduledDays.toLongOrNull() ?: 1L
                val futureTime = System.currentTimeMillis() + (days * 86400000)
                repository.schedulePayment(
                    senderId = sId,
                    receiverId = receiver.id,
                    amount = amount,
                    currency = paymentCurrency,
                    note = paymentNote,
                    deliveryTime = futureTime
                )
                paymentStatusMessage = "Transaction scheduled successfully in ${days} day(s)!"
                paymentSuccess = true
                clearPaymentState()
            } else {
                val (success, message) = repository.executeP2PPayment(
                    senderId = sId,
                    receiverId = receiver.id,
                    amount = amount,
                    currency = paymentCurrency,
                    note = paymentNote
                )
                paymentStatusMessage = message
                paymentSuccess = success
                if (success) {
                    clearPaymentState()
                }
            }
        }
    }

    fun requestPayment() {
        val rId = _currentUserId.value ?: return
        val target = selectedContactForPayment ?: return
        val amount = requestAmount.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            requestStatusMessage = "Please specify a correct amount."
            return
        }

        viewModelScope.launch {
            repository.requestPayment(
                senderId = target.id,
                requesterId = rId,
                amount = amount,
                currency = paymentCurrency,
                note = requestNote
            )
            requestStatusMessage = "Request initialized! notifications pushed."
            requestAmount = ""
            requestNote = ""
        }
    }

    fun toggleBiometrics(enabled: Boolean) {
        val id = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.updateBiometricSetting(id, enabled)
        }
    }

    suspend fun updateSecurityPin(userId: Long, newPin: String) {
        repository.updateSecurityPin(userId, newPin)
    }

    fun clearNotifications() {
        val id = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.clearNotifications(id)
        }
    }

    suspend fun performSuperServicePayment(serviceType: String, details: String, amountLKR: Double): Pair<Boolean, String> {
        val id = _currentUserId.value ?: return Pair(false, "User context lost")
        val usdDebit = amountLKR / 300.0
        return repository.executeSuperServicePayment(id, serviceType, details, amountLKR, "LKR", usdDebit)
    }

    suspend fun performMerchantSettlement(amountLKR: Double, bankName: String): Pair<Boolean, String> {
        val id = _currentUserId.value ?: return Pair(false, "User context lost")
        val usdCredit = amountLKR / 300.0
        return repository.executeMerchantSettlement(id, amountLKR, "LKR", usdCredit, bankName)
    }

    suspend fun performMerchantCollection(amountLKR: Double, payerName: String): Pair<Boolean, String> {
        val id = _currentUserId.value ?: return Pair(false, "User context lost")
        val usdCredit = amountLKR / 300.0
        return repository.executeMerchantDeposit(id, amountLKR, "LKR", usdCredit, payerName)
    }

    fun performMobileRecharge() {
        val uId = _currentUserId.value ?: return
        val phone = rechargePhone.trim()
        val original = rechargeAmountVal.toDoubleOrNull() ?: 0.0
        val discounted = getRechargeDiscountedAmount()

        // Find current conversion rate
        val curr = _selectedCurrency.value
        val usdDebit = discounted / curr.rateToUSD

        if (phone.length < 5) {
            rechargeStatusMsg = "Please enter a valid phone number."
            rechargeSuccess = false
            return
        }

        if (original <= 0.0) {
            rechargeStatusMsg = "Please enter/select a valid recharge amount."
            rechargeSuccess = false
            return
        }

        viewModelScope.launch {
            val (success, message) = repository.executeMobileRecharge(
                userId = uId,
                phoneNumber = "$rechargeDialCode $phone",
                carrier = rechargeCarrier,
                originalAmount = original,
                discountedAmount = discounted,
                currency = curr.code,
                usdDebitAmount = usdDebit,
                couponApplied = if (rechargeCouponCode.isEmpty()) "NONE" else rechargeCouponCode
            )
            rechargeStatusMsg = message
            rechargeSuccess = success
            if (success) {
                // Clear fields if success
                rechargePhone = ""
            }
        }
    }

    private fun clearPaymentState() {
        paymentAmount = ""
        paymentNote = ""
        paymentIsScheduled = false
        paymentScheduledDays = "1"
    }

    // Dynamic QR Generator Code
    fun getQRPayloadForWallet(): String {
        val idVal = _currentUserId.value ?: return "payflow://pay"
        return "payflow://pay?userId=$idVal"
    }

    // Direct Quick Payment from QR Scans
    fun processScannedQRPayload(payload: String): Boolean {
        if (!payload.startsWith("payflow://pay")) return false
        try {
            val params = payload.substringAfter("?").split("&")
            var scannedId: Long? = null
            for (p in params) {
                val parts = p.split("=")
                if (parts.size == 2) {
                    if (parts[0] == "userId") scannedId = parts[1].toLongOrNull()
                }
            }
            if (scannedId != null) {
                viewModelScope.launch {
                    val user = repository.getUserById(scannedId)
                    if (user != null) {
                        selectedContactForPayment = user
                    }
                }
                return true
            }
        } catch (e: Exception) {
            // handle error parse
        }
        return false
    }

    fun getActiveUserId(): Long? = _currentUserId.value
}
