package com.goldpricemonitor.api.service;

import com.goldpricemonitor.api.ApiFault;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class GoldAccountServiceTest {
    @Test void passwordCaptchaAndLoginUseStoredCredentialsAndOnlySaveSuccessfulToken() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_password_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(255),token VARCHAR(512),is_online TINYINT DEFAULT 0)");
        AtomicReference<String> request = new AtomicReference<>();
        AtomicBoolean accepted = new AtomicBoolean(false);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/User/GetPwdLoginCode", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            byte[] response = "{\"status\":true,\"data\":{\"base64str\":\"aW1hZ2U=\",\"cachkey\":\"key-1\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/User/GetLoginByUserName", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response = (accepted.get() ? "{\"status\":true,\"data\":{\"token\":\"password-token\"}}" : "{\"status\":false,\"msg\":\"验证码错误\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db,"http://127.0.0.1:"+server.getAddress().getPort());
            int id = (int)service.create(1,"account","secret").get("id");
            assertThrows(ApiFault.class, () -> service.passwordCode(2,id));
            assertEquals("key-1", service.passwordCode(1,id).get("cachkey"));
            assertThrows(ApiFault.class, () -> service.passwordLogin(2,id,"1234","key-1"));
            assertThrows(ApiFault.class, () -> service.passwordLogin(1,id,"bad","key-1"));
            assertFalse((boolean)service.get(1,id).get("isOnline"));
            accepted.set(true);
            service.passwordLogin(1,id,"1234","key-1");
            assertTrue(request.get().contains("\"userName\":\"account\""));
            assertTrue(request.get().contains("\"password\":\"secret\""));
            assertTrue(request.get().contains("\"cachkey\":\"key-1\""));
            assertEquals("password-token",db.queryForObject("SELECT token FROM gold_account WHERE id=?",String.class,id));
            assertFalse(service.get(1,id).containsKey("password"));
        } finally { server.stop(0); }
    }
    @Test void smsAndLoginUseRequiredPayloadAndSaveTokenOnlyOnSuccess() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_remote_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(255),token VARCHAR(512),is_online TINYINT DEFAULT 0)");
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
            byte[] response = (rejectLogin.get() ? "{\"status\":false,\"msg\":\"验证码错误\",\"data\":null}" : "{\"status\":true,\"data\":{\"token\":\"gold-token\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            GoldAccountService service = new GoldAccountService(db,"http://127.0.0.1:"+server.getAddress().getPort());
            int id = (int)service.create(1,"account",null).get("id");
            service.sendSms(1,id,"13800138000");
            assertTrue(sms.get().contains("\"messageEnum\":2"));
            assertTrue(sms.get().contains("\"code\":\"Login\""));
            ApiFault rejected = assertThrows(ApiFault.class, () -> service.login(1,id,"13800138000","bad"));
            assertEquals("验证码错误", rejected.getMessage());
            assertFalse((boolean)service.get(1,id).get("isOnline"));
            rejectLogin.set(false);
            service.login(1,id,"13800138000","123456");
            assertTrue(login.get().contains("\"loginType\":2"));
            assertEquals("gold-token",db.queryForObject("SELECT token FROM gold_account WHERE id=?",String.class,id));
            assertTrue((boolean)service.get(1,id).get("isOnline"));
        } finally { server.stop(0); }
    }
    @Test void crudIsScopedAndKeepsSecretsPrivate() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(255),token VARCHAR(512),is_online TINYINT DEFAULT 0)");
        GoldAccountService service = new GoldAccountService(db, "");
        int id = (int) service.create(1,"first","plain-password").get("id");
        assertEquals(0,db.queryForObject("SELECT is_online FROM gold_account WHERE id=?",Integer.class,id));
        assertFalse(service.get(1,id).containsKey("password"));
        assertThrows(ApiFault.class,() -> service.get(2,id));
        assertThrows(ApiFault.class,() -> service.sendSms(2,id,"13800138000"));
        service.update(1,id,"second",null,false);
        assertEquals("plain-password",db.queryForObject("SELECT password FROM gold_account WHERE id=?",String.class,id));
        assertEquals("second",service.get(1,id).get("username"));
        assertThrows(ApiFault.class,() -> service.delete(2,id));
        service.delete(1,id);
        assertThrows(ApiFault.class,() -> service.get(1,id));
    }
}
