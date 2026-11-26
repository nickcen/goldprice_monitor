package com.goldpricemonitor.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldpricemonitor.api.admin.*;
import com.goldpricemonitor.api.mapper.*;
import com.goldpricemonitor.api.service.*;
import com.goldpricemonitor.api.user.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ApiControllerTest {
    final UserMapper users=mock(UserMapper.class);final UserConfigMapper configs=mock(UserConfigMapper.class);final AdminAccountMapper admins=mock(AdminAccountMapper.class);final AuthSessionMapper sessions=mock(AuthSessionMapper.class);final PaymentMapper payments=mock(PaymentMapper.class);final SkuMapper skus=mock(SkuMapper.class);
    final AuthService auth=new AuthService(users,configs,admins,sessions,new ObjectMapper(),"test-only-secret-12345678901234567890",60,"http://localhost:8080","","");
    final UserConfigService configService=new UserConfigService(configs,"gram","CNY");final UserService userService=new UserService(users,auth);final SkuService skuService=new SkuService(skus,payments);
    @Test void routesRequireAuthorizationAndValidateRegistration() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new AuthController(auth),new AdminAuthController(auth),new UserConfigController(auth,configService),new GoldPriceController(auth,new GoldPriceService(configService,"","gram","CNY")),new AdminUserController(auth,userService),new PaymentController(auth,new PaymentService(payments)),new SkuController(auth,skuService)).setControllerAdvice(new ApiErrors()).build();
        mvc.perform(get("/api/v1/me/config")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/admin/skus")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"phone\":\"abc\",\"password\":\"password123\"}")).andExpect(status().isBadRequest());
    }
    @Test void adminCanReadUserWithoutPasswordHash() {
        String hash=new BCryptPasswordEncoder().encode("admin-password");when(admins.byUsername("root")).thenReturn(Map.of("id",1,"password_hash",hash,"status",1));when(admins.activeById(1)).thenReturn(Map.of("id",1,"status",1));when(sessions.find(anyString(),eq(1),eq("ADMIN"))).thenReturn(Map.of("revoked",false,"expires_at",LocalDateTime.now(ZoneOffset.UTC).plusHours(1)));when(users.detail(7)).thenReturn(Map.of("id",7,"phone","+8613800000000","password_hash","secret"));
        String token=(String)auth.adminLogin("root","admin-password").get("token");MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+token);assertEquals("ADMIN",auth.admin(request).role());assertFalse(userService.get(7).containsKey("password_hash"));
    }
    @Test void configKeepsDecimalPrecisionAndRejectsInvalidBounds() throws Exception {
        when(configs.byUser(1)).thenReturn(Map.of("level",0,"monitor_interval_seconds",60,"min_price",new BigDecimal("900719925474.0993"),"max_price",new BigDecimal("900719925475.0000"),"gold_price_url","http://localhost"));assertEquals("900719925474.0993",configService.get(1).get("minPrice"));
        assertEquals("INVALID_PARAMETER",assertThrows(ApiFault.class,()->configService.update(1,new ObjectMapper().readTree("{\"minPrice\":\"999999999999.0000\"}"))).code);
    }
    @Test void listFiltersAndSkuConflictRemain() {
        when(users.list("138",1,10)).thenReturn(List.of());assertEquals(2,userService.list("138",1,2).get("page"));verify(users).list("138",1,10);
        when(skus.byId(5)).thenReturn(Map.of("id",5,"name","gold"));when(payments.countBySku(5)).thenReturn(1);assertEquals(HttpStatus.CONFLICT,assertThrows(ApiFault.class,()->skuService.delete(5)).status);
    }
    @Test void registrationCreatesConfigAndRefreshRevokesOldSession() {
        when(users.insert(anyMap())).thenAnswer(call->{Map<String,Object> row=call.getArgument(0);row.put("id",42);return 1;});
        assertEquals(42,auth.register("13800000000","password123").get("id"));verify(configs).insert(42,60,"http://localhost:8080/api/v1/gold-price/latest");
        String hash=new BCryptPasswordEncoder().encode("password123");when(users.byPhone("+8613800000000")).thenReturn(Map.of("id",42,"password_hash",hash,"status",1));when(users.byId(42)).thenReturn(Map.of("id",42,"status",1));when(sessions.find(anyString(),eq(42),eq("USER"))).thenReturn(Map.of("revoked",false,"expires_at",LocalDateTime.now(ZoneOffset.UTC).plusHours(1)));when(sessions.revokeActive(anyString())).thenReturn(1);
        String token=(String)auth.login("13800000000","password123").get("token");MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+token);assertEquals("USER",auth.refresh(request).get("role"));verify(sessions).revokeActive(anyString());
    }
    @Test void paymentTimeBoundsAndSkuCreation() {
        PaymentService service=new PaymentService(payments);Instant start=Instant.parse("2026-01-01T00:00:00Z"),end=Instant.parse("2026-01-02T00:00:00Z");
        assertEquals("INVALID_PARAMETER",assertThrows(ApiFault.class,()->service.list(null,null,end,start,null,null,1)).code);
        when(payments.list(eq(7),eq("pay-1"),any(),any(),isNull(),isNull(),eq(0))).thenReturn(List.of());service.list(7,"pay-1",start,end,null,null,1);verify(payments).count(7,"pay-1",LocalDateTime.ofInstant(start,ZoneOffset.UTC),LocalDateTime.ofInstant(end,ZoneOffset.UTC),null,null);
        when(skus.insert(anyMap())).thenAnswer(call->{Map<String,Object> row=call.getArgument(0);row.put("id",9);return 1;});when(skus.byId(9)).thenReturn(Map.of("id",9,"name","gold","price",100,"status",1));assertEquals(9,skuService.create(new SkuService.Input("gold",100,1,0)).get("id"));
    }
}
