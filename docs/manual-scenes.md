# Manual scenes

Use manual scenes only for complete caller-supplied engine-neutral evidence. Declare output dimensions, camera, world identity and height, weather, required chunks, every required section state, blocks, entities, structures, and resolved visuals needed by the scene. The builder rejects incomplete evidence instead of guessing. Scene instances belong to the client/provider generation that created them and must be rebuilt after reload. See [assets](assets.md) for pack ownership.

```java
--8<-- "fixtures/ManualSceneExample.java"
```
