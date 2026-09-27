# PNG and map conversions

Run nontrivial conversion off the main thread. PNG encoding preserves the result, while map quantization returns one map-palette byte per pixel. Map tiles require both dimensions to be multiples of 128. Store or transmit the returned copies under your plugin's own bounds and retention policy.

```java
--8<-- "fixtures/ConversionExample.java"
```
