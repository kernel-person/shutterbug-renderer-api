# PNG and map conversions

## Convenient frame output

Use `frame.image()` for a newly allocated `BufferedImage`, `frame.savePng(path)` for asynchronous PNG encoding and atomic file replacement, or `frame.toMapItem()` for a provider-owned durable photo. `captureMap()` combines capture and map saving. The PNG parent directory must exist; unsupported atomic replacement or filesystem errors fail the future.

Saved-map delivery waits for persistence. One map is fitted with black aspect-preserving letterboxing; `MapOutputOptions` selects palette dithering. Raw capture and PNG output allocate no maps. See [saved photos](capture.md) for backup and ownership semantics.

## Direct conversion and multi-map output

The existing conversion utilities remain available for custom filters, palettes and multi-map grouping. A consumer-owned `MapSink` receives an immutable `MapFrame` containing fitted palette bytes and the original frame evidence. The consuming plugin owns filtered-image identity, tile grouping, item delivery and any archival format beyond explicit provider photo saving.

Run nontrivial conversion off the main thread. PNG encoding preserves the result, while map quantization returns one map-palette byte per pixel. Map tiles require both dimensions to be multiples of 128. Store or transmit the returned copies under your plugin's own bounds and retention policy.

```java
--8<-- "fixtures/ConversionExample.java"
```
