# node-benchmark

This is a work in progress (WIP). The goal is to benchmark the rate of confirmations per second. However, due to Atto's
asynchronous nature and the absence of network latency in a local setup, nodes may rapidly fall out of sync.

The primary cause of this issue, interestingly, involves two specific features of Atto:

### No Persistent Unconfirmed Transactions

Atto commits to saving only those transactions that have been confirmed. This means that even unchecked transactions
must be confirmed prior to database insertion. While this approach helps mitigate the effects of spam attacks, it also
leads to the dropping of any transactions deemed invalid (e.g., those lacking a confirmed preceding transaction or
receivable).

Moreover, each node maintains a minimal buffer, consisting of one previous and one receivable transaction, to manage
discrepancies arising from different node elections. However, this benchmark's dense web of transactions, compounded by
zero network latency, does not give nodes ample time to synchronize.

### Transaction Prioritization

In its operation, Atto categorizes transactions into groups called 'buckets' based on the whole part of the raw
transaction amount (for example, amounts like 1 or 5 fall into bucket 19, while 13 or 19 fall into bucket 18). Within
each bucket, transactions are prioritized according to the temporal gap between their timestamps and reception. The
narrower this gap, the higher the transaction's priority.

This system delays the processing of transactions for up to 100ms, a strategy designed to fight against spam. However,
this also inadvertently postpones the commencement of a transaction's election process.

### Broadcast Deduplication (resolved by connecting to all nodes)

Atto holds off on rebroadcasting a vote or transaction for up to 100ms. During this interval, it compiles a list of all
initial broadcasts associated with a specific transaction or vote. Following this pause, it proceeds to rebroadcast the
information to all nodes, excluding those from which the data had already been received.

As nodes spend this time discovering each other, a transaction must traverse one or more nodes before it can fully
propagate. This can delay the election process increasing the probability a new transaction is issued, further
complicating the synchronization between nodes.
