package cash.atto.transaction

import cash.atto.commons.*
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

class TransactionGenerator(val accountCount: UShort, val totalTransactions: UInt) {
    init {
        require(accountCount > 0u) { "Account count must be positive." }
        require(accountCount <= 1000u) { "Account count must not exceed 1000." }
    }

    companion object {
        val genesisPrivateKey = AttoPrivateKey(ByteArray(32))
    }

    val worker = AttoWorker.cpu()

    val transactionAccount = AttoAccount.open(
        representativeAlgorithm = AttoAlgorithm.V1,
        representativePublicKey = genesisPrivateKey.toPublicKey(),
        receivable = AttoReceivable(
            hash = AttoHash(ByteArray(32)),
            version = 0u.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(ByteArray(32)),
            timestamp = Clock.System.now().minus(1.seconds),
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = genesisPrivateKey.toPublicKey(),
            amount = AttoAmount.MAX

        ),
        network = AttoNetwork.LOCAL,
        timestamp = Clock.System.now(),
    ).let {
        val block = it.first
        val transaction = AttoTransaction(
            block = block,
            signature = runBlocking { genesisPrivateKey.sign(block.hash) },
            work = runBlocking { worker.work(block) }
        )
        TransactionAccount(genesisPrivateKey, transaction, it.second)
    }

    private val accountMap = mutableMapOf<AttoPublicKey, PrivateKeyAccount>()

    fun prepare(): List<AttoTransaction> {
        println(
            "Starting the preparation of the initial transactions. Those are the transactions to distribute the same amount to all accounts."
        )

        val initialTransactions = mutableListOf<AttoTransaction>()
        initialTransactions.add(transactionAccount.transaction) // Genesis

        accountMap[genesisPrivateKey.toPublicKey()] =
            PrivateKeyAccount(transactionAccount.privateKey, transactionAccount.account)


        val initialAmount =
            AttoAmount.MAX.toString(AttoUnit.RAW)
                .toBigDecimal()
                .divide(BigDecimal(accountCount.toInt()))
                .toString()
                .toAttoAmount()

        repeat(accountCount.toInt() - 1) {
            val thisAccountPrivateKey = AttoPrivateKey.generate()
            accountMap[thisAccountPrivateKey.toPublicKey()] = PrivateKeyAccount(thisAccountPrivateKey, null)

            val sendTransaction =
                send(genesisPrivateKey.toPublicKey(), thisAccountPrivateKey.toPublicKey(), initialAmount)
            initialTransactions.add(sendTransaction)

            val receiveTransaction = receive((sendTransaction.block as AttoSendBlock).toReceivable())
            initialTransactions.add(receiveTransaction)
        }

        println(
            "Prepared ${initialTransactions.size} initial transactions. Note: 1 fewer transactions because 1 account is the genesis account - it doesn't have to receive anything."
        )

        return initialTransactions
    }

    fun generate(): Map<AttoPublicKey, List<AttoTransaction>> {
        val receiverPublicKey = AttoPublicKey(ByteArray(32))
        val keys = accountMap.values.map { it.privateKey }

        val transactionMap = mutableMapOf<AttoPublicKey, MutableList<AttoTransaction>>()
        keys.forEach { key ->
            transactionMap[key.toPublicKey()] = mutableListOf()
        }

        val total = totalTransactions.toInt()

        for (i in 0 until totalTransactions.toInt()) {
            val keyIndex = i % keys.size
            val privateKey = keys[keyIndex]
            val publicKey = privateKey.toPublicKey()
            val tx = send(publicKey, receiverPublicKey, AttoAmount(1UL))
            transactionMap[publicKey]!!.add(tx)

            if ((i + 1) % 1000 == 0 || i == total - 1) {
                val completed = i + 1
                val remaining = total - completed
                println("Prepared $completed transactions for ${transactionMap.size} accounts, $remaining remaining")
            }
        }

        println("Generated ${transactionMap.values.sumOf { it.size }} transactions.")

        return transactionMap
    }


    private fun send(
        senderPublicKey: AttoPublicKey,
        receiverPublicKey: AttoPublicKey,
        amount: AttoAmount
    ): AttoTransaction {
        Thread.sleep(1) // make sure there's a difference of 1ms per transaction

        val privateKeyAccount = accountMap[senderPublicKey]!!
        val privateKey = privateKeyAccount.privateKey
        val account = privateKeyAccount.account!!

        val (block, updatedAccount) = account.send(AttoAlgorithm.V1, receiverPublicKey, amount)

        val transaction = AttoTransaction(
            block = block,
            signature = runBlocking { privateKey.sign(block.hash) },
            work = runBlocking { worker.work(block) }
        )

        accountMap[senderPublicKey] = privateKeyAccount.update(updatedAccount)

        return transaction
    }

    private fun receive(
        receivable: AttoReceivable,
    ): AttoTransaction {
        val privateKeyAccount = accountMap[receivable.receiverPublicKey]!!
        val privateKey = privateKeyAccount.privateKey
        val account = privateKeyAccount.account

        val (transaction, updatedAccount) = if (account == null) {
            val (openBlock, updatedAccount) = AttoAccount.open(
                representativeAlgorithm = AttoAlgorithm.V1,
                representativePublicKey = genesisPrivateKey.toPublicKey(),
                receivable = receivable,
                network = AttoNetwork.LOCAL
            )

            val transaction = AttoTransaction(
                block = openBlock,
                signature = runBlocking { privateKey.sign(openBlock.hash) },
                work = runBlocking { worker.work(openBlock) }
            )

            transaction to updatedAccount
        } else {
            val (receiveBlock, updatedAccount) = account.receive(
                receivable = receivable,
            )

            val openTransaction = AttoTransaction(
                block = receiveBlock,
                signature = runBlocking { privateKey.sign(receiveBlock.hash) },
                work = runBlocking { worker.work(receiveBlock) }
            )

            openTransaction to updatedAccount
        }

        accountMap[receivable.receiverPublicKey] = privateKeyAccount.update(updatedAccount)


        return transaction
    }
}