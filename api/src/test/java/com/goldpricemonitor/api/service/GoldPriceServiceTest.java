package com.goldpricemonitor.api.service;

import com.goldpricemonitor.api.ApiFault;
import com.goldpricemonitor.api.mapper.GoldPriceMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoldPriceServiceTest {
    @Test void queryUsesUtcMinuteAndCachesForFiveMinutes() {
        GoldPriceMapper mapper = mock(GoldPriceMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(mapper.price(LocalDate.of(2026, 11, 26), 60)).thenReturn(768.48);
        var service = new GoldPriceService(mock(UserConfigService.class), mapper, redis, "", "gram", "CNY", Clock.systemUTC(), mock(RestClient.class));
        var result = service.at(OffsetDateTime.parse("2026-11-26T09:00:42+08:00").toInstant());
        assertEquals("768.48", result.get("price"));
        assertEquals("2026-11-26T01:00:00Z", result.get("dataTime"));
        verify(values).set("gold-price:2026-11-26:60", "768.48", Duration.ofMinutes(5));
    }

    @Test void missingMinuteReturns404EvenWhenRedisFails() {
        GoldPriceMapper mapper = mock(GoldPriceMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("down"));
        when(mapper.price(LocalDate.of(2026, 11, 26), 60)).thenReturn(null);
        var service = new GoldPriceService(mock(UserConfigService.class), mapper, redis, "", "gram", "CNY", Clock.systemUTC(), mock(RestClient.class));
        ApiFault fault = assertThrows(ApiFault.class, () -> service.at(Instant.parse("2026-11-26T01:00:42Z")));
        assertEquals("PRICE_NOT_FOUND", fault.code);
    }
}
