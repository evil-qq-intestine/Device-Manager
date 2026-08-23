package com.example.tool.device.util;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.beans.PropertyDescriptor;
import java.util.HashSet;
import java.util.Set;

public class BeanCopyUtils {

    // 1. 定义需要保护的字段（打死都不能被更新覆盖）
    private static final Set<String> PROTECTED_FIELDS = Set.of(
            "deviceId",   // 数据库主键
            "userId",     // 关联用户ID（防止被别人篡改归属）
            "class"       // Spring 内部属性
    );

    /**
     * 拷贝非空属性（只覆盖 source 中不为 null 的字段）
     * 注意：deviceId 和 userId 无论如何都不会被覆盖
     */
    public static void copyNonNullProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }

        BeanWrapper srcWrapper = new BeanWrapperImpl(source);
        BeanWrapper targetWrapper = new BeanWrapperImpl(target);

        // 2. 获取所有属性描述
        PropertyDescriptor[] pds = srcWrapper.getPropertyDescriptors();

        for (PropertyDescriptor pd : pds) {
            String propertyName = pd.getName();

            // 3. 跳过受保护的字段（deviceId, userId, class）
            if (PROTECTED_FIELDS.contains(propertyName)) {
                continue;
            }

            // 4. 检查源对象中该字段的值是否为 null
            Object value = srcWrapper.getPropertyValue(propertyName);
            if (value == null) {
                continue;
            }

            // 5. 检查目标对象是否支持写该字段（防止 read-only 属性报错）
            if (targetWrapper.isWritableProperty(propertyName)) {
                targetWrapper.setPropertyValue(propertyName, value);
            }
        }
    }
}