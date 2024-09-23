package cash.atto.account

import cash.atto.commons.*
import io.netty.handler.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.netty.ByteBufFlux
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.logging.AdvancedByteBufFormat
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random
import kotlin.random.nextULong

private val httpClient = HttpClient.create()
    .headers { it.add("Content-Type", "application/json") }
    .wiretap(
        Account::javaClass.name,
        LogLevel.DEBUG,
        AdvancedByteBufFormat.TEXTUAL
    )

class Account(
    private val port: Int,
    private val representativePublicKey: AttoPublicKey,
    private val privateKey: AttoPrivateKey,
    private val counter: AtomicLong,
) : AutoCloseable {
    companion object {
        val WORKER = AttoWorker.cpu()
    }
    private val logger = KotlinLogging.logger {}

    val publicKey = privateKey.toPublicKey()
    private val accountState = MutableStateFlow<AttoAccount?>(null)

    val attoAccount: AttoAccount?
        get() = accountState.value

    @Volatile
    var lastHeight = 0UL

    val lock = ReentrantLock()

    @Volatile
    private var running = false

    fun start() {
        running = true


        val latch = CountDownLatch(2)

        initAccountStream {
            initReceivableStream {
                latch.countDown()
            }
            latch.countDown()
        }

        latch.await()
    }

    private fun initAccountStream(onSubscribeListener: () -> Any) {
        val uri = "http://localhost:${port}/accounts/$publicKey/stream"

        httpClient.get()
            .uri(uri)
            .responseContent()
            .asString()
            .map { Json.decodeFromString<AttoAccount>(it) }
            .takeWhile { running }
            .doOnSubscribe {
                logger.info { "$publicKey subscribed to $uri" }
                onSubscribeListener.invoke()
            }
            .retryWhen(Retry.fixedDelay(6, Duration.ofSeconds(10)))
            .doOnError { e -> logger.error(e) { "Error while processing $uri stream" } }
            .log()
            .subscribe {
                accountState.value = it
            }
    }

    private fun initReceivableStream(onSubscribeListener: () -> Any) {
        val uri = "http://localhost:${port}/accounts/$publicKey/receivables/stream"
        httpClient.get()
            .uri(uri)
            .responseContent()
            .asString()
            .map { Json.decodeFromString<AttoReceivable>(it) }
            .takeWhile { running }
            .doOnSubscribe {
                logger.info { "$publicKey subscribed to $uri" }
                onSubscribeListener.invoke()
            }
            .doOnError { e -> logger.error(e) { "Error while processing $uri stream" } }
            .retryWhen(Retry.fixedDelay(6, Duration.ofSeconds(10)))
            .log()
            .subscribe {
                Thread.startVirtualThread {
                    receive(it)
                }
            }
    }

    private fun receive(receivable: AttoReceivable) {
        lock.withLock {
            val account = accountState.value
            val (block, work) = if (account == null) {
                val block = AttoAccount.open(AttoAlgorithm.V1, representativePublicKey, receivable, AttoNetwork.LOCAL)
                val work = WORKER.work(block)
                block to work
            } else {
                val block = account.receive(receivable)
                val work = WORKER.work(block)
                block to work
            }

            val transaction = AttoTransaction(
                block = block,
                signature = privateKey.sign(block.hash),
                work = work
            )
            publish(transaction)
        }
    }

    fun send(publicKey: AttoPublicKey, amount: AttoAmount? = null) {
        lock.withLock {
            val account = accountState.value ?: throw IllegalStateException("Account don't exist")
            val sendAmount = amount ?: account.balance.randomSendAmount() ?: return
            val block = account.send(AttoAlgorithm.V1, publicKey, sendAmount)
            val transaction = AttoTransaction(
                block = block,
                signature = privateKey.sign(block.hash),
                work = WORKER.work(block)
            )
            publish(transaction)
        }
    }

    private fun publish(transaction: AttoTransaction) {
        if (!running) {
            logger.warn { "Not running. Skip $transaction" }
            return
        }
        val uri = "http://localhost:${port}/transactions/stream"
        val json = Json.encodeToString(transaction)

        val timestamp = System.currentTimeMillis()

        httpClient
            .post()
            .uri(uri)
            .send(ByteBufFlux.fromString(Mono.just(json)))
            .responseContent()
            .asString()
            .map { Json.decodeFromString<AttoTransaction>(it) }
            .doOnRequest { logger.info { "Sending to $uri $json" } }
            .doOnComplete { logger.info { "Sent request to $uri $json in ${System.currentTimeMillis() - timestamp}ms" } }
            .doOnError { e -> logger.error(e) { "Error sending to $uri $json" } }
            .timeout(Duration.ofSeconds(10))
            .retry(9)
            .blockFirst()

        Thread.sleep(1_000)
        println(counter.incrementAndGet())
    }

    override fun close() {
        running = false
    }


    private fun AttoAmount.randomSendAmount(): AttoAmount? {
        val raw = this.raw
        if (raw == 0UL) {
            return null
        } else if (raw == 1UL) {
            return this
        }
        val random = Random.Default.nextULong(1U, raw)
        return AttoAmount(random)
    }
}