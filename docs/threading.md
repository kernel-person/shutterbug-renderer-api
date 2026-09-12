# Threading

Capture batches Paper reads on the main thread, but continuations have no general Paper-thread guarantee. Keep continuations small, perform conversion or storage on your executor, and schedule Bukkit world or player mutation back to the server main thread. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).
