package cash.atto

import cash.atto.account.AccountManager
import cash.atto.commons.*
import cash.atto.node.NodeFactory
import cash.atto.node.NodeManager
import kotlinx.datetime.Clock
import mu.KotlinLogging
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong


class Application

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    val votingNodeCount = 4U
    val nonVotingNodeCount = 6U
    val accountCount = 100U
    val timeoutInSeconds = 60

    val genesisPrivateKey = AttoPrivateKey.generate()
    val genesisTransaction = createGenesis(genesisPrivateKey)

    val tag = "main"

    val factory = NodeFactory(genesisTransaction)
    val nodeManager = NodeManager(genesisPrivateKey, tag, factory)
    nodeManager.start(votingNodeCount, nonVotingNodeCount)

    val accountManager = AccountManager(genesisPrivateKey)
    accountManager.start(accountCount, { nodeManager.getNonVoter() }, { nodeManager.getVoter().publicKey!! })

    val running = AtomicBoolean(true)
    val counter = AtomicLong(0)

    accountManager.accounts.forEach {
        Thread.startVirtualThread {
            while (running.get()) {
                if (it.attoAccount == null) {
                    Thread.sleep(100)
                    continue
                }
                var destinationAccount = accountManager.accounts.random()
                while (destinationAccount == it) {
                    destinationAccount = accountManager.accounts.random()
                }

                it.send(destinationAccount.publicKey)
                counter.incrementAndGet()
            }
        }
    }

    Thread.sleep(timeoutInSeconds * 1000L)
    val transactionCount = counter.get()
    running.set(false)

    try {
        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .until {
                val totalAmount = accountManager.accounts
                    .sumOf { it.attoAccount?.balance?.raw ?: 0U }

                totalAmount == AttoAmount.MAX.raw
            }
    } catch (e: Exception) {
        logger.info { "Failed to gracefully stop. Total balance is not equals the MAX" }
    }

    accountManager.close()
    nodeManager.close()
    factory.close()

    logger.info { "Processed $transactionCount transactions from $accountCount accounts in ${timeoutInSeconds}s. Stopping..." }
}


private fun createGenesis(privateKey: AttoPrivateKey): AttoTransaction {
    val block = AttoOpenBlock(
        version = 0u.toAttoVersion(),
        algorithm = AttoAlgorithm.V1,
        publicKey = privateKey.toPublicKey(),
        balance = AttoAmount.MAX,
        timestamp = Clock.System.now(),
        sendHashAlgorithm = AttoAlgorithm.V1,
        sendHash = AttoHash(ByteArray(32)),
        representative = privateKey.toPublicKey(),
    )

    return AttoTransaction(
        block = block,
        signature = privateKey.sign(block.hash),
        work = AttoWork.work(AttoNetwork.LOCAL, block.timestamp, block.publicKey)
    )
}
