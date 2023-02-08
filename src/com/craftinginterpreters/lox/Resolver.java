package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private enum FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD,
    }

    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS,
    }

    private sealed interface ResolveType
            permits ResolveType.Declared, ResolveType.Defined, ResolveType.Used {
        record Declared() implements ResolveType {}
        record Defined(int line) implements ResolveType {}
        record Used() implements ResolveType {}
    }

    private final Interpreter interpreter;
    private final Stack<Map<String, ResolveType>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;
    private boolean inLoop = false;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        Map<String, ResolveType> scope = scopes.pop();
        for (Map.Entry<String,ResolveType> entry : scope.entrySet()) {
            var value = entry.getValue();
            if (value instanceof ResolveType.Defined) {
                var line = ((ResolveType.Defined)value).line();
                Lox.error(line,
                        "Local variable " + entry.getKey() + " not used.");
            }
        }
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) return;
        Map<String, ResolveType> scope = scopes.peek();
        if (scope.containsKey(name.lexeme())) {
            Lox.error(name,
                    "Already a variable with this name in this scope.");
        }
        scope.put(name.lexeme(), new ResolveType.Declared());
    }

    private void define(Token name) {
        if (scopes.isEmpty()) return;
        var scope = scopes.peek();
        var info = scope.get(name.lexeme());
        scope.put(name.lexeme(),
                new ResolveType.Defined(name.line()));
    }

    private void use(Token name) {
        if (scopes.isEmpty()) return;
        var scope = scopes.peek();
        var info = scope.get(name.lexeme());
        scope.put(name.lexeme(), new ResolveType.Used());
    }

    private void resolveLocal(Expr expr, Token name) {
        resolveLocal(expr, name, true);
    }

    private void resolveLocal(Expr expr, Token name, boolean isUse) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Map<String, ResolveType> scope = scopes.get(i);
            var info = scope.get(name.lexeme());
            if (info != null) {
                if (isUse) {
                    scope.put(name.lexeme(), new ResolveType.Used());
                }
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    private void resolveFunction(Expr.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();
        for (Token param : function.params) {
            declare(param);
            define(param);
            use(param);
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name, false);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitFunctionExpr(Expr.Function expr) {
        resolveFunction(expr, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        resolveLocal(expr, expr.keyword);
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'super' outside of a class.");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword,
                    "Can't use 'super' in a class with no superclass.");
        }
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword,
                    "Can't use 'this' outside of a class.");
            return null;
        }

        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() &&
                scopes.peek().get(expr.name.lexeme()) instanceof ResolveType.Declared) {
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }

        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.cond);
        resolve(expr.ifTrue);
        resolve(expr.ifFalse);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;

        declare(stmt.name);
        define(stmt.name);

        if (stmt.superClass != null &&
                stmt.name.lexeme().equals(stmt.superClass.name.lexeme())) {
            Lox.error(stmt.superClass.name,
                    "A class can't inherit from itself.");
        }

        if (stmt.superClass != null) {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superClass);
            beginScope();
            scopes.peek().put("super", new ResolveType.Used());
        }

        beginScope();
        var scope = scopes.peek();
        scope.put("this", new ResolveType.Declared());

        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.function.name.lexeme().equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            resolveFunction(method.function, declaration);
        }
        for (Stmt.Function method : stmt.classMethods) {
            FunctionType declaration = FunctionType.METHOD;
            resolveFunction(method.function, declaration);
        }

        endScope();

        if (stmt.superClass != null) {
            endScope();
        }

        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        if (!inLoop) {
            Lox.error(stmt.keyword,
                    "Can't break outside of loop body.");
        }
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        Expr.Function function = stmt.function;
        declare(function.name);
        define(function.name);

        resolveFunction(function, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword,
                        "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        boolean enclosingLoop = inLoop;
        inLoop = true;
        resolve(stmt.body);
        inLoop = enclosingLoop;
        return null;
    }
}
