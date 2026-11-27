package com.goldpricemonitor.api.service;

import com.goldpricemonitor.api.ApiFault;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class GoldAccountServiceTest {
    @Test void smsAndLoginUseRequiredPayloadAndSaveTokenOnlyOnSuccess() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:gold_remote_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("CREATE TABLE gold_account (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,username VARCHAR(120),password VARCHAR(255),token VARCHAR(512),is_online TINYINT DEFAULT 0)");
        AtomicReference<String> sms = new AtomicReference<>(), login = new AtomicReference<>();
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
            byte[] response = "{\"status\":true,\"data\":{\"token\":\"gold-token\"}}".getBytes(StandardCharsets.UTF_8);
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
