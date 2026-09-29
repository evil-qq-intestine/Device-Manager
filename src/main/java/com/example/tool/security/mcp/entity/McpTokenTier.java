package com.example.tool.security.mcp.entity;

/**
 * 令牌等级。单值，存在主表的一列里（不需要子表）。
 *
 * <p>EXTERNAL 给外部客户端（Claude Desktop 等），TRUSTED 只给本机 bot。
 * 对应工具侧 authorities 里的 {@code TIER:EXTERNAL} / {@code TIER:TRUSTED}。
 */
public enum McpTokenTier {
    EXTERNAL,
    TRUSTED
}
