package cash.atto.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import cash.atto.commons.toHex
import cash.atto.commons.toPublicKey
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy

class Node(
    val id: String,
    pull: Boolean,
    tag: String,
    privateKey: AttoPrivateKey?,
    genesis: AttoTransaction,
    databaseHost: String,
    databaseUser: String,
    databasePassword: String,
    defaultNodes: List<String>,
    private val benchmarkNetwork: Network,
) : AutoCloseable {
    val publicKey = privateKey?.toPublicKey()

    private val container = GenericContainer("ghcr.io/attocash/node:$tag").apply {
        withNetwork(benchmarkNetwork)
        withNetworkAliases(id)
        withCreateContainerCmdModifier {
            it
                .withName(id)
                .withHostName(id)
        }
        withExposedPorts(8080, 8081)
        waitingFor(Wait.forHttp("/transactions/${genesis.hash}").forPort(8080))
        withEnv("SPRING_PROFILES_ACTIVE", "local")
        withEnv("ATTO_DB_HOST", databaseHost)
        withEnv("ATTO_DB_PORT", "3306")
        withEnv("ATTO_DB_NAME", id)
        withEnv("ATTO_DB_USER", databaseUser)
        withEnv("ATTO_DB_PASSWORD", databasePassword)
        withEnv("ATTO_PUBLIC_URI", "ws://$id:8082")
        withEnv("ATTO_GENESIS", genesis.toHex())
        withEnv("ATTO_NODE_FORCE_API", "true")
        if (privateKey != null) {
            withEnv("ATTO_PRIVATE_KEY", privateKey.value.toHex())
        }
        if (pull) {
            withImagePullPolicy(PullPolicy.alwaysPull())
        }
        if (defaultNodes.isNotEmpty()) {
            withEnv("ATTO_DEFAULT_NODES", defaultNodes.joinToString(separator = ","))
        }
        withEnv("LOGGING_LEVEL_CASH_ATTO_NODE_NETWORK", "INFO")
        withEnv("LOGGING_LEVEL_CASH_ATTO_NODE_ELECTION", "INFO")
        withEnv("LOGGING_LEVEL_CASH_ATTO_NODE_TRANSACTION", "INFO")
        withEnv("LOGGING_LEVEL_CASH_ATTO_NODE_VOTE", "INFO")
        withEnv("LOGGING_LEVEL_CASH_ATTO_NODE_BOOTSTRAP", "INFO")
        withEnv("LOGGING_LEVEL_CASH_ATTO_NODE_TRANSACTION_VALIDATION", "DEBUG")
        start()
    }

    val httpPort = container.getMappedPort(8080)

    override fun close() {
        container.close()
    }

}