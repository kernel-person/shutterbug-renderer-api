# Threading

Capture batches Paper reads on the main thread, but continuations have no general Paper-thread guarantee. Keep continuations small, perform conversion or storage on your bounded executor, and schedule Bukkit world or player mutation back to the server main thread. Never block the main thread waiting for a capture or render future. Cancellation, disable, and provider reload may race with continuations, so verify the current client generation before publishing results.
