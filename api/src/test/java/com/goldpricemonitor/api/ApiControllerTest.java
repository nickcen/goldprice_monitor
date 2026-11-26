package com.goldpricemonitor.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
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
    @Test void configPricesAreDecimalStrings() {
        var db=mock(JdbcTemplate.class);
        when(db.queryForList("SELECT * FROM user_config WHERE user_id=?", 1)).thenReturn(List.of(Map.of(
            "level", 0, "monitor_interval_seconds", 60, "min_price", new BigDecimal("900719925474.0993"),
            "max_price", new BigDecimal("900719925475.0000"), "gold_price_url", "http://localhost:8080/api/v1/gold-price/latest")));
        var controller=new ApiController(db,new ObjectMapper(),"test-only-secret-12345678901234567890","","CNY","gram",60,"http://localhost:8080","","");
        Map<?,?> value=ReflectionTestUtils.invokeMethod(controller,"config",1);
        org.junit.jupiter.api.Assertions.assertEquals("900719925474.0993",value.get("minPrice"));
        org.junit.jupiter.api.Assertions.assertEquals("900719925475.0000",value.get("maxPrice"));
    }
}
