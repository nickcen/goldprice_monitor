package com.goldpricemonitor.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

// Legacy implementation retained for reference; the controllers in admin/ and user/
// own the HTTP routes. Registering both implementations causes duplicate mappings.
public class ApiController {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final byte[] secret;
    private final String priceSource, currency, unit, publicBaseUrl;
    private final int interval;
    private final RestClient http = RestClient.builder().requestFactory(requestFactory()).build();
    private static SimpleClientHttpRequestFactory requestFactory() { var f=new SimpleClientHttpRequestFactory(); f.setConnectTimeout(Duration.ofSeconds(3)); f.setReadTimeout(Duration.ofSeconds(5)); return f; }

    public ApiController(JdbcTemplate db, ObjectMapper json,
        @Value("${app.jwt-secret:}") String secret,
        @Value("${app.price-source-url:}") String priceSource,
        @Value("${app.currency:CNY}") String currency,
        @Value("${app.unit:gram}") String unit,
        @Value("${app.monitor-interval-seconds:60}") int interval,
        @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
        @Value("${app.admin-username:}") String adminUsername,
        @Value("${app.admin-password:}") String adminPassword) {
        if (secret.length() < 32) throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 characters");
        this.db=db; this.json=json; this.secret=secret.getBytes(StandardCharsets.UTF_8);
        this.priceSource=priceSource; this.currency=currency; this.unit=unit;
        this.interval=interval; this.publicBaseUrl=publicBaseUrl.replaceAll("/$", "");
        if (!adminUsername.isBlank() && !adminPassword.isBlank()) {
            Integer count=db.queryForObject("SELECT count(*) FROM admin_account WHERE username=?", Integer.class, adminUsername);
            if (count==0) db.update("INSERT INTO admin_account(username,password_hash) VALUES (?,?)", adminUsername,passwords.encode(adminPassword));
        }
    }

    record Login(String phone, String password) {}
    record AdminLogin(String username, String password) {}
    record Register(String phone, String password) {}
    record PasswordChange(String password) {}
    record StatusChange(Integer status) {}
    record ValidityChange(LocalDate validFromDate, LocalDate validToDate) {}
    record SkuInput(String name, Integer price, Integer status, Integer sortOrder) {}
    record Principal(int id, String role, String sessionId, Instant expiresAt) {}

