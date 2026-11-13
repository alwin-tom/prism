package com.prism.designlibrary;

import java.util.List;
import java.util.Optional;

public interface EmbeddingClient {

    boolean available();

    Optional<float[]> embed(String text);

    String modelName();

    int dimensions();
}
