package cash.atto

import cash.atto.commons.AttoTransaction
import cash.atto.node.NodeFactory
import cash.atto.node.NodeManager
import cash.atto.transaction.TransactionGenerator
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import kotlin.time.measureTime


class Benchmark


fun main(args: Array<String>) {
    val mb = 1024 * 1024
    println("Max Memory: ${Runtime.getRuntime().maxMemory() / mb} MB")
    println("Total Memory: ${Runtime.getRuntime().totalMemory() / mb} MB")
    println("Free Memory: ${Runtime.getRuntime().freeMemory() / mb} MB")

    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val timer = Timer.builder("transaction.confirmation.duration")
        .publishPercentileHistogram()
        .publishPercentiles(0.5, 0.95) // P50, P95
        .register(registry)

    val initialTransactions = readFrom("initialTransactions.zip")
    val genesisTransaction = initialTransactions[0]

    val benchmarkTransactions = readFrom("benchmarkTransactions.zip").groupBy { it.block.publicKey }

    val factory = NodeFactory(genesis = genesisTransaction, inMemory = false)
    val nodeManager = NodeManager(
        genesisPrivateKey = TransactionGenerator.genesisPrivateKey,
        tag = "main",
        factory = factory
    )
    nodeManager.start(voterNodeCount = 1U, nonVoterNodeCount = 0U)


    val url = "http://localhost:${nodeManager.getVoter().httpPort}"
//    val url = "http://localhost:8080"

    val publisher = TransactionPublisher(url = url).apply {
        start()
    }

    println("Sending initial transactions to the node...")
    runBlocking {
        initialTransactions
            .drop(1) // drop genesis
            .forEachIndexed { index, transaction ->
                publisher.publish(transaction)

                if ((index + 1) % 100 == 0) {
                    println("Sent ${index + 1}/${initialTransactions.size - 1} initial transactions")
                }
            }
    }
    println("${initialTransactions.size} initial transactions sent to the node")


    println("Starting load test...")

    val scope = CoroutineScope(Dispatchers.Default)

    val publishesPerSecond = ConcurrentHashMap<Long, AtomicInteger>()
    val started = Clock.System.now()

    val running = AtomicBoolean(true)
    val jobs = benchmarkTransactions.entries
        .map { (publicKey, transactions) ->
            scope.launch {
                transactions.forEach {
                    if (!running.get()) {
                        println("Benchmark failed, stopping ${it.block.publicKey}")
                        return@launch
                    }
                    val time = measureTime {
                        val success = publisher.publish(it)

                        running.compareAndSet(true, success)
                    }

                    timer.record(Duration.ofNanos(time.inWholeNanoseconds))

                    val second = Clock.System.now().epochSeconds
                    publishesPerSecond
                        .computeIfAbsent(second) { AtomicInteger() }
                        .incrementAndGet()
                }
            }
        }

    scope.launch {
        while (isActive) {
            delay(60_000)
            timer.printReport(started)
        }
    }

    runBlocking {
        jobs.forEach { it.join() }
    }

    timer.printReport(started)

    val peakSecond = publishesPerSecond.maxBy { it.value.get() }
    println("Peak TPS: ${peakSecond.value} at epoch second ${peakSecond.key}")
}

private fun Timer.printReport(started: Instant) {
    val testDuration = Clock.System.now() - started

    println("Count: ${this.count()}")
    println("Max: ${this.max(TimeUnit.SECONDS)}s")
    println("P50: ${this.percentile(0.5, TimeUnit.SECONDS)}s")
    println("P95: ${this.percentile(0.95, TimeUnit.SECONDS)}s")

    val totalTransactions = this.count().toDouble()
    val durationSeconds = testDuration.inWholeSeconds
    val averageCps = totalTransactions / durationSeconds
    println("Average CPS: %.2f".format(averageCps))
}

private fun readFrom(path: String): List<AttoTransaction> {
    println("Reading compressed file $path")
    val transactions = ArrayList<AttoTransaction>()

    val zipPath = Paths.get(path)
    val zipFile = ZipFile(zipPath.toFile())

    val entries = zipFile.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (!entry.isDirectory) {
            println("Reading: ${entry.name}")
            val inputStream = zipFile.getInputStream(entry)
            val reader = BufferedReader(InputStreamReader(inputStream))

            reader.useLines { lines ->
                lines.forEach { line ->
                    transactions.add(Json.decodeFromString(line))
                }
            }
        }
    }
    return transactions
}