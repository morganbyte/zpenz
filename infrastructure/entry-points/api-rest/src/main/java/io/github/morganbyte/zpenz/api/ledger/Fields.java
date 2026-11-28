package io.github.morganbyte.zpenz.api.ledger;

/** Validación de los campos obligatorios de un cuerpo JSON. */
final class Fields {

    private Fields() {
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Falta el campo \"" + field + "\"");
        }
        return value;
    }
}
