package io.github.morganbyte.zpenz.api.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Un campo de texto no acepta números ni booleanos JSON (ADR-0009). Así {@code "amount": 70000.5}
 * se rechaza en lugar de convertirse en texto: un cliente JavaScript ya pudo perder centavos
 * al tratarlo como {@code double}.
 */
@Configuration
public class JsonConfig {

    @Bean
    JsonMapperBuilderCustomizer strictTextualFields() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
