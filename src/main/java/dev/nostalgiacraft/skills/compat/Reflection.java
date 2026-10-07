package dev.nostalgiacraft.skills.compat;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class Reflection {
    private Reflection() {
    }

    public static Object call(Object target, String method, Object... arguments) {
        return invoke(target.getClass(), target, method, arguments);
    }

    public static Object callStatic(String className, String method, Object... arguments) {
        return invoke(type(className), null, method, arguments);
    }

    public static Object field(Object target, String name) {
        return readField(target.getClass(), target, name);
    }

    public static Object staticField(String className, String name) {
        return readField(type(className), null, name);
    }

    public static boolean exists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    public static boolean isInstance(String className, Object candidate) {
        return type(className).isInstance(candidate);
    }

    private static Class<?> type(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("missing class " + className, failure);
        }
    }

    private static Object invoke(Class<?> owner, Object target, String method, Object[] arguments) {
        try {
            return find(owner, method, arguments).invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot call " + owner.getName() + "." + method, failure);
        }
    }

    private static Method find(Class<?> owner, String name, Object[] arguments) throws NoSuchMethodException {
        return Arrays.stream(owner.getMethods())
                .filter(method -> method.getName().equals(name) && accepts(method.getParameterTypes(), arguments))
                .findFirst().orElseThrow(() -> new NoSuchMethodException(owner.getName() + "." + name));
    }

    private static boolean accepts(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].isPrimitive() && !parameters[i].isInstance(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private static Object readField(Class<?> owner, Object target, String name) {
        try {
            return owner.getField(name).get(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("cannot read " + owner.getName() + "." + name, failure);
        }
    }
}
