# PNG and map conversions

Run nontrivial conversion off the main thread. PNG encoding preserves the result, while map quantization returns one map-palette byte per pixel. Map tiles require both dimensions to be multiples of 128. See the [complete sample plugin](https://github.com/kernel-person/shutterbug-renderer-api/tree/v1.0.0/sample-plugin).

```java
--8<-- "fixtures/ConversionExample.java"
```
