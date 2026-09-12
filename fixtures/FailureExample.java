package docs.examples;

import ke.ric.renderer.api.RendererException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class FailureExample {
    public static String code(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        return cause instanceof RendererException renderer ? renderer.code().name() : "UNEXPECTED";
    }
}
