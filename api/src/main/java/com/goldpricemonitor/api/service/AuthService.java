package com.goldpricemonitor.api.service;

import com.fasterxml.jackson.databind.*;
import com.goldpricemonitor.api.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.goldpricemonitor.api.service.ApiSupport.error;

@Service public class AuthService {
    public record Principal(int id,String role,String sessionId,Instant expiresAt) {}
    private final UserMapper users; private final UserConfigMapper configs; private final AdminAccountMapper admins; private final AuthSessionMapper sessions; private final ObjectMapper json;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(); private final byte[] secret; private final int interval; private final String publicBaseUrl;
    public AuthService(UserMapper users,UserConfigMapper configs,AdminAccountMapper admins,AuthSessionMapper sessions,ObjectMapper json,
        @Value("${app.jwt-secret:}") String secret,@Value("${app.monitor-interval-seconds:60}") int interval,@Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
        @Value("${app.admin-username:}") String adminUsername,@Value("${app.admin-password:}") String adminPassword) {
        if(secret.length()<32)throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 characters");
        this.users=users;this.configs=configs;this.admins=admins;this.sessions=sessions;this.json=json;this.secret=secret.getBytes(StandardCharsets.UTF_8);this.interval=interval;this.publicBaseUrl=publicBaseUrl.replaceAll("/$","");
        if(!adminUsername.isBlank()&&!adminPassword.isBlank()&&admins.byUsername(adminUsername)==null)admins.insert(adminUsername,passwords.encode(adminPassword));
    }
    public String phone(String value) {
        if(value==null)throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","手机号不能为空");
        String normalized=value.replaceAll("[\\s()-]",""); if(normalized.matches("1[3-9]\\d{9}"))normalized="+86"+normalized;
        if(!normalized.matches("\\+[1-9]\\d{7,14}"))throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","手机号格式无效");return normalized;
    }
    public String password(String value) {if(value==null||value.length()<8||value.length()>72)throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","密码长度应为8到72位");return passwords.encode(value);}
    private LocalDate date(Object value){return value==null?null:value instanceof LocalDate d?d:((java.sql.Date)value).toLocalDate();}
    private Instant instant(Object value){return value instanceof LocalDateTime d?d.toInstant(ZoneOffset.UTC):((java.sql.Timestamp)value).toInstant();}
    private boolean active(Map<String,Object> row){LocalDate now=LocalDate.now(ZoneOffset.UTC),from=date(row.get("valid_from_date")),to=date(row.get("valid_to_date"));return ((Number)row.get("status")).intValue()==1&&(from==null||!now.isBefore(from))&&(to==null||now.isBefore(to));}
    private String b64(byte[] data){return Base64.getUrlEncoder().withoutPadding().encodeToString(data);}
    private byte[] sign(String data){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> token(int id,String role){String sid=UUID.randomUUID().toString();Instant expiry=Instant.now().plus(Duration.ofHours(2));sessions.insert(sid,id,role,LocalDateTime.ofInstant(expiry,ZoneOffset.UTC));try{String header=b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));String payload=b64(json.writeValueAsBytes(Map.of("sub",String.valueOf(id),"role",role,"jti",sid,"exp",expiry.getEpochSecond())));String data=header+"."+payload;return Map.of("token",data+"."+b64(sign(data)),"expiresAt",expiry.toString(),"role",role,"userId",id);}catch(Exception e){throw new IllegalStateException(e);}}
    public Principal principal(HttpServletRequest request){String auth=request.getHeader("Authorization");if(auth==null||!auth.startsWith("Bearer "))throw error(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","需要有效令牌");try{String[] p=auth.substring(7).split("\\.");if(p.length!=3||!MessageDigest.isEqual(sign(p[0]+"."+p[1]),Base64.getUrlDecoder().decode(p[2])))throw new IllegalArgumentException();JsonNode node=json.readTree(Base64.getUrlDecoder().decode(p[1]));if(!"HS256".equals(json.readTree(Base64.getUrlDecoder().decode(p[0])).path("alg").asText()))throw new IllegalArgumentException();Instant expires=Instant.ofEpochSecond(node.path("exp").asLong());if(!expires.isAfter(Instant.now()))throw new IllegalArgumentException();int id=Integer.parseInt(node.path("sub").asText());String role=node.path("role").asText(),sid=node.path("jti").asText();Map<String,Object> session=sessions.find(sid,id,role);if(session==null||revoked(session.get("revoked"))||!instant(session.get("expires_at")).isAfter(Instant.now()))throw new IllegalArgumentException();if("USER".equals(role)){Map<String,Object> user=users.byId(id);if(user==null||!active(user))throw new IllegalArgumentException();}else if("ADMIN".equals(role)){if(admins.activeById(id)==null)throw new IllegalArgumentException();}else throw new IllegalArgumentException();return new Principal(id,role,sid,expires);}catch(Exception e){throw error(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","需要有效令牌");}}
    private boolean revoked(Object value){return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
    public Principal user(HttpServletRequest request){Principal p=principal(request);if(!p.role().equals("USER"))throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","权限不足");return p;}
    public Principal admin(HttpServletRequest request){Principal p=principal(request);if(!p.role().equals("ADMIN"))throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","权限不足");return p;}
    @Transactional public Map<String,Object> register(String phone,String password){String ph=phone(phone),hash=password(password);try{Map<String,Object> row=new HashMap<>();row.put("phone",ph);row.put("hash",hash);users.insert(row);int id=((Number)row.get("id")).intValue();configs.insert(id,interval,publicBaseUrl+"/api/v1/gold-price/latest");return Map.of("id",id,"phone",ph);}catch(DataIntegrityViolationException e){throw error(HttpStatus.CONFLICT,"PHONE_EXISTS","手机号已注册");}}
    public Map<String,Object> login(String phone,String password){Map<String,Object> user=users.byPhone(phone(phone));if(user==null||password==null||!passwords.matches(password,(String)user.get("password_hash"))||!active(user))throw error(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","账号或密码无效");return token(((Number)user.get("id")).intValue(),"USER");}
    public Map<String,Object> adminLogin(String username,String password){Map<String,Object> admin=admins.byUsername(username);if(admin==null||password==null||!passwords.matches(password,(String)admin.get("password_hash"))||((Number)admin.get("status")).intValue()!=1)throw error(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","账号或密码无效");return token(((Number)admin.get("id")).intValue(),"ADMIN");}
    public Map<String,Object> session(HttpServletRequest request){Principal p=principal(request);return Map.of("userId",p.id(),"role",p.role(),"expiresAt",p.expiresAt().toString());}
    @Transactional public Map<String,Object> refresh(HttpServletRequest request){Principal p=principal(request);if(sessions.revokeActive(p.sessionId())!=1)throw error(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","会话已失效");return token(p.id(),p.role());}
    public Map<String,Object> logout(HttpServletRequest request){Principal p=principal(request);sessions.revoke(p.sessionId());return Map.of("success",true);}
    public void revokeUser(int id){sessions.revokeUser(id);}
}
