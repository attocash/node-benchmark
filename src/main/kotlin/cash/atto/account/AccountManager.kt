package cash.atto.account

import cash.atto.commons.*
import cash.atto.node.Node
import mu.KotlinLogging
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


class AccountManager(private val genesisPrivateKey: AttoPrivateKey) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    private lateinit var genesisAccount: Account
    lateinit var accounts: List<Account>
        private set

    fun start(
        accountCount: UInt,
        nodeProvider: () -> Node,
        representativeProvider: () -> AttoPublicKey,
        counter: AtomicLong
    ) {
        val accounts = mutableListOf<Account>()

        genesisAccount = create(counter, nodeProvider.invoke(), representativeProvider.invoke(), genesisPrivateKey)
        accounts.add(genesisAccount)

        logger.info { "Created 1/${accountCount} accounts" }
        for ((index, _) in (2U..accountCount).withIndex()) {
            val account = create(counter, nodeProvider.invoke(), representativeProvider.invoke())
            accounts.add(account)
            logger.info { "Created ${index + 2}/${accountCount} accounts" }
        }

        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .until { genesisAccount.attoAccount != null }

        val initialAmount = AttoAmount.MAX.toBigDecimal(AttoUnit.RAW).divide(BigDecimal(accounts.size)).toAttoAmount()

        accounts.asSequence()
            .filter { it != genesisAccount }
            .forEach {
                genesisAccount.send(it.publicKey, initialAmount)
                logger.info { "Sent $initialAmount raw to ${it.publicKey}" }
            }

        Awaitility.await()
            .atMost(1, TimeUnit.MINUTES)
            .until { genesisAccount.attoAccount?.balance == initialAmount }

        this.accounts = accounts
    }

    private fun create(
        counter: AtomicLong,
        node: Node,
        representative: AttoPublicKey,
        privateKey: AttoPrivateKey = AttoPrivateKey.generate()
    ): Account {
        return Account(
            port = node.httpPort,
            representative = representative,
            privateKey = privateKey,
            counter
        ).apply {
            start()
        }
    }

    override fun close() {
        accounts.forEach { it.close() }
    }
}