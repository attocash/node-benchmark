package cash.atto.transaction

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction

data class TransactionAccount(
    val privateKey: AttoPrivateKey,
    val transaction: AttoTransaction,
    val account: AttoAccount?
) {
    fun update(transaction: AttoTransaction, account: AttoAccount): TransactionAccount {
        return copy(transaction = transaction, account = account)
    }
}