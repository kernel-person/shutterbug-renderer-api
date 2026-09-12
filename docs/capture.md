# Paper capture

`capture` is asynchronous. Required chunks must already be loaded, and `chunksPerTick` limits main-thread capture work. Do not block the server thread for capture or rendering. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).

```java
--8<-- "fixtures/CaptureExample.java"
```
