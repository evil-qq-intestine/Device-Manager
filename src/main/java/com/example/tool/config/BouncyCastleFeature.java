package com.example.tool.config;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * Native 构建期把 BouncyCastle 注册进 java.security.Security。
 *
 * <p>MINA SSHD 运行时会注册 BouncyCastle。GraalVM 的 SecurityServicesFeature 只在构建期
 * beforeAnalysis 扫描 Security.getProviders()，此时 BC 尚未注册就不会把它的验证结果写进镜像，
 * 运行时首次使用 SSH 会抛：
 * SecurityException: Attempted to verify a provider that was not registered at build time: BC ...
 *
 * <p>本 Feature 在 duringSetup（早于 beforeAnalysis）注册 BC，使 GraalVM 能在构建期完成登记与验证。
 * 该选项 -H:AdditionalSecurityProviders 本身不会注册 Provider，只是防止已注册的 Provider 被移除。
 */
public class BouncyCastleFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
