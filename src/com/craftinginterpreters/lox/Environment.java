package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    static class Uninit {
        private static final Uninit value = new Uninit();

        private Uninit() {}

        static Uninit value() {
            return value;
        }
    }

    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme())) {
            Object value = values.get(name.lexeme());
            if (value != Uninit.value()) return value;
            throw new RuntimeError(name,
                    "Use of uninitialized variable '" + name.lexeme() + "'.");
        }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme() + "'.");
    }

    void define(String name, Object value) {
        values.put(name, value);
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme())) {
            values.put(name.lexeme(), value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme() + "'.");
    }
}
