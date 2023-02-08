package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
    final String name;
    final LoxClass superClass;
    private final Map<String, LoxFunction> methods;

    LoxClass(String name, LoxClass superClass, Map<String, LoxFunction> methods,
             Map<String, LoxFunction> classMethods) {
        super(new LoxClass(name + "_class", classMethods));
        this.name = name;
        this.methods = methods;
        this.superClass = superClass;
    }


    LoxClass(String name, Map<String, LoxFunction> classMethods) {
        super();
        this.name = name;
        this.methods = classMethods;
        this.superClass = null;
    }

    LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        if (superClass != null) {
            return superClass.findMethod(name);
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }
        return instance;
    }
}
