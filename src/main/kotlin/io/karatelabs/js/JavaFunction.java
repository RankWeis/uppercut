package io.karatelabs.js;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class JavaFunction implements Invokable {

    final Invokable invokable;

    @SuppressWarnings("unchecked")
    JavaFunction(Object o) {
        if (o instanceof Function) {
            invokable = args -> ((Function<Object, Object>) o).apply(args[0]);
        } else if (o instanceof Runnable) {
            invokable = args -> {
                ((Runnable) o).run();
                return null;
            };
        } else if (o instanceof Callable) {
            invokable = args -> {
                try {
                    return ((Callable<Object>) o).call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        } else if (o instanceof Consumer) {
            invokable = args -> {
                ((Consumer<Object>) o).accept(args[0]);
                return null;
            };
        } else if (o instanceof Supplier) {
            invokable = args -> ((Supplier<Object>) o).get();
        } else if (o instanceof Predicate) {
            invokable = args -> ((Predicate<Object>) o).test(args[0]);
        } else {
            throw new RuntimeException("cannot convert to java function: " + o);
        }
    }

    static boolean isFunction(Object o) {
        return o instanceof Function
                || o instanceof Runnable
                || o instanceof Callable
                || o instanceof Consumer
                || o instanceof Supplier
                || o instanceof Predicate;
    }

    @Override
    public Object invoke(Object... args) {
        return invokable.invoke(args);
    }

}
