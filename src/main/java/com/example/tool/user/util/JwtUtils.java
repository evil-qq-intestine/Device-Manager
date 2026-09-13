package com.example.tool.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtils {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // 从配置的字符串生成签名密钥
    private SecretKey getSigningKey(){
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // 从Token中提取用户名
    public String extractUsername(String token){
        return extractClaim(token, Claims::getSubject);
    }

    // 从Token中提取过期时间
    public Date extractExpiration(String token){
        return extractClaim(token, Claims::getExpiration);
    }

    public Integer extractTokenVersion(String token){
        return extractClaim(token, claims -> claims.get("tokenVersion", Integer.class));
    }

    // 从Token中提取过期时间
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver){
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // 解析Token获取所有Claims
    public Claims extractAllClaims(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 检查Token是否过期
    public Boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }

    // 为用户生成Token
    public String generateToken(UserDetails userDetails){
        Map<String, Object> claims = new HashMap<>();
        // 可以在这里添加额外信息，比如用户角色
        if(userDetails instanceof CustomUserDetails customUserDetails){
            claims.put("userId", customUserDetails.getUserId());
            claims.put("tokenVersion", customUserDetails.getTokenVersion());
        }
        return createToken(claims, userDetails.getUsername());
    }

    // 创建Token
    public String createToken(Map<String, Object> claims, String subject){
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // 验证Token是否有效
    public Boolean validateToken(String token, UserDetails userDetails){
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
