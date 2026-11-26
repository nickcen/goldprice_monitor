package com.goldpricemonitor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldpricemonitor.api.mapper.GoldPriceMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import static com.goldpricemonitor.api.service.ApiSupport.error;

@Service
public class GoldPriceService {
    private static final Logger log = LoggerFactory.getLogger(GoldPriceService.class);
    private final String source, unit, currency;
    private final UserConfigService configs;
    private final GoldPriceMapper mapper;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final RestClient http;

    @Autowired
    public GoldPriceService(UserConfigService configs, GoldPriceMapper mapper, StringRedisTemplate redis,
            @Value("${app.price-source-url:}") String source,
            @Value("${app.unit:gram}") String unit, @Value("${app.currency:CNY}") String currency) {
        this(configs, mapper, redis, source, unit, currency, Clock.systemUTC(), client());
    }

    GoldPriceService(UserConfigService configs, GoldPriceMapper mapper, StringRedisTemplate redis,
            String source, String unit, String currency, Clock clock, RestClient http) {
        this.configs = configs; this.mapper = mapper; this.redis = redis;
        this.source = source; this.unit = unit; this.currency = currency; this.clock = clock; this.http = http;
    }

    private static RestClient client() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3)); f.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(f).build();
    }

    public Map<String,Object> latest() {
        BigDecimal price = fetchSourcePrice();
        return response(price.toPlainString(), Instant.now(clock));
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void collect() {
        Instant minute = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        try {
            BigDecimal price = fetchSourcePrice();
            LocalDate date = minute.atZone(ZoneOffset.UTC).toLocalDate();
            int index = minute.atZone(ZoneOffset.UTC).getHour() * 60 + minute.atZone(ZoneOffset.UTC).getMinute();
            mapper.upsert(date, index, price.doubleValue());
            try { redis.delete(key(date, index)); }
            catch (RuntimeException e) { log.warn("Unable to invalidate gold price cache", e); }
        } catch (RuntimeException e) { log.warn("Unable to collect gold price for {}", minute, e); }
    }

    public Map<String,Object> at(Instant time) {
        Instant minute = time.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        var utc = minute.atZone(ZoneOffset.UTC);
        LocalDate date = utc.toLocalDate();
        int index = utc.getHour() * 60 + utc.getMinute();
        String cacheKey = key(date, index);
        String value = null;
        try { value = redis.opsForValue().get(cacheKey); }
        catch (RuntimeException e) { log.warn("Unable to read gold price cache", e); }
        if (value == null) {
            Double stored = mapper.price(date, index);
            if (stored == null) throw error(HttpStatus.NOT_FOUND, "PRICE_NOT_FOUND", "该分钟尚无金价记录");
            value = BigDecimal.valueOf(stored).stripTrailingZeros().toPlainString();
            try { redis.opsForValue().set(cacheKey, value, Duration.ofMinutes(5)); }
            catch (RuntimeException e) { log.warn("Unable to write gold price cache", e); }
        }
        return response(value, minute);
    }

    private static String key(LocalDate date, int minute) { return "gold-price:" + date + ":" + minute; }

    private Map<String,Object> response(String price, Instant time) {
        return Map.of("price", price, "unit", unit, "currency", currency, "precision", 2, "dataTime", time.toString());
    }

    private BigDecimal fetchSourcePrice() {
        if (source.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_SOURCE_UNAVAILABLE", "金价数据源未配置");
        try {
            JsonNode node = http.get().uri(source).retrieve().body(JsonNode.class);
            if (node == null) throw new IllegalArgumentException("Empty price response");
            JsonNode raw;
            if (node.has("status") && node.has("data")) {
                if (!node.path("status").asBoolean() || node.path("code").asInt() != 1) throw new IllegalArgumentException("Source rejected request");
                raw = node.get("data");
            } else {
                raw = node.get("price");
                if (!node.hasNonNull("dataTime")) throw new IllegalArgumentException("Missing dataTime");
                Instant.parse(node.get("dataTime").asText());
            }
            if (raw == null) throw new IllegalArgumentException("Missing price");
            return configs.decimal(raw);
        } catch (RuntimeException e) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "PRICE_SOURCE_UNAVAILABLE", "金价数据源不可用");
        }
    }
}
