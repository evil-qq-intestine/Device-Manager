package com.example.tool.scripttask.entity;

public enum TriggerType {
    ONCE,
    CRON,
    ON_BOOT,
    /** 永不自动运行，只能在管理界面手动点击执行。 */
    MANUAL
}
