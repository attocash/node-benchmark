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
    val votingNodeCount = 10U
    val nonVotingNodeCount = 0U
    val accountCount = 200U
    val timeoutInSeconds = 60

    val genesisPrivateKey = AttoPrivateKey.generate()
    val genesisTransaction = createGenesis(genesisPrivateKey)

    val tag = "main"

    val factory = NodeFactory(genesisTransaction)
    val nodeManager = NodeManager(genesisPrivateKey, tag, factory)
    nodeManager.start(votingNodeCount, nonVotingNodeCount)

    val counter = AtomicLong(0)

    val accountManager = AccountManager(genesisPrivateKey)
    accountManager.start(accountCount, { nodeManager.getVoter() }, { nodeManager.getVoter().publicKey!! }, counter)

    val running = AtomicBoolean(true)

    accountManager.accounts.forEach {
        Thread.startVirtualThread {
            while (running.get()) {
                var destinationAccount = accountManager.accounts.random()
                while (destinationAccount == it) {
                    destinationAccount = accountManager.accounts.random()
                }

                it.send(destinationAccount.publicKey)
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
        logger.error { "Failed to gracefully stop. Total balance is not equals the MAX" }
    }

    accountManager.close()
    nodeManager.close()
    factory.close()

    logger.info { "Processed $transactionCount transactions from $accountCount accounts in ${timeoutInSeconds}s. Stopping..." }
}


private fun createGenesis(privateKey: AttoPrivateKey): AttoTransaction {
    val block = AttoOpenBlock(
        network = AttoNetwork.LOCAL,
        version = 0u.toAttoVersion(),
        algorithm = AttoAlgorithm.V1,
        publicKey = privateKey.toPublicKey(),
        balance = AttoAmount.MAX,
        timestamp = Clock.System.now(),
        sendHashAlgorithm = AttoAlgorithm.V1,
        sendHash = AttoHash(ByteArray(32)),
        representativeAlgorithm = AttoAlgorithm.V1,
        representativePublicKey = privateKey.toPublicKey(),
    )

    return AttoTransaction(
        block = block,
        signature = privateKey.sign(block.hash),
        work = AttoWorker.cpu().use { it.work(block) }
    )
}
