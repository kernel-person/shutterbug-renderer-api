package ke.ric.renderer.api;

import java.util.Objects;

/**
 * Immutable scene evidence overrides. Omitted creative controls retain the source scene's controls;
 * explicit values replace them, including world lighting and normal scene occlusion.
 * Primary visibility affects camera rays only, preserving lighting geometry.
 *
 * @param content evidence to retain; cannot restore evidence removed from the source
 * @param environment environment fields to override; unspecified fields inherit the source
 * @param appearance appearance fields to override; unspecified fields inherit the source
 * @param lighting replacement lighting, or null to inherit source lighting
 * @param occlusion replacement primary visibility, or null to inherit source visibility
 * @param primaryEntities primary-camera entity selection; null exactly when occlusion is null
 */
public record SceneTransform(SceneContent content,RenderEnvironment environment,RenderAppearance appearance,
                             RenderLighting lighting,Occlusion occlusion,EntitySelector primaryEntities) {
    public SceneTransform {
        Objects.requireNonNull(content,"content");
        Objects.requireNonNull(environment,"environment");
        Objects.requireNonNull(appearance,"appearance");
        if((occlusion==null)!=(primaryEntities==null))
            throw new IllegalArgumentException("occlusion and primaryEntities must both be omitted or both supplied");
    }

    /** Retains source creative lighting and primary visibility while applying these legacy overrides. */
    public SceneTransform(SceneContent content,RenderEnvironment environment,RenderAppearance appearance) {
        this(content,environment,appearance,null,null,null);
    }

    /** Retains all source evidence and settings. */
    public static SceneTransform defaults() {
        return new SceneTransform(SceneContent.all(),RenderEnvironment.captured(),RenderAppearance.defaults());
    }

    /** True only when every setting is inherited; explicit creative defaults still require application. */
    public boolean isIdentity() {
        return content.isAll()&&environment.isCaptured()&&appearance.isDefault()
                &&lighting==null&&occlusion==null&&primaryEntities==null;
    }
}
