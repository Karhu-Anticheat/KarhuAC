package me.liwk.karhu.util.file;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public final class SystemClassLoader {
    private static boolean REMIND = false;

    public static void setSystemClassloader(ClassLoader classloader) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Field scl = null;

        try {
            scl = ClassLoader.class.getDeclaredField("scl");
        } catch (NoSuchFieldException var11) {
            try {
                Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", Boolean.TYPE);
                getDeclaredFields0.setAccessible(true);
                Field[] fields = (Field[])((Field[])getDeclaredFields0.invoke(ClassLoader.class, false));
                Field[] var5 = fields;
                int var6 = fields.length;

                for(int var7 = 0; var7 < var6; ++var7) {
                    Field classField = var5[var7];
                    if ("scl".equals(classField.getName())) {
                        classField.setAccessible(true);
                        scl = classField;
                        break;
                    }
                }
            } catch (Throwable var10) {
                if (!REMIND) {
                    REMIND = true;
                    System.err.println("Unable to override class loader. please add --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED to your startup flag.");
                    System.err.println("Ignore if everything went perfectly.");
                }
            }
        }

        if (scl != null) {
            Unsafe unsafe;
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                unsafe = (Unsafe)theUnsafe.get(null);
                Class.forName(Lookup.class.getName(), true, null);
            } catch (Throwable var9) {
                throw new IllegalStateException("Unsafe not found");
            }

            long sclOffset = unsafe.staticFieldOffset(scl);
            unsafe.putObjectVolatile(ClassLoader.class, sclOffset, classloader);
        }
    }
}

