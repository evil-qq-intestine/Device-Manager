package com.example.tool.wake.util;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.HashSet;
import java.util.Set;

public class BeanCopyUtils {

    // 在 BeanCopyUtils 里加一行硬编码排除
    private static final Set<String> IGNORE_PROPERTIES = Set.of("id", "class");

    /**
     * 拷贝非空属性（核心：把 source 中不为 null 的值赋给 target）
     */
    public static void copyNonNullProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        // 1. 获取 source 中所有为 null 的属性名
        BeanWrapper srcWrapper = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = srcWrapper.getPropertyDescriptors();
        Set<String> nullPropertyNames = new HashSet<>();
        for (PropertyDescriptor pd : pds) {
            String propertyName = pd.getName();
            // 排除 class 属性
            if (IGNORE_PROPERTIES.contains(propertyName) && srcWrapper.getPropertyValue(propertyName) == null) {
                nullPropertyNames.add(propertyName);
            }
        }
        // 2. 执行拷贝，但忽略这些为 null 的属性（即不覆盖 target 的旧值）
        BeanWrapper targetWrapper = new BeanWrapperImpl(target);
        for (PropertyDescriptor pd : pds) {
            String propertyName = pd.getName();
            if (IGNORE_PROPERTIES.contains(propertyName) && !nullPropertyNames.contains(propertyName)) {
                Object value = srcWrapper.getPropertyValue(propertyName);
                targetWrapper.setPropertyValue(propertyName, value);
            }
        }
    }
}