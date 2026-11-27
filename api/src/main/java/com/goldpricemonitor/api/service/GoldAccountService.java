package com.goldpricemonitor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.goldpricemonitor.api.service.ApiSupport.error;

@Service
public class GoldAccountService {
    private static final Logger log = LoggerFactory.getLogger(GoldAccountService.class);
    private static final ZoneId GOLD_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    private final OkHttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public GoldAccountService(JdbcTemplate db, @Value("${app.price-server-base-url:}") String baseUrl) {
        this.db = db;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.http = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(3))
            .readTimeout(Duration.ofSeconds(8))
            .build();
    }

    private Map<String,Object> row(java.sql.ResultSet rs, int ignored) throws java.sql.SQLException {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", rs.getInt("id"));
        out.put("username", rs.getString("username"));
        out.put("isOnline", rs.getInt("is_online") == 1);
        Timestamp validUntil = rs.getTimestamp("valid_until");
        out.put("validUntil", validUntil == null ? null : validUntil.toInstant().toString());
        return out;
    }

    public List<Map<String,Object>> list(int userId) {
        return db.query("SELECT id,username,is_online,valid_until FROM gold_account WHERE user_id=? ORDER BY id DESC", this::row, userId);
    }

    public Map<String,Object> get(int userId, int id) {
        return db.query("SELECT id,username,is_online,valid_until FROM gold_account WHERE user_id=? AND id=?", this::row, userId, id)
            .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "GOLD_ACCOUNT_NOT_FOUND", "Gold账号不存在"));
    }

    public Map<String,Object> getForEdit(int userId, int id) {
        return db.query("SELECT id,username,is_online,valid_until,password FROM gold_account WHERE user_id=? AND id=?", (rs, rowNum) -> {
            Map<String,Object> account = row(rs, rowNum);
            account.put("password", rs.getString("password"));
            return account;
        }, userId, id).stream().findFirst()
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "GOLD_ACCOUNT_NOT_FOUND", "Gold账号不存在"));
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

    public Map<String,Object> logout(int userId, int id) {
        if (db.update("UPDATE gold_account SET token=NULL,valid_until=NULL,is_online=0 WHERE user_id=? AND id=?", userId, id) == 0)
            throw error(HttpStatus.NOT_FOUND, "GOLD_ACCOUNT_NOT_FOUND", "Gold账号不存在");
        return get(userId, id);
    }

    private JsonNode call(String path, Map<String,Object> payload) {
        return call(path, payload, null);
    }

    private JsonNode call(String path, Map<String,Object> payload, String token) {
        if (baseUrl.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "GOLD_SERVICE_UNCONFIGURED", "Gold服务地址未配置");
        URI uri = URI.create(baseUrl + path);
        if (!Set.of("http", "https").contains(uri.getScheme()))
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "GOLD_SERVICE_UNCONFIGURED", "Gold服务地址无效");
        String method = payload == null ? "GET" : "POST";
        String endpoint = uri.getPath();
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        log.info("Gold request requestId={} method={} endpoint={}", requestId, method, endpoint);
        try {
            Request.Builder request = new Request.Builder().url(uri.toString());
            if (token != null) request.header("Authorization", "Bearer " + token);
            if (payload == null) request.get();
            else request.post(RequestBody.create(mapper.writeValueAsBytes(payload), okhttp3.MediaType.get("application/json; charset=utf-8")));
            JsonNode result;
            try (Response response = http.newCall(request.build()).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Gold response requestId={} method={} endpoint={} httpStatus={} elapsedMs={}",
                        requestId, method, endpoint, response.code(), (System.nanoTime() - started) / 1_000_000);
                    throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_ERROR", "Gold服务请求失败");
                }
                result = response.body() == null ? null : mapper.readTree(response.body().byteStream());
            }
            if (result == null || !result.isObject()) {
                log.warn("Gold response requestId={} method={} endpoint={} invalidBody=true elapsedMs={}",
                    requestId, method, endpoint, (System.nanoTime() - started) / 1_000_000);
                throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务返回无效响应");
            }
            JsonNode data = result.path("data");
            if ("/api/grabcoin/chances/bubbles".equals(endpoint))
                log.info("Gold response requestId={} method={} endpoint={} accepted={} bubbles={} sellableCount={} elapsedMs={}",
                    requestId, method, endpoint, result.path("status").asBoolean(),
                    data.path("bubbles").isArray() ? data.path("bubbles").size() : -1,
                    data.path("sellableCount").isNumber() ? data.path("sellableCount").asInt() : -1,
                    (System.nanoTime() - started) / 1_000_000);
            else
                log.info("Gold response requestId={} method={} endpoint={} accepted={} elapsedMs={}",
                    requestId, method, endpoint, result.path("status").asBoolean(), (System.nanoTime() - started) / 1_000_000);
            if (!result.path("status").asBoolean()) {
                String message = result.path("msg").asText("Gold服务拒绝请求");
                throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_REJECTED", message.isBlank() ? "Gold服务拒绝请求" : message);
            }
            return result;
        } catch (com.goldpricemonitor.api.ApiFault e) { throw e; }
        catch (IOException e) {
            log.warn("Gold request failed requestId={} method={} endpoint={} errorType={} elapsedMs={}",
                requestId, method, endpoint, e.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000_000);
            throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_ERROR", "Gold服务请求失败");
        }
    }

    public Map<String,Object> sellable(int userId, int id) {
        List<String> tokens = db.query("SELECT token FROM gold_account WHERE user_id=? AND id=?",
            (rs, ignored) -> rs.getString("token"), userId, id);
        if (tokens.isEmpty()) throw error(HttpStatus.NOT_FOUND, "GOLD_ACCOUNT_NOT_FOUND", "Gold账号不存在");
        String token = tokens.get(0);
        if (token == null || token.isBlank())
            throw error(HttpStatus.CONFLICT, "GOLD_ACCOUNT_NOT_LOGGED_IN", "Gold账号尚未登录");
        JsonNode data = call("/api/grabcoin/chances/bubbles", null, token).path("data");
        if (!data.isObject() || !data.path("bubbles").isArray()
            || !data.path("sellableCount").isNumber() || !data.path("sellableWeight").isNumber()
            || !data.path("remainingSellableWeight").isNumber())
            throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回可挂卖数量");
        Map<String,Object> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(GOLD_ZONE);
        int remainingToday = 0;
        java.math.BigDecimal remainingWeightToday = java.math.BigDecimal.ZERO;
        for (JsonNode bubble : data.get("bubbles")) {
            if (!createdToday(bubble.path("createTime"), today)) continue;
            remainingToday++;
            if (!bubble.path("weight").isNumber())
                throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回气泡克重");
            remainingWeightToday = remainingWeightToday.add(bubble.get("weight").decimalValue());
        }
        result.put("sellableCount", data.get("sellableCount").numberValue());
        result.put("sellableWeight", remainingWeightToday.add(data.path("soldWeight").isNumber() ? data.get("soldWeight").decimalValue() : java.math.BigDecimal.ZERO));
        result.put("soldCount", data.path("soldCount").isNumber() ? data.get("soldCount").numberValue() : 0);
        result.put("soldWeight", data.path("soldWeight").isNumber() ? data.get("soldWeight").numberValue() : 0);
        result.put("remainingSellableCount", remainingToday);
        result.put("remainingSellableWeight", remainingWeightToday);
        log.info("Gold sellable userId={} accountId={} returnedBubbles={} remainingToday={} remainingWeightToday={}",
            userId, id, data.get("bubbles").size(), remainingToday, remainingWeightToday);
        return result;
    }

    private boolean createdToday(JsonNode value, LocalDate today) {
        String timestamp = value.asText("");
        if (timestamp.length() < 10) throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回创建时间");
        try { return LocalDate.parse(timestamp.substring(0, 10)).equals(today); }
        catch (java.time.format.DateTimeParseException e) {
            throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务返回无效创建时间");
        }
    }

    public Map<String,Object> sellGrabcoin(int userId, int id, java.math.BigDecimal weight) {
        if (weight == null || weight.signum() <= 0 || weight.scale() > 4)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "请输入大于0且最多4位小数的克数");
        weight = weight.stripTrailingZeros();
        List<String> tokens = db.query("SELECT token FROM gold_account WHERE user_id=? AND id=?",
            (rs, ignored) -> rs.getString("token"), userId, id);
        if (tokens.isEmpty()) throw error(HttpStatus.NOT_FOUND, "GOLD_ACCOUNT_NOT_FOUND", "Gold账号不存在");
        String token = tokens.get(0);
        if (token == null || token.isBlank()) throw error(HttpStatus.CONFLICT, "GOLD_ACCOUNT_NOT_LOGGED_IN", "Gold账号尚未登录");
        JsonNode bubbles = call("/api/grabcoin/chances/bubbles", null, token).path("data").path("bubbles");
        if (!bubbles.isArray()) throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回可挂卖气泡");
        LocalDate today = LocalDate.now(GOLD_ZONE);
        Map<java.math.BigDecimal,List<String>> sums = new LinkedHashMap<>();
        sums.put(java.math.BigDecimal.ZERO, List.of());
        for (JsonNode bubble : bubbles) {
            if (!createdToday(bubble.path("createTime"), today)) continue;
            String chanceNo = bubble.path("chanceNo").asText("");
            if (chanceNo.isBlank() || !bubble.path("weight").isNumber())
                throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务返回无效气泡");
            java.math.BigDecimal bubbleWeight = bubble.get("weight").decimalValue();
            if (bubbleWeight.signum() <= 0) continue;
            Map<java.math.BigDecimal,List<String>> additions = new LinkedHashMap<>();
            for (var entry : sums.entrySet()) {
                java.math.BigDecimal sum = entry.getKey().add(bubbleWeight).stripTrailingZeros();
                if (sum.compareTo(weight) > 0 || sums.containsKey(sum) || additions.containsKey(sum)) continue;
                List<String> selected = new ArrayList<>(entry.getValue());
                selected.add(chanceNo);
                additions.put(sum, selected);
            }
            sums.putAll(additions);
            if (sums.size() > 20000) throw error(HttpStatus.BAD_REQUEST, "WEIGHT_SELECTION_TOO_COMPLEX", "可挂卖气泡组合过多，请缩小克数");
            if (sums.containsKey(weight)) break;
        }
        List<String> selected = sums.get(weight);
        if (selected == null) throw error(HttpStatus.BAD_REQUEST, "WEIGHT_NOT_AVAILABLE", "今天的可挂卖气泡无法组成该克数");
        List<String> orderNos = new ArrayList<>();
        java.math.BigDecimal actualWeight = java.math.BigDecimal.ZERO;
        int completedChances = 0;
        for (String chanceNo : selected) {
            try {
                JsonNode data = call("/api/grabcoin/chances/grab-v2", Map.of("chanceNo", chanceNo), token).path("data");
                if (!data.path("grabWeight").isNumber()) throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回挂卖克重");
                actualWeight = actualWeight.add(data.get("grabWeight").decimalValue());
                completedChances++;
                if (data.path("orderNos").isArray()) for (JsonNode orderNo : data.get("orderNos")) orderNos.add(orderNo.asText());
                else if (data.path("orderNo").isTextual()) orderNos.add(data.get("orderNo").asText());
            } catch (com.goldpricemonitor.api.ApiFault e) {
                if (completedChances == 0) throw e;
                return Map.of("success", false, "completedCount", completedChances, "actualWeight", actualWeight,
                    "orderNos", orderNos, "message", e.getMessage());
            }
        }
        return Map.of("success", true, "completedCount", completedChances, "actualWeight", actualWeight, "orderNos", orderNos);
    }

    public Map<String,Object> grabcoin(int userId) {
        LocalDate today = LocalDate.now(GOLD_ZONE);
        List<Map<String,Object>> accounts = new ArrayList<>();
        List<Map<String,Object>> orders = new ArrayList<>();
        for (Map<String,Object> account : list(userId)) {
            Map<String,Object> summary = new LinkedHashMap<>(account);
            accounts.add(summary);
            if (!Boolean.TRUE.equals(account.get("isOnline"))) continue;
            int id = (int) account.get("id");
            int orderStart = orders.size();
            try {
                Map<String,Object> chance = sellable(userId, id);
                summary.put("availableCount", chance.get("remainingSellableCount"));
                String token = db.queryForObject("SELECT token FROM gold_account WHERE user_id=? AND id=?", String.class, userId, id);
                int page = 1, count, fetched = 0;
                do {
                    JsonNode data = call("/api/grabcoin/orders?status=0&beginTime=" + today + "&page=" + page + "&limit=100", null, token).path("data");
                    if (!data.path("list").isArray() || !data.path("count").canConvertToInt())
                        throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回挂单记录");
                    count = data.path("count").asInt();
                    for (JsonNode order : data.path("list")) {
                        fetched++;
                        if (!createdToday(order.path("createTime"), today)) continue;
                        if (!order.path("goldPrice").isNumber())
                            throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "Gold服务未返回挂单价格");
                        Map<String,Object> item = new LinkedHashMap<>();
                        item.put("accountId", id);
                        item.put("username", account.get("username"));
                        item.put("orderNo", order.path("orderNo").asText(""));
                        item.put("goldPrice", order.get("goldPrice").numberValue());
                        item.put("totalWeight", order.path("totalWeight").isNumber() ? order.get("totalWeight").numberValue() : null);
                        item.put("createTime", order.path("createTime").asText(""));
                        orders.add(item);
                    }
                    page++;
                } while (fetched < count && page <= 100);
                if (fetched < count)
                    throw error(HttpStatus.BAD_GATEWAY, "GOLD_SERVICE_RESPONSE", "挂单记录超过查询上限");
                summary.put("listedCount", ordersForAccount(orders, id));
            } catch (com.goldpricemonitor.api.ApiFault e) {
                orders.subList(orderStart, orders.size()).clear();
                summary.put("error", e.getMessage());
            }
        }
        orders.sort(Comparator.comparing(o -> new java.math.BigDecimal(o.get("goldPrice").toString())));
        return Map.of("accounts", accounts, "orders", orders);
    }

    private int ordersForAccount(List<Map<String,Object>> orders, int id) {
        return (int) orders.stream().filter(order -> (int) order.get("accountId") == id).count();
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
        db.update("UPDATE gold_account SET token=?,valid_until=?,is_online=1 WHERE user_id=? AND id=?", token.asText(), validUntil(token.asText()), userId, id);
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
        if (db.update("UPDATE gold_account SET token=?,valid_until=?,is_online=1 WHERE user_id=? AND id=?", token.asText(), validUntil(token.asText()), userId, id) == 0)
            throw error(HttpStatus.NOT_FOUND,"GOLD_ACCOUNT_NOT_FOUND","Gold账号不存在");
        return get(userId,id);
    }

    private String mobile(String value) {
        if (value == null || !value.matches("(?:1[3-9]\\d{9}|\\+[1-9]\\d{7,14})"))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","手机号格式无效");
        return value;
    }

    private Timestamp validUntil(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            JsonNode payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode exp = payload.path("exp");
            return exp.isIntegralNumber() ? Timestamp.from(Instant.ofEpochSecond(exp.longValue())) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
