package io.github.morganbyte.zpenz.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Datos generales del contrato OpenAPI (ADR-0009). */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "token";

    @Bean
    OpenAPI zpenzOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("zpenz API")
                        .version("v1")
                        .description("""
                                Finanzas personales con un libro contable de doble partida. El saldo no se edita: \
                                se calcula desde asientos que nunca se borran.

                                - Los montos son texto con hasta dos decimales, en pesos colombianos: `"70000.50"`.
                                - Todo `POST` exige `Idempotency-Key`.
                                - Los errores siguen *Problem Details* (RFC 9457) con un campo `code` estable.
                                - Hasta la Fase 5 la API exige un token compartido (botón *Authorize*).""")
                        .license(new License().name("Código en GitHub").url("https://github.com/morganbyte/zpenz")))
                // Relativo: el contrato guardado no depende del puerto ni del dominio donde corre.
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("El valor de `API_TOKEN` (ADR-0008). Se reemplaza por JWT en la Fase 5.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
