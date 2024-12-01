package com.rankweis.uppercut.karate.schema;

import com.intellij.lang.javascript.EmbeddedJsonSchemaFileProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class KarateJsonSchemaProviderFactory implements JsonSchemaProviderFactory {

  static class KarateJsonSchemaFileProvider extends EmbeddedJsonSchemaFileProvider {
    public KarateJsonSchemaFileProvider(@NotNull VirtualFile file) {
      super(file);
    }

    @Override public @NotNull SchemaType getSchemaType() {
      return SchemaType.schema;
    }
  }

  @Override public @NotNull List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
    return List.of();
  }
}
