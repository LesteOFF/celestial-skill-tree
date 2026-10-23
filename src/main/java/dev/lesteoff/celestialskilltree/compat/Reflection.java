package dev.lesteoff.celestialskilltree.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Reflection {
    private static final Map<String, Class<?>> TYPES = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Method> METHODS = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Set<Class<?>>> WIDENS_FROM = Map.of(
            boolean.class, Set.of(Boolean.class),
            char.class, Set.of(Character.class),
            byte.class, Set.of(Byte.class),
            short.class, Set.of(Short.class, Byte.class),
            int.class, Set.of(Integer.class, Short.class, Byte.class, Character.class),
            long.class, Set.of(Long.class, Integer.class, Short.class, Byte.class, Character.class),
            float.class, Set.of(Float.class, Long.class, Integer.class, Short.class, Byte.class, Character.class),
            double.class, Set.of(Double.class, Float.class, Long.class, Integer.class, Short.class, Byte.class, Character.class));

    public static final class Failure extends IllegalStateException {
        Failure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record MethodKey(Class<?> owner, String name, List<Class<?>> argumentTypes) {
    }

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

    public static Object hiddenStaticField(String className, String name) {
        try {
            Field field = type(className).getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new Failure("cannot read " + className + "." + name, failure);
        }
    }

    public static Object construct(String className, Object... arguments) {
        Class<?> owner = type(className);
        try {
            return Arrays.stream(owner.getConstructors()).filter(candidate -> accepts(candidate.getParameterTypes(), arguments))
                    .findFirst().orElseThrow(() -> new NoSuchMethodException(className)).newInstance(arguments);
        } catch (ReflectiveOperationException failure) {
            throw new Failure("cannot construct " + className, failure);
        }
    }

    public static void requireTypes(String... classNames) {
        for (String className : classNames) {
            try {
                Class.forName(className, false, Reflection.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError missing) {
                throw new Failure("missing class " + className, missing);
            }
        }
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

    public static Class<?> type(String className) {
        Class<?> known = TYPES.get(className);
        if (known != null) {
            return known;
        }
        try {
            Class<?> found = Class.forName(className);
            TYPES.put(className, found);
            return found;
        } catch (ClassNotFoundException failure) {
            throw new Failure("missing class " + className, failure);
        }
    }

    private static Object invoke(Class<?> owner, Object target, String method, Object[] arguments) {
        try {
            return cachedMethod(owner, method, arguments).invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new Failure("cannot call " + owner.getName() + "." + method, failure);
        }
    }

    private static Method cachedMethod(Class<?> owner, String name, Object[] arguments) throws NoSuchMethodException {
        MethodKey key = new MethodKey(owner, name, Arrays.stream(arguments).<Class<?>>map(argument -> argument == null ? null : argument.getClass()).toList());
        Method known = METHODS.get(key);
        if (known != null) {
            return known;
        }
        Method found = find(owner, name, arguments);
        METHODS.put(key, found);
        return found;
    }

    private static Method find(Class<?> owner, String name, Object[] arguments) throws NoSuchMethodException {
        return Arrays.stream(owner.getMethods())
                .filter(method -> method.getName().equals(name) && accepts(method.getParameterTypes(), arguments))
                .findFirst().map(method -> publicDeclaration(owner, method))
                .orElseThrow(() -> new NoSuchMethodException(owner.getName() + "." + name));
    }

    private static Method publicDeclaration(Class<?> owner, Method method) {
        if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            return method;
        }
        for (Class<?> contract : owner.getInterfaces()) {
            try {
                return contract.getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException notDeclaredHere) {
                continue;
            }
        }
        return method;
    }

    private static boolean accepts(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!accepts(parameters[i], arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean accepts(Class<?> parameter, Object argument) {
        if (parameter.isPrimitive()) {
            return argument != null && WIDENS_FROM.get(parameter).contains(argument.getClass());
        }
        return argument == null || parameter.isInstance(argument);
    }

    private static Object readField(Class<?> owner, Object target, String name) {
        try {
            return owner.getField(name).get(target);
        } catch (ReflectiveOperationException failure) {
            throw new Failure("cannot read " + owner.getName() + "." + name, failure);
        }
    }
}
