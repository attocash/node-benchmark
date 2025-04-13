package cash.atto.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.Network
import org.testcontainers.images.PullPolicy
import java.sql.DriverManager

class NodeFactory(private val genesis: AttoTransaction, private val inMemory: Boolean = false) : AutoCloseable {
    private val benchmarkNetwork = Network.builder().build()
    private val databaseContainerName = "benchmark-database"
    private val mysqlContainer = MySQLContainer("mysql:8.4").apply {
        withCreateContainerCmdModifier {
            it
                .withName(databaseContainerName)
                .withHostName(databaseContainerName)
        }
        withNetwork(benchmarkNetwork)
        withDatabaseName("benchmark")
        withUsername("root")
        withImagePullPolicy(PullPolicy.alwaysPull())
        if (inMemory) {
            withTmpFs(mapOf("/var/lib/mysql" to "rw,noexec,nosuid,size=1024m"))
        }
        withCommand(
            "--innodb_flush_log_at_trx_commit=1",
            "--innodb_flush_method=O_DIRECT",
            "--innodb_log_buffer_size=64M",
            "--innodb_buffer_pool_size=512M",
            "--bulk_insert_buffer_size=256M",
            "--innodb_log_file_size=128M",
            "--innodb_log_files_in_group=2"
        )
        start()
    }

//    private val mysqlContainer = MariaDBContainer("mariadb:11.7").apply {
//        withCreateContainerCmdModifier {
//            it
//                .withName(databaseContainerName)
//                .withHostName(databaseContainerName)
//        }
//        withNetwork(benchmarkNetwork)
//        withDatabaseName("benchmark")
//        withUsername("root")
//        withImagePullPolicy(PullPolicy.alwaysPull())
//        withCommand(
//            "--innodb_flush_log_at_trx_commit=1",
//            "--innodb_log_buffer_size=64M",
//            "--innodb_buffer_pool_size=512M",
//            "--innodb_flush_method=O_DIRECT",
//            "--bulk_insert_buffer_size=256M",
//        )
//        start()
//    }

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