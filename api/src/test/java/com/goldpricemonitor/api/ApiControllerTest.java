package com.goldpricemonitor.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ApiControllerTest {
    MockMvc mvc;
    @BeforeEach void setup() {
        var controller=new ApiController(mock(JdbcTemplate.class),new ObjectMapper(),"test-only-secret-12345678901234567890","","CNY","gram",60,"http://localhost:8080","","");
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiErrors()).build();
    }
    @Test void protectedRoutesNeedToken() throws Exception {
        mvc.perform(get("/api/v1/me/config")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/admin/skus")).andExpect(status().isUnauthorized());
    }
    @Test void adminCanReadUserWithoutPasswordHash() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        String hash=new BCryptPasswordEncoder().encode("admin-password");
        when(db.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if(sql.contains("admin_account WHERE username")) return List.of(Map.of("id",1,"username","root","password_hash",hash,"status",1));
            if(sql.contains("auth_session WHERE")) return List.of(Map.of("id","session","principal_id",1,"role","ADMIN","revoked",false,"expires_at",java.time.LocalDateTime.now().plusHours(1)));
            if(sql.contains("admin_account WHERE id")) return List.of(Map.of("id",1,"status",1));
            if(sql.contains("FROM `user` u")) return List.of(Map.of("id",7,"phone","+8613800000000","status",1,"createTime",java.time.LocalDateTime.now(),"password_hash","must-not-appear"));
            return List.of();
        });
        var controller=new ApiController(db,new ObjectMapper(),"test-only-secret-12345678901234567890","","CNY","gram",60,"http://localhost:8080","","");
        String token=(String)controller.adminLogin(new ApiController.AdminLogin("root","admin-password")).get("token");
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer "+token);
        Map<String,Object> user=controller.getUser(request,7);
        assertEquals(7,user.get("id")); assertFalse(user.containsKey("password_hash"));
    }
    @Test void badParametersAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"phone\":\"abc\",\"password\":\"password123\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"phone\":\"13800000000\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest());
    }
}
