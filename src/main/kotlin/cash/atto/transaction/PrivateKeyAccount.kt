package cash.atto.transaction

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoPrivateKey

data class PrivateKeyAccount(
    val privateKey: AttoPrivateKey,
    val account: AttoAccount?
) {
    fun update(account: AttoAccount): PrivateKeyAccount {
        return copy(account = account)
    }
}