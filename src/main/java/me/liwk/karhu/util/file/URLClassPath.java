package me.liwk.karhu.util.file;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

public class URLClassPath {
    private final KarhuClassLoader classLoader;

    URLClassPath(KarhuClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void addURL(URL url) {
        Field field;
        MethodHandles.Lookup lookup;
        Unsafe unsafe;
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe)theUnsafe.get(null);
            Class.forName(MethodHandles.Lookup.class.getName(), true, null);
            Field lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object lookupBase = unsafe.staticFieldBase(lookupField);
            long lookupOffset = unsafe.staticFieldOffset(lookupField);
            lookup = (MethodHandles.Lookup)unsafe.getObject(lookupBase, lookupOffset);
        }
        catch (Throwable t) {
            throw new IllegalStateException("Unsafe not found");
        }
        try {
            field = URLClassLoader.class.getDeclaredField("ucp");
        }
        catch (NoSuchFieldException e2) {
            throw new RuntimeException("Couldn't find ucp field from ClassLoader!");
        }
        try {
            long ucpOffset = unsafe.objectFieldOffset(field);
            Object ucp = unsafe.getObject((Object)this.classLoader, ucpOffset);
            MethodHandle methodHandle = lookup.findVirtual(ucp.getClass(), "addURL", MethodType.methodType((Class)Void.TYPE, URL.class));
            methodHandle.invoke(ucp, url);
        }
        catch (Throwable throwable) {
            throw new RuntimeException("Something wrong while adding dependency to class path!", throwable);
        }
    }
}
