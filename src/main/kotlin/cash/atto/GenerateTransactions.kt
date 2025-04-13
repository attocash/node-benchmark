package cash.atto

import cash.atto.commons.AttoTransaction
import cash.atto.transaction.TransactionGenerator
import kotlinx.serialization.json.Json
import java.io.File


class GenerateTransactions

fun main() {
    val transactionGenerator = TransactionGenerator(accountCount = 1000U, totalTransactions = 200_000U)

    val initialTransactions = transactionGenerator.prepare()
    initialTransactions.writeTo("initialTransactions.jsonl")

    val benchmarkTransactions = transactionGenerator.generate()
    benchmarkTransactions.values.flatten().writeTo("benchmarkTransactions.jsonl")
}

private fun Collection<AttoTransaction>.writeTo(path: String) {
    File(path).printWriter().use { writer ->
        this.forEach { transaction ->
            writer.println(Json.encodeToString(transaction))
        }
    }
}