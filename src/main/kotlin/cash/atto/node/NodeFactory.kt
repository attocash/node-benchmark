package cash.atto.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.Network
import java.sql.DriverManager

class NodeFactory(private val genesis: AttoTransaction) : AutoCloseable {
    private val benchmarkNetwork = Network.builder().build()
    private val databaseContainerName = "benchmark-database"
    private val mysqlContainer = MySQLContainer("mysql:8.2").apply {
        withCreateContainerCmdModifier {
            it
                .withName(databaseContainerName)
                .withHostName(databaseContainerName)
        }
        withNetwork(benchmarkNetwork)
        withDatabaseName("benchmark")
        withUsername("root")
        start()
    }

    init {
        DriverManager.getConnection(mysqlContainer.jdbcUrl, mysqlContainer.username, mysqlContainer.password)
            .use { connection ->
                val statement = connection.createStatement()
                statement.executeUpdate("SET GLOBAL max_connections = 1100")
            }
    }

    fun create(tag: String, alwaysPull: Boolean, privateKey: AttoPrivateKey?, defaultNodes: List<String>): Node {
        val id = randomId()

        DriverManager.getConnection(mysqlContainer.jdbcUrl, mysqlContainer.username, mysqlContainer.password)
            .use { connection ->
                connection.createStatement().executeUpdate("CREATE DATABASE $id")
            }

        val node = Node(
            id = id,
            tag = tag,
            pull = alwaysPull,
            privateKey = privateKey,
            genesis = genesis,
            databaseHost = databaseContainerName,
            databaseUser = mysqlContainer.username,
            databasePassword = mysqlContainer.password,
            defaultNodes = defaultNodes,
            benchmarkNetwork = benchmarkNetwork,
        )

        return node
    }

    override fun close() {
        mysqlContainer.close()
    }
}

private fun randomId(): String {
    val allowedChars = ('a'..'z')
    return (1..20)
        .map { allowedChars.random() }
        .joinToString("")
}