package ke.ric.renderer.api;
/**
 * Coordinate system for explicit light positions and directions. Positions are measured in blocks.
 * Camera coordinates are right/up/forward and follow the effective frame camera, including roll;
 * they are resolved again when an existing capture is rendered from a new camera pose.
 */
public enum LightSpace {
 /** Absolute world coordinates; directions use world axes. */ WORLD,
 /** Offsets and directions relative to the effective camera: right, up and forward. */ CAMERA
}
