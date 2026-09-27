# Paper capture

`capture` is asynchronous. Required chunks must already be loaded, and `chunksPerTick` limits main-thread capture work. Do not synchronously load cold chunks or block the server thread for capture or rendering. The completion continuation has no general Paper-thread guarantee; return to the main thread before reading or mutating Bukkit state. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.1/sample-plugin).

```java
--8<-- "fixtures/CaptureExample.java"
```
