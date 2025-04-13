package cash.atto

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoTransaction
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val httpClient =
    HttpClient(Apache) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }

        install(HttpTimeout)

        engine {
            customizeClient {
                setMaxConnTotal(1000)
                setMaxConnPerRoute(1000)
            }
        }

        expectSuccess = true
    }

class TransactionPublisher(
    private val url: String,
    private val retryDelay: Duration = 1.seconds
) : AutoCloseable {
    val scope = CoroutineScope(Dispatchers.Default)

    val publicKeyMap = ConcurrentHashMap<AttoPublicKey, MutableSharedFlow<AttoHash>>()

    private fun getFlow(publicKey: AttoPublicKey): MutableSharedFlow<AttoHash> {
        return publicKeyMap.computeIfAbsent(publicKey) {
            MutableSharedFlow()
        }
    }

    fun start() {
        val transactionStreamUrl = "$url/transactions/stream"
        scope.launch {
            while (isActive) {
                try {

                    httpClient
                        .prepareGet(transactionStreamUrl) {
                            timeout {
                                socketTimeoutMillis = Long.MAX_VALUE
                                connectTimeoutMillis = Long.MAX_VALUE
                            }
                            headers {
                                append("Accept", "application/x-ndjson")
                            }
                        }.execute { response ->
                            val channel: ByteReadChannel = response.body()
                            while (!channel.isClosedForRead) {
                                val line = channel.readUTF8Line()
                                if (line != null) {
                                    val transaction = Json.decodeFromString<AttoTransaction>(line)
                                    val flow = getFlow(transaction.block.publicKey)
                                    flow.emit(transaction.hash)
                                }
                            }
                        }
                } catch (e: Exception) {
                    println("Failed to stream $transactionStreamUrl due to ${e.message}. Retrying in $retryDelay...")
                }

                delay(retryDelay)
            }
        }
    }


    @OptIn(FlowPreview::class)
    suspend fun publish(transaction: AttoTransaction): Boolean {
        try {
            getFlow(transaction.block.publicKey)
                .onSubscription { send(transaction) }
                .filter { transaction.hash == it }
                .timeout(60.seconds)
                .first()
            return true
        } catch (e: Exception) {
            println("Failed to publish $url due to ${e.message}")
            return false
        }
    }

    private suspend fun send(transaction: AttoTransaction): Boolean {
        val transactionUrl = "$url/transactions"
        val json = Json.encodeToString(transaction)

        try {
            httpClient.post(transactionUrl) {
                timeout {
                    socketTimeoutMillis = 10.seconds.inWholeMilliseconds
                    connectTimeoutMillis = 10.seconds.inWholeMilliseconds
                }
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            return true
        } catch (e: Exception) {
            println("Error processing transactions from account ${transaction.block.publicKey} with hash ${transaction.hash} ${e.message}")
            return false
        }
    }


    override fun close() {
        scope.cancel()
    }
}