package com.goldpricemonitor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import static com.goldpricemonitor.api.service.ApiSupport.error;

@Service
public class GoldAccountService {
    private final JdbcTemplate db;
    private final RestClient http;
    private final String baseUrl;

    public GoldAccountService(JdbcTemplate db, @Value("${app.gold-account-base-url:}") String baseUrl) {
        this.db = db;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    private Map<String,Object> row(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", rs.getInt("id"));
        out.put("username", rs.getString("username"));
        out.put("isOnline", rs.getInt("is_online") == 1);
        return out;
    }

    public List<Map<String,Object>> list(int userId) {
        return db.query("SELECT id,username,is_online FROM gold_account WHERE user_id=? ORDER BY id DESC", this::row, userId);
    }

    public Map<String,Object> get(int userId, int id) {
        return db.query("SELECT id,username,is_online FROM gold_account WHERE user_id=? AND id=?", this::row, userId, id)
            .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "GOLD_ACCOUNT_NOT_FOUND", "Gold账号不存在"));
    }

    private String username(String value) {
        if (value == null || value.isBlank() || value.length() > 120)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "账号名长度须为1到120位");
        return value.trim();
    }

    private String password(String value) {
        if (value != null && value.length() > 255)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "密码不能超过255位");
        return value;
    }

    @Transactional
    public Map<String,Object> create(int userId, String username, String password) {
        String name = username(username);
        String secret = password(password);
        var key = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO gold_account(user_id,username,password) VALUES(?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, userId);
            statement.setString(2, name);
            statement.setString(3, secret);
            return statement;
        }, key);
        return get(userId, Objects.requireNonNull(key.getKey()).intValue());
    }

    @Transactional
    public Map<String,Object> update(int userId, int id, String username, String password, boolean replacePassword) {
        get(userId, id);
        if (replacePassword) db.update("UPDATE gold_account SET username=?,password=? WHERE user_id=? AND id=?", username(username), password(password), userId, id);
        else db.update("UPDATE gold_account SET username=? WHERE user_id=? AND id=?", username(username), userId, id);
        return get(userId, id);
    }

    public void delete(int userId, int id) {
        if (db.update("DELETE FROM gold_account WHERE user_id=? AND id=?", userId, id) == 0)
            throw error(HttpStatus.NOT_FOUND, "GOLD_ACCOUNT_NOT_FOUND", "Gold账号不存在");
    }

    private JsonNode call(String path, Map<String,Object> payload) {
        if (baseUrl.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "GOLD_SERVICE_UNCONFIGURED", "Gold服务地址未配置");
        URI uri = URI.create(baseUrl + path);
        if (!Set.of("http", "https").contains(uri.getScheme()))
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "GOLD_SERVICE_UNCONFIGURED", "Gold服务地址无效");
        try {
            JsonNode result = payload == null ? http.get().uri(uri).retrieve().body(JsonNode.class)
                : http.post().uri(uri).body(payload).retrieve().body(JsonNode.class);
            if (result == null || !result.isObject())
                throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务返回无效响应");
            if (!result.path("status").asBoolean()) {
                String message = result.path("msg").asText("Gold服务拒绝请求");
                throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_REJECTED", message.isBlank() ? "Gold服务拒绝请求" : message);
            }
            return result;
        } catch (com.goldpricemonitor.api.ApiFault e) { throw e; }
        catch (RestClientException e) { throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_ERROR", "Gold服务请求失败"); }
    }

    public Map<String,Object> passwordCode(int userId, int id) {
        get(userId, id);
        JsonNode data = call("/api/User/GetPwdLoginCode", null).path("data");
        String image = data.path("base64str").asText("");
        String key = data.path("cachkey").asText("");
        if (image.isBlank() || key.isBlank())
            throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回图形验证码");
        return Map.of("base64str", image, "cachkey", key);
    }

    @Transactional
    public Map<String,Object> passwordLogin(int userId, int id, String code, String cachkey) {
        get(userId, id);
        if (code == null || code.isBlank() || cachkey == null || cachkey.isBlank())
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "图形验证码不能为空");
        Map<String,Object> account = db.queryForMap("SELECT username,password FROM gold_account WHERE user_id=? AND id=?", userId, id);
        String password = (String) account.get("password");
        if (password == null || password.isBlank())
            throw error(HttpStatus.BAD_REQUEST, "GOLD_PASSWORD_MISSING", "请先为Gold账号设置密码");
        JsonNode result = call("/api/User/GetLoginByUserName", Map.of(
            "userName", account.get("username"), "password", password, "code", code.trim(), "cachkey", cachkey.trim()));
        JsonNode token = result.path("data").path("token");
        if (!token.isTextual() || token.asText().isBlank())
            throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回token");
        db.update("UPDATE gold_account SET token=?,is_online=1 WHERE user_id=? AND id=?", token.asText(), userId, id);
        return get(userId, id);
    }

    public Map<String,Object> sendSms(int userId, int id, String mobile) {
        get(userId,id);
        call("/api/User/SendSms", Map.of("mobile", mobile(mobile), "messageEnum", 2, "code", "Login"));
        return Map.of("success", true);
    }

    @Transactional
    public Map<String,Object> login(int userId, int id, String mobile, String code) {
        get(userId,id);
        if (code == null || code.isBlank()) throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","验证码不能为空");
        JsonNode result = call("/api/User/LoginByPhoneAndCode", Map.of("mobile", mobile(mobile), "code", code.trim(), "messageEnum", 2, "loginType", 2));
        JsonNode token = result.path("data").path("token");
        if (!token.isTextual() || token.asText().isBlank())
            throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回token");
        if (db.update("UPDATE gold_account SET token=?,is_online=1 WHERE user_id=? AND id=?", token.asText(), userId, id) == 0)
            throw error(HttpStatus.NOT_FOUND,"GOLD_ACCOUNT_NOT_FOUND","Gold账号不存在");
        return get(userId,id);
    }

    private String mobile(String value) {
        if (value == null || !value.matches("(?:1[3-9]\\d{9}|\\+[1-9]\\d{7,14})"))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","手机号格式无效");
        return value;
    }
}
