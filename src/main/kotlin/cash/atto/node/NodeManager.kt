package cash.atto.node

import cash.atto.commons.AttoPrivateKey
import mu.KotlinLogging

class NodeManager(
    private val genesisPrivateKey: AttoPrivateKey,
    private val tag: String,
    private val factory: NodeFactory,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    private val nodes = mutableListOf<Node>()
    private val nonVoterNodes = mutableListOf<Node>()
    private val voterNodes = mutableListOf<Node>()

    fun start(voterNodeCount: UInt, nonVoterNodeCount: UInt = 0U) {
        require(voterNodeCount > 0U) { "At least one voting node is required" }

        create(alwaysPull = true, privateKey = genesisPrivateKey)
        logger.info { "Started 1/${voterNodeCount} voting nodes" }
        for ((index, _) in (2U..voterNodeCount).withIndex()) {
            create(alwaysPull = false, privateKey = AttoPrivateKey.generate())
            logger.info { "Started ${index + 2}/${voterNodeCount} voting nodes" }
        }

        for ((index, _) in (1U..nonVoterNodeCount).withIndex()) {
            create(alwaysPull = false, privateKey = null)
            logger.info { "Started ${index + 1}/${nonVoterNodeCount} non-voting nodes" }

        }
    }

    private fun create(alwaysPull: Boolean, privateKey: AttoPrivateKey?) {
        val defaultNodes = nodes.map {
            "ws://${it.id}:8082"
        }
        val node = factory.create(tag, alwaysPull, privateKey, defaultNodes)
        nodes.add(node)
        if (privateKey != null) {
            voterNodes.add(node)
        } else {
            nonVoterNodes.add(node)
        }
    }

    fun getSingle(): Node {
        return nodes.random()
    }

    fun getVoter(): Node {
        return voterNodes.random()
    }

    fun getNonVoter(): Node {
        return nonVoterNodes.random()
    }

    override fun close() {
        nodes.forEach { it.close() }
    }

}