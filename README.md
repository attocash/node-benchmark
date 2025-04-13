# Node Benchmark

This is the first version of the node benchmark.

Note: Initial transactions itself does not affect the CPS (confirmations per second) calculation—only the actual
benchmark transactions and their subsequent confirmations are measured.

Currently, the benchmark focuses on a single node. This choice reduces database load because all nodes share the same
database. In earlier attempts, I tried running one database per node, but the database quickly became a bottleneck.
Using a single database made the benchmark run faster overall, though it doesn’t fully reflect real-world behavior. To
keep things simple for now, I decided to focus on a single-node setup rather than testing multiple nodes on the same
machine.

## How to run?

`./gradlew run`

Requirements: Docker or Podman