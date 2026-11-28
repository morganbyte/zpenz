package io.github.morganbyte.zpenz;

import org.springframework.boot.SpringApplication;

/**
 * Arranca la aplicación en local con un PostgreSQL de Testcontainers, sin instalar nada:
 * {@code ./gradlew :app-service:bootTestRun}. Necesita Docker.
 */
public class LocalMainApplication {

    public static void main(String[] args) {
        SpringApplication.from(MainApplication::main).with(PostgresTestConfiguration.class).run(args);
    }
}
