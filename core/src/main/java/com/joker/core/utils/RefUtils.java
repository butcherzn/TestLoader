package com.joker.core.utils;

import android.support.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by joker on 2017/7/11.
 */

public class RefUtils {

    public static Object getStaticField(Class clazz, String fieldName) throws Exception {
        return getField(clazz, null, fieldName);
    }

    public static Object getStaticFieldSafely(Class clazz, String fieldName) {
        try {
            return getStaticField(clazz, fieldName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * @param clazz
     * @param target
     * @param fieldName
     * @return
     * @throws Exception
     */
    public static Object getField(Class clazz, Object target, String fieldName)
            throws Exception {
        Field declaredField = clazz.getDeclaredField(fieldName);
        declaredField.setAccessible(true);
        return declaredField.get(target);
    }


    /**
     * @param clazz
     * @param target
     * @param fieldName
     * @param replaceFieldObject
     * @throws Exception
     */
    public static void setField(Class clazz, Object target, String fieldName, Object replaceFieldObject)
            throws Exception {
        Field declaredField = clazz.getDeclaredField(fieldName);
        declaredField.setAccessible(true);
        declaredField.set(target, replaceFieldObject);
    }

    /**
     * @param clazz
     * @param target
     * @param fieldName
     * @return
     */
    @Nullable
    public static Object getFieldSafely(Class clazz, Object target, String fieldName) {
        try {
            return getField(clazz, target, fieldName);
        } catch (Exception e) {

        }
        return null;
    }

    /**
     * @param clazz
     * @param target
     * @param fieldName
     * @param replace
     */
    public static void setFieldSafely(Class clazz, Object target, String fieldName, Object replace) {
        try {
            setField(clazz, target, fieldName, replace);
        } catch (Exception e) {

        }
    }


    @SuppressWarnings("unchecked")
    public static Object invoke(Class clazz, Object target, String methodName, Object... args)
            throws Exception {
        Class[] parameterTypes = getParameterTypes(args);
        return invoke(clazz, target, methodName, parameterTypes, args);
    }


    @Nullable
    private static Class[] getParameterTypes(Object[] args) {
        Class[] parameterTypes = null;
        if (args != null) {
            int length = args.length;
            parameterTypes = new Class[length];
            for (int i = 0; i < length; i++) {
                parameterTypes[i] = args[i].getClass();
            }
        }
        return parameterTypes;
    }


    /**
     * @param clazz
     * @param target
     * @param methodName
     * @param parameterTypes
     * @param args
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Object invoke(Class clazz, Object target, String methodName, Class[] parameterTypes, Object... args)
            throws Exception {
        Method declaredMethod = clazz.getDeclaredMethod(methodName, parameterTypes);
        declaredMethod.setAccessible(true);
        return declaredMethod.invoke(target, args);
    }

    @Nullable
    public static Object invokeSafely(Class clazz, Object target, String methodName, Object... args) {
        try {
            return invoke(clazz, target, methodName, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    @Nullable
    public static Object invokeSafely(Class clazz, Object target, String methodName, Class[] parameterTypes, Object... args) {
        try {
            return invoke(clazz, target, methodName, parameterTypes, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Object newInstance(Class clazz, Object... args)
            throws Exception {
        Class[] parameterTypes = getParameterTypes(args);
        return clazz.getDeclaredConstructor(parameterTypes);
    }


    public static Object newInstanceSafely(Class clazz, Object... args) {
        try {
            return newInstance(clazz, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


}
