package com.goldpricemonitor.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Test void badParametersAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"phone\":\"abc\",\"password\":\"password123\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"phone\":\"13800000000\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest());
    }
}
