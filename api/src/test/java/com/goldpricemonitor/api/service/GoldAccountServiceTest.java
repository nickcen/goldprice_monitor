package com.goldpricemonitor.api.service;

import com.goldpricemonitor.api.ApiFault;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class GoldAccountServiceTest {
    @Test void balancesUseOwnedTokensAndKeepOtherAccountsWhenOneFails() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_balances_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/User/GetUserInfo", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] response = ("Bearer good-token".equals(token)
                ? "{\"status\":true,\"data\":{\"balance\":12.34}}"
                : "{\"status\":false,\"msg\":\"expired\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db, "http://127.0.0.1:" + server.getAddress().getPort(), "12345678901234567890123456789012");
            int good = (int) service.create(1, "good", null, null).get("id");
            int bad = (int) service.create(1, "bad", null, null).get("id");
            int other = (int) service.create(2, "other", null, null).get("id");
            db.update("UPDATE gold_account SET token=? WHERE id=?", "good-token", good);
            db.update("UPDATE gold_account SET token=? WHERE id=?", "bad-token", bad);
            db.update("UPDATE gold_account SET token=? WHERE id=?", "good-token", other);
            var balances = service.balances(1);
            assertEquals(2, balances.size());
            assertEquals(bad, balances.get(0).get("id"));
            assertNull(balances.get(0).get("balance"));
            assertEquals(new java.math.BigDecimal("12.34"), balances.get(1).get("balance"));
        } finally { server.stop(0); }
    }
    @Test void sellAllAndSellOrderUseOwnedTokenAndEncryptedPassword() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_sell_actions_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/grabcoin/order/sell-all-v2", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"status\":true,\"data\":{\"completedCount\":1}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/grabcoin/order/sell-v2", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":true,\"data\":{\"sold\":true}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db, "http://127.0.0.1:" + server.getAddress().getPort(), "12345678901234567890123456789012");
            int id = (int) service.create(1, "account", null, null).get("id");
            assertThrows(ApiFault.class, () -> service.sellAllGrabcoin(2, id));
            assertThrows(ApiFault.class, () -> service.sellAllGrabcoin(1, id));
            db.update("UPDATE gold_account SET token=? WHERE id=?", "gold-token", id);
            assertEquals(1, service.sellAllGrabcoin(1, id).path("completedCount").asInt());
            assertThrows(ApiFault.class, () -> service.sellGrabcoinOrder(1, id, 0));
            assertThrows(ApiFault.class, () -> service.sellGrabcoinOrder(2, id, 12));
            assertThrows(ApiFault.class, () -> service.sellGrabcoinOrder(1, id, 12));
            service.update(1, id, "account", null, false, "123456", true);
            assertTrue(service.sellGrabcoinOrder(1, id, 12).path("sold").asBoolean());
            assertEquals("Bearer gold-token", authorization.get());
            var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload.get());
            assertEquals(12, body.path("orderId").asInt());
            assertNotEquals("123456", body.path("passWord").asText());
            assertFalse(body.path("passWord").asText().isBlank());
        } finally { server.stop(0); }
    }
    @Test void sellGrabcoinUsesOnlyTodayBubblesAndExactWeight() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_sell_today_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        AtomicReference<String> submitted = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/grabcoin/chances/bubbles", exchange -> {
            byte[] response = ("{\"status\":true,\"data\":{\"bubbles\":[{\"chanceNo\":\"old\",\"weight\":1,\"createTime\":\"" + today.minusDays(1) + "T12:00:00\"},{\"chanceNo\":\"today\",\"weight\":1.0,\"createTime\":\"" + today + "T12:00:00\"}]}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/grabcoin/chances/grab-v2", exchange -> {
            submitted.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response = "{\"status\":true,\"data\":{\"grabWeight\":1,\"orderNos\":[\"order-1\"]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db,"http://127.0.0.1:"+server.getAddress().getPort(), "12345678901234567890123456789012");
            int id = (int) service.create(1,"account",null,null).get("id");
            db.update("UPDATE gold_account SET token=? WHERE id=?", "gold-token", id);
            assertThrows(ApiFault.class, () -> service.sellGrabcoin(2,id,new java.math.BigDecimal("1")));
            assertThrows(ApiFault.class, () -> service.sellGrabcoin(1,id,new java.math.BigDecimal("2")));
            assertNull(submitted.get());
            Map<String,Object> result = service.sellGrabcoin(1,id,new java.math.BigDecimal("1"));
            assertEquals(true, result.get("success"));
            assertEquals(1, result.get("completedCount"));
            assertTrue(submitted.get().contains("\"chanceNo\":\"today\""));
        } finally { server.stop(0); }
    }
    @Test void grabcoinOmitsHistoricalPendingOrders() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_grabcoin_today_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/grabcoin/chances/bubbles", exchange -> {
            byte[] response = ("{\"status\":true,\"data\":{\"sellableCount\":0,\"sellableWeight\":1,\"soldCount\":0,\"soldWeight\":0,\"remainingSellableWeight\":1,\"bubbles\":[{\"createTime\":\"" + today.minusDays(1) + "T12:00:00\",\"weight\":1}]}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/grabcoin/orders", exchange -> {
            assertTrue(exchange.getRequestURI().getQuery().contains("beginTime=" + today));
            byte[] response = ("{\"status\":true,\"data\":{\"count\":2,\"list\":[{\"id\":11,\"createTime\":\"" + today.minusDays(1) + "T12:00:00\",\"goldPrice\":888,\"totalWeight\":1},{\"id\":12,\"createTime\":\"" + today + "T12:00:00\",\"goldPrice\":889,\"totalWeight\":1}]}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        AtomicReference<String> soldPayload = new AtomicReference<>();
        server.createContext("/api/grabcoin/order/sell-v2", exchange -> {
            soldPayload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":true,\"data\":{\"sold\":true}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db,"http://127.0.0.1:"+server.getAddress().getPort(), "12345678901234567890123456789012");
            int id = (int) service.create(1,"account",null,null).get("id");
            db.update("UPDATE gold_account SET token=?,is_online=1 WHERE id=?", "gold-token", id);
            Map<String,Object> result = service.grabcoin(1);
            Map<?,?> account = (Map<?,?>) ((java.util.List<?>) result.get("accounts")).get(0);
            assertEquals(0, account.get("availableCount"));
            assertEquals(1, account.get("listedCount"));
            assertEquals(1, ((java.util.List<?>) result.get("orders")).size());
            assertThrows(ApiFault.class, () -> service.sellGrabcoinOrders(2, new java.math.BigDecimal("889")));
            assertThrows(ApiFault.class, () -> service.sellGrabcoinOrders(1, new java.math.BigDecimal("888")));
            assertNull(soldPayload.get());
            service.update(1, id, "account", null, false, "123456", true);
            assertEquals(1, service.sellGrabcoinOrders(1, new java.math.BigDecimal("889.00")).get("completedCount"));
            assertTrue(soldPayload.get().contains("\"orderId\":12"));
        } finally { server.stop(0); }
    }
    @Test void sellableUsesOwnedAccountTokenAndReturnsCurrentBubbleCount() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_sellable_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/grabcoin/chances/bubbles", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
            String yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
            byte[] response = ("{\"status\":true,\"data\":{\"sellableCount\":1,\"sellableWeight\":2.0,\"soldCount\":0,\"soldWeight\":0,\"bubbles\":[{\"createTime\":\"" + yesterday + "T00:00:00\",\"weight\":1.0},{\"createTime\":\"" + today + "T00:00:00\",\"weight\":1.0}],\"remainingSellableWeight\":2.0}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db,"http://127.0.0.1:"+server.getAddress().getPort(), "12345678901234567890123456789012");
            int id = (int) service.create(1,"account",null,null).get("id");
            assertThrows(ApiFault.class, () -> service.sellable(2,id));
            assertThrows(ApiFault.class, () -> service.sellable(1,id));
            db.update("UPDATE gold_account SET token=? WHERE id=?", "gold-token", id);
            Map<String,Object> result = service.sellable(1,id);
            assertEquals("Bearer gold-token", authorization.get());
            assertEquals(1, result.get("sellableCount"));
            assertEquals(0, result.get("soldCount"));
            assertEquals(0, result.get("soldWeight"));
            assertEquals(1, result.get("remainingSellableCount"));
            assertEquals(0, new java.math.BigDecimal("1.0").compareTo((java.math.BigDecimal) result.get("remainingSellableWeight")));
            assertFalse(result.containsKey("token"));
        } finally { server.stop(0); }
    }
    @Test void passwordCaptchaAndLoginUseStoredCredentialsAndOnlySaveSuccessfulToken() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_password_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> requestContentLength = new AtomicReference<>();
        AtomicBoolean accepted = new AtomicBoolean(false);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/User/GetPwdLoginCode", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            byte[] response = "{\"status\":true,\"data\":{\"base64str\":\"aW1hZ2U=\",\"cachkey\":\"key-1\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/User/GetLoginByUserName", exchange -> {
            requestContentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response = (accepted.get() ? "{\"status\":true,\"data\":{\"token\":\"" + jwt() + "\"}}" : "{\"status\":false,\"msg\":\"验证码错误\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db,"http://127.0.0.1:"+server.getAddress().getPort(), "12345678901234567890123456789012");
            int id = (int)service.create(1,"account","secret",null).get("id");
            assertThrows(ApiFault.class, () -> service.passwordCode(2,id));
            assertEquals("key-1", service.passwordCode(1,id).get("cachkey"));
            assertThrows(ApiFault.class, () -> service.passwordLogin(2,id,"1234","key-1"));
            assertThrows(ApiFault.class, () -> service.passwordLogin(1,id,"bad","key-1"));
            assertFalse((boolean)service.get(1,id).get("isOnline"));
            accepted.set(true);
            service.passwordLogin(1,id,"1234","key-1");
            assertNotNull(requestContentLength.get(), "Gold login request must include Content-Length");
            assertTrue(request.get().contains("\"userName\":\"account\""));
            assertTrue(request.get().contains("\"password\":\"secret\""));
            assertTrue(request.get().contains("\"cachkey\":\"key-1\""));
            assertEquals(jwt(),db.queryForObject("SELECT token FROM gold_account WHERE id=?",String.class,id));
            assertEquals("2030-01-01T00:00:00Z",service.get(1,id).get("validUntil"));
            assertFalse(service.get(1,id).containsKey("password"));
        } finally { server.stop(0); }
    }
    @Test void smsAndLoginUseRequiredPayloadAndSaveTokenOnlyOnSuccess() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_remote_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        AtomicReference<String> sms = new AtomicReference<>(), login = new AtomicReference<>();
        AtomicBoolean rejectLogin = new AtomicBoolean(true);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/User/SendSms", exchange -> {
            sms.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response = "{\"status\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/User/LoginByPhoneAndCode", exchange -> {
            login.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response = (rejectLogin.get() ? "{\"status\":false,\"msg\":\"验证码错误\",\"data\":null}" : "{\"status\":true,\"data\":{\"token\":\"" + jwt() + "\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db,"http://127.0.0.1:"+server.getAddress().getPort(), "12345678901234567890123456789012");
            int id = (int)service.create(1,"account",null,null).get("id");
            service.sendSms(1,id,"13800138000");
            assertTrue(sms.get().contains("\"messageEnum\":2"));
            assertTrue(sms.get().contains("\"code\":\"Login\""));
            ApiFault rejected = assertThrows(ApiFault.class, () -> service.login(1,id,"13800138000","bad"));
            assertEquals("验证码错误", rejected.getMessage());
            assertFalse((boolean)service.get(1,id).get("isOnline"));
            rejectLogin.set(false);
            service.login(1,id,"13800138000","123456");
            assertTrue(login.get().contains("\"loginType\":2"));
            assertEquals(jwt(),db.queryForObject("SELECT token FROM gold_account WHERE id=?",String.class,id));
            assertEquals("2030-01-01T00:00:00Z",service.list(1).get(0).get("validUntil"));
            assertTrue((boolean)service.get(1,id).get("isOnline"));
        } finally { server.stop(0); }
    }
    @Test void crudIsScopedAndKeepsSecretsPrivate() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(512),payment_password VARCHAR(512),salt VARCHAR(12),token VARCHAR(512),valid_until TIMESTAMP,is_online TINYINT DEFAULT 0)");
        GoldAccountService service = new GoldAccountService(db, "", "12345678901234567890123456789012");
        int id = (int) service.create(1,"first","plain-password",null).get("id");
        assertEquals(0,db.queryForObject("SELECT is_online FROM gold_account WHERE id=?",Integer.class,id));
        assertFalse(service.get(1,id).containsKey("password"));
        assertEquals("plain-password", service.getForEdit(1,id).get("password"));
        assertThrows(ApiFault.class, () -> service.getForEdit(2,id));
        assertThrows(ApiFault.class,() -> service.get(2,id));
        assertThrows(ApiFault.class,() -> service.sendSms(2,id,"13800138000"));
        service.update(1,id,"second",null,false,"654321",true);
        assertTrue(db.queryForObject("SELECT password FROM gold_account WHERE id=?",String.class,id).startsWith("v1:"));
        assertTrue(db.queryForObject("SELECT payment_password FROM gold_account WHERE id=?",String.class,id).startsWith("v1:"));
        assertEquals("654321",service.getForEdit(1,id).get("paymentPassword"));
        assertFalse(service.get(1,id).containsKey("paymentPassword"));
        assertEquals("second",service.get(1,id).get("username"));
        db.update("UPDATE gold_account SET token=?,valid_until=?,is_online=1 WHERE id=?", jwt(), java.sql.Timestamp.from(Instant.parse("2030-01-01T00:00:00Z")), id);
        assertThrows(ApiFault.class, () -> service.logout(2,id));
        assertTrue((boolean) service.get(1,id).get("isOnline"));
        service.logout(1,id);
        assertNull(db.queryForObject("SELECT token FROM gold_account WHERE id=?", String.class, id));
        assertNull(service.get(1,id).get("validUntil"));
        assertFalse((boolean) service.get(1,id).get("isOnline"));
        assertThrows(ApiFault.class,() -> service.delete(2,id));
        service.delete(1,id);
        assertThrows(ApiFault.class,() -> service.get(1,id));
    }
    private static String jwt() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":1893456000}".getBytes(StandardCharsets.UTF_8));
        return "eyJhbGciOiJub25lIn0." + payload + ".signature";
    }
}