    private ApiFault error(HttpStatus status,String code,String message) { return new ApiFault(status,code,message); }
    private String phone(String value) {
        if (value==null) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","手机号不能为空");
        String normalized=value.replaceAll("[\\s()-]","");
        if (normalized.matches("1[3-9]\\d{9}")) normalized="+86"+normalized;
        if (!normalized.matches("\\+[1-9]\\d{7,14}")) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","手机号格式无效");
        return normalized;
    }
    private String password(String value) {
        if (value==null || value.length()<8 || value.length()>72) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","密码长度应为8到72位");
        return passwords.encode(value);
    }
    private Instant instant(Object value) { return value instanceof LocalDateTime d?d.toInstant(ZoneOffset.UTC):((java.sql.Timestamp)value).toInstant(); }
    private LocalDate date(Object value) { return value==null?null:value instanceof LocalDate d?d:((java.sql.Date)value).toLocalDate(); }
    private boolean active(Map<String,Object> row) {
        LocalDate now=LocalDate.now(ZoneOffset.UTC);
        LocalDate from=date(row.get("valid_from_date")), to=date(row.get("valid_to_date"));
        return ((Number)row.get("status")).intValue()==1 && (from==null || !now.isBefore(from)) && (to==null || now.isBefore(to));
    }
    private List<Map<String,Object>> rows(String sql,Object... args) { return db.queryForList(sql,args); }
    private Map<String,Object> one(String sql,Object... args) { List<Map<String,Object>> r=rows(sql,args); return r.isEmpty()?null:r.getFirst(); }
    private String b64(byte[] data) { return Base64.getUrlEncoder().withoutPadding().encodeToString(data); }
    private byte[] sign(String data) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return mac.doFinal(data.getBytes(StandardCharsets.UTF_8)); }
        catch(Exception e){ throw new IllegalStateException(e); }
    }
    private Map<String,Object> token(int id,String role) {
        String sid=UUID.randomUUID().toString(); Instant expiry=Instant.now().plus(Duration.ofHours(2));
        db.update("INSERT INTO auth_session(id,principal_id,role,expires_at) VALUES (?,?,?,?)",sid,id,role,LocalDateTime.ofInstant(expiry,ZoneOffset.UTC));
        try {
            String header=b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload=b64(json.writeValueAsBytes(Map.of("sub",String.valueOf(id),"role",role,"jti",sid,"exp",expiry.getEpochSecond())));
            String data=header+"."+payload;
            return Map.of("token",data+"."+b64(sign(data)),"expiresAt",expiry.toString(),"role",role,"userId",id);
        } catch(Exception e){ throw new IllegalStateException(e); }
    }
    private Principal principal(HttpServletRequest request) {
        String auth=request.getHeader("Authorization");
        if (auth==null || !auth.startsWith("Bearer ")) throw error(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","需要有效令牌");
        try {
            String[] p=auth.substring(7).split("\\.");
            if(p.length!=3 || !MessageDigest.isEqual(sign(p[0]+"."+p[1]),Base64.getUrlDecoder().decode(p[2]))) throw new IllegalArgumentException();
            JsonNode node=json.readTree(Base64.getUrlDecoder().decode(p[1]));
            if(!"HS256".equals(json.readTree(Base64.getUrlDecoder().decode(p[0])).path("alg").asText())) throw new IllegalArgumentException();
            Instant expires=Instant.ofEpochSecond(node.path("exp").asLong());
            if(!expires.isAfter(Instant.now())) throw new IllegalArgumentException();
            int id=Integer.parseInt(node.path("sub").asText()); String role=node.path("role").asText(), sid=node.path("jti").asText();
            Map<String,Object> session=one("SELECT * FROM auth_session WHERE id=? AND principal_id=? AND role=?",sid,id,role);
            if(session==null || ((Boolean)session.get("revoked")) || !instant(session.get("expires_at")).isAfter(Instant.now())) throw new IllegalArgumentException();
            if("USER".equals(role)) { Map<String,Object> user=one("SELECT * FROM `user` WHERE id=?",id); if(user==null || !active(user)) throw new IllegalArgumentException(); }
            else if("ADMIN".equals(role)) { Map<String,Object> admin=one("SELECT * FROM admin_account WHERE id=? AND status=1",id); if(admin==null) throw new IllegalArgumentException(); }
            else throw new IllegalArgumentException();
            return new Principal(id,role,sid,expires);
        } catch(Exception e){ throw error(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","需要有效令牌"); }
    }
    private Principal admin(HttpServletRequest r) { Principal p=principal(r); if(!p.role.equals("ADMIN")) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","权限不足"); return p; }
    private Principal user(HttpServletRequest r) { Principal p=principal(r); if(!p.role.equals("USER")) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","权限不足"); return p; }

    @Transactional
    @PostMapping("/auth/register") public Map<String,Object> register(@RequestBody Register body) {
        String ph=phone(body.phone()), hash=password(body.password());
        try {
            db.update("INSERT INTO `user`(phone,password_hash) VALUES (?,?)",ph,hash);
            int id=db.queryForObject("SELECT id FROM `user` WHERE phone=?",Integer.class,ph);
            db.update("INSERT INTO user_config(user_id,monitor_interval_seconds,level) VALUES (?,?,0)",id,interval);
            return Map.of("id",id,"phone",ph);
        } catch(DataIntegrityViolationException e){ throw error(HttpStatus.CONFLICT,"PHONE_EXISTS","手机号已注册"); }
    }
    @PostMapping("/auth/login") public Map<String,Object> login(@RequestBody Login body) {
        Map<String,Object> u=one("SELECT * FROM `user` WHERE phone=?",phone(body.phone()));
        if(u==null || body.password()==null || !passwords.matches(body.password(),(String)u.get("password_hash")) || !active(u)) throw error(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","账号或密码无效");
        return token(((Number)u.get("id")).intValue(),"USER");
    }
    @PostMapping("/admin/auth/login") public Map<String,Object> adminLogin(@RequestBody AdminLogin body) {
        Map<String,Object> u=one("SELECT * FROM admin_account WHERE username=?",body.username());
        if(u==null || body.password()==null || !passwords.matches(body.password(),(String)u.get("password_hash")) || ((Number)u.get("status")).intValue()!=1) throw error(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","账号或密码无效");
        return token(((Number)u.get("id")).intValue(),"ADMIN");
    }
    @GetMapping("/auth/session") public Map<String,Object> session(HttpServletRequest r) { Principal p=principal(r); return Map.of("userId",p.id,"role",p.role,"expiresAt",p.expiresAt.toString()); }
    @Transactional
    @PostMapping("/auth/refresh") public Map<String,Object> refresh(HttpServletRequest r) { Principal p=principal(r); int changed=db.update("UPDATE auth_session SET revoked=true WHERE id=? AND revoked=false",p.sessionId); if(changed!=1) throw error(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","会话已失效"); return token(p.id,p.role); }
    @PostMapping("/auth/logout") public Map<String,Object> logout(HttpServletRequest r) { Principal p=principal(r); db.update("UPDATE auth_session SET revoked=true WHERE id=?",p.sessionId); return Map.of("success",true); }

    private Map<String,Object> config(int id) {
        Map<String,Object> c=one("SELECT * FROM user_config WHERE user_id=?",id);
        if(c==null) throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","配置不存在");
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("level",c.get("level")); out.put("monitorIntervalSeconds",c.get("monitor_interval_seconds"));
        out.put("minPrice",c.get("min_price")==null?null:((BigDecimal)c.get("min_price")).toPlainString()); out.put("maxPrice",c.get("max_price")==null?null:((BigDecimal)c.get("max_price")).toPlainString());
        out.put("unit",unit); out.put("currency",currency); out.put("precision",2);
        return out;
    }
    @GetMapping("/me/config") public Map<String,Object> getConfig(HttpServletRequest r) { return config(user(r).id); }
    @PatchMapping("/me/config") public Map<String,Object> updateConfig(HttpServletRequest r,@RequestBody JsonNode body) {
        int id=user(r).id; if(!body.isObject()) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","请求必须为对象");
        Map<String,Object> old=config(id);
        BigDecimal min=body.has("minPrice")?decimal(body.get("minPrice")):(BigDecimal)old.get("minPrice");
        BigDecimal max=body.has("maxPrice")?decimal(body.get("maxPrice")):(BigDecimal)old.get("maxPrice");
        if(min!=null && max!=null && min.compareTo(max)>=0) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","低价必须小于高价");
        db.update("UPDATE user_config SET min_price=?,max_price=? WHERE user_id=?",min,max,id); return config(id);
    }
    private BigDecimal decimal(JsonNode node) {
        if(node.isNull()) return null;
        if(!node.isNumber() && !node.isTextual()) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","价格格式无效");
        try { BigDecimal n=new BigDecimal(node.asText()); if(n.signum()<0 || n.scale()>2 || n.precision()>18) throw new NumberFormatException(); return n; }
        catch(NumberFormatException e){ throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","价格须为非负且最多两位小数"); }
    }
    @GetMapping("/gold-price/latest") public Map<String,Object> latest(HttpServletRequest r) {
        user(r);
        if(priceSource.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE,"PRICE_SOURCE_UNAVAILABLE","金价数据源未配置");
        try {
            JsonNode node=http.get().uri(priceSource).retrieve().body(JsonNode.class);
            return priceResponse(node);
        } catch(Exception e){ throw error(HttpStatus.SERVICE_UNAVAILABLE,"PRICE_SOURCE_UNAVAILABLE","金价数据源不可用"); }
    }

    private Map<String,Object> priceResponse(JsonNode node) {
        if(node==null) throw new IllegalArgumentException("Empty price response");
        boolean coinApi=node.has("status") && node.has("data");
        if(coinApi && (!node.path("status").asBoolean() || node.path("code").asInt()!=1))
            throw new IllegalArgumentException("Price source reported failure");
        JsonNode value=coinApi?node.get("data"):node.get("price");
        if(value==null || value.isNull()) throw new IllegalArgumentException("Missing price");
        BigDecimal price=decimal(value);
        if(price==null) throw new IllegalArgumentException("Missing price");
        Instant time=coinApi?Instant.now():Instant.parse(node.path("dataTime").asText());
        return Map.of("price",price.toPlainString(),"unit",unit,"currency",currency,"precision",2,"dataTime",time.toString());
    }

    private Map<String,Object> publicRow(Map<String,Object> row) {
        Map<String,Object> out=new LinkedHashMap<>();
        row.forEach((key,value)->{
            if (key.toLowerCase(Locale.ROOT).contains("password")) return;
            if(value instanceof java.sql.Timestamp t) out.put(key,t.toInstant().toString());
            else if(value instanceof java.sql.Date d) out.put(key,d.toLocalDate().toString());
            else if(value instanceof LocalDateTime d) out.put(key,d.toInstant(ZoneOffset.UTC).toString());
            else if(value instanceof LocalDate d) out.put(key,d.toString());
            else out.put(key,value);
        });
        return out;
    }
    private int page(Integer value) { if(value==null) return 1; if(value<1 || value>1000000) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","页码无效"); return value; }
    private Map<String,Object> paged(String base,String order,List<Object> args,int page) {
        long count=db.queryForObject("SELECT count(*) FROM ("+base+") q",Long.class,args.toArray());
        List<Object> params=new ArrayList<>(args); params.add(10); params.add((page-1)*10);
        List<Map<String,Object>> items=rows(base+" "+order+" LIMIT ? OFFSET ?",params.toArray()).stream().map(this::publicRow).toList();
        return Map.of("items",items,"page",page,"pageSize",10,"total",count,"totalPages",(count+9)/10);
    }
    @GetMapping("/admin/users") public Map<String,Object> users(HttpServletRequest r,@RequestParam(required=false) String phone,@RequestParam(required=false) Integer status,@RequestParam(required=false) Integer page) {
        admin(r); String sql="SELECT u.id,u.phone,c.level,u.status,u.valid_from_date AS validFromDate,u.valid_to_date AS validToDate,u.create_time AS createTime FROM `user` u LEFT JOIN user_config c ON c.user_id=u.id WHERE 1=1"; List<Object> a=new ArrayList<>();
        if(phone!=null && !phone.isBlank()){sql+=" AND u.phone LIKE ?";a.add("%"+phone+"%");} if(status!=null){checkStatus(status);sql+=" AND u.status=?";a.add(status);}
        return paged(sql,"ORDER BY u.create_time DESC,u.id DESC",a,page(page));
    }
    @GetMapping("/admin/users/{id}") public Map<String,Object> getUser(HttpServletRequest r,@PathVariable int id) {
        admin(r);
        Map<String,Object> u=one("SELECT u.id,u.phone,c.level,u.status,u.valid_from_date AS validFromDate,u.valid_to_date AS validToDate,u.create_time AS createTime FROM `user` u LEFT JOIN user_config c ON c.user_id=u.id WHERE u.id=?",id);
        if(u==null) throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","用户不存在");
        return publicRow(u);
    }
    private void checkStatus(Integer s) { if(s==null || (s!=0 && s!=1)) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","状态只能为0或1"); }
    private void existsUser(int id) { if(one("SELECT id FROM `user` WHERE id=?",id)==null) throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","用户不存在"); }
    @PutMapping("/admin/users/{id}/password") public Map<String,Object> changePassword(HttpServletRequest r,@PathVariable int id,@RequestBody PasswordChange b) { admin(r);existsUser(id);db.update("UPDATE `user` SET password_hash=? WHERE id=?",password(b.password()),id);db.update("UPDATE auth_session SET revoked=true WHERE role='USER' AND principal_id=?",id);return Map.of("success",true); }
    @PatchMapping("/admin/users/{id}/status") public Map<String,Object> status(HttpServletRequest r,@PathVariable int id,@RequestBody StatusChange b) { admin(r);existsUser(id);checkStatus(b.status());db.update("UPDATE `user` SET status=? WHERE id=?",b.status(),id);return Map.of("id",id,"status",b.status()); }
    @PatchMapping("/admin/users/{id}/validity") public Map<String,Object> validity(HttpServletRequest r,@PathVariable int id,@RequestBody ValidityChange b) { admin(r);existsUser(id);if(b.validFromDate()!=null && b.validToDate()!=null && !b.validToDate().isAfter(b.validFromDate())) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","结束日期须晚于开始日期");db.update("UPDATE `user` SET valid_from_date=?,valid_to_date=? WHERE id=?",b.validFromDate(),b.validToDate(),id);Map<String,Object> out=new HashMap<>();out.put("id",id);out.put("validFromDate",b.validFromDate());out.put("validToDate",b.validToDate());return out; }
    @GetMapping("/admin/payments") public Map<String,Object> payments(HttpServletRequest r,@RequestParam(required=false) Integer userId,@RequestParam(required=false) String paymentNumber,@RequestParam(required=false) Instant createdFrom,@RequestParam(required=false) Instant createdTo,@RequestParam(required=false) Instant paidFrom,@RequestParam(required=false) Instant paidTo,@RequestParam(required=false) Integer page) {
        admin(r);String sql="SELECT p.id,p.user_id AS userId,u.phone,p.sku_id AS skuId,s.name AS skuName,p.payment_number AS paymentNumber,p.thirdparty_number AS thirdpartyNumber,p.pay_time AS payTime,p.create_time AS createTime FROM payment p JOIN `user` u ON u.id=p.user_id JOIN sku s ON s.id=p.sku_id WHERE 1=1";List<Object>a=new ArrayList<>();
        if(userId!=null){sql+=" AND p.user_id=?";a.add(userId);}if(paymentNumber!=null && !paymentNumber.isBlank()){sql+=" AND p.payment_number=?";a.add(paymentNumber);}
        Instant[] bounds={createdFrom,createdTo,paidFrom,paidTo};String[] cols={"p.create_time>=?","p.create_time<?","p.pay_time>=?","p.pay_time<?"};for(int i=0;i<4;i++)if(bounds[i]!=null){sql+=" AND "+cols[i];a.add(LocalDateTime.ofInstant(bounds[i],ZoneOffset.UTC));}
        if(createdFrom!=null && createdTo!=null && !createdTo.isAfter(createdFrom) || paidFrom!=null && paidTo!=null && !paidTo.isAfter(paidFrom)) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","时间范围无效");
        return paged(sql,"ORDER BY p.create_time DESC,p.id DESC",a,page(page));
    }
    private Map<String,Object> sku(int id) { Map<String,Object> s=one("SELECT id,name,price,status,sort_order AS sortOrder,create_time AS createTime FROM sku WHERE id=?",id);if(s==null) throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","商品不存在");return publicRow(s); }
    @GetMapping("/admin/skus") public Map<String,Object> skus(HttpServletRequest r,@RequestParam(required=false) String name,@RequestParam(required=false) Integer status,@RequestParam(required=false) Integer page) {admin(r);String sql="SELECT id,name,price,status,sort_order AS sortOrder,create_time AS createTime FROM sku WHERE 1=1";List<Object>a=new ArrayList<>();if(name!=null&&!name.isBlank()){sql+=" AND name LIKE ?";a.add("%"+name+"%");}if(status!=null){checkStatus(status);sql+=" AND status=?";a.add(status);}return paged(sql,"ORDER BY sortOrder ASC,id ASC",a,page(page));}
    @GetMapping("/admin/skus/{id}") public Map<String,Object> getSku(HttpServletRequest r,@PathVariable int id){admin(r);return sku(id);}
    private void validateSku(SkuInput b){if(b.name()==null||b.name().isBlank()||b.name().length()>120||b.price()==null||b.price()<0||b.sortOrder()==null)throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","商品参数无效");checkStatus(b.status());}
    @PostMapping("/admin/skus") public ResponseEntity<Map<String,Object>> createSku(HttpServletRequest r,@RequestBody SkuInput b){admin(r);validateSku(b);db.update("INSERT INTO sku(name,price,status,sort_order) VALUES (?,?,?,?)",b.name().trim(),b.price(),b.status(),b.sortOrder());int id=db.queryForObject("SELECT LAST_INSERT_ID()",Integer.class);return ResponseEntity.status(201).body(sku(id));}
    @PutMapping("/admin/skus/{id}") public Map<String,Object> updateSku(HttpServletRequest r,@PathVariable int id,@RequestBody SkuInput b){admin(r);sku(id);validateSku(b);db.update("UPDATE sku SET name=?,price=?,status=?,sort_order=? WHERE id=?",b.name().trim(),b.price(),b.status(),b.sortOrder(),id);return sku(id);}
    @Transactional
    @DeleteMapping("/admin/skus/{id}") public Map<String,Object> deleteSku(HttpServletRequest r,@PathVariable int id){admin(r);sku(id);if(db.queryForObject("SELECT count(*) FROM payment WHERE sku_id=?",Integer.class,id)>0)throw error(HttpStatus.CONFLICT,"DELETE_CONFLICT","商品有支付记录，请停用");try { db.update("DELETE FROM sku WHERE id=?",id); } catch(DataIntegrityViolationException e) { throw error(HttpStatus.CONFLICT,"DELETE_CONFLICT","商品有支付记录，请停用"); } return Map.of("success",true);}
}
