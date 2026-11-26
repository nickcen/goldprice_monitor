package com.goldpricemonitor.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldpricemonitor.api.mapper.GoldPriceMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private static final ZoneId PRICE_ZONE = ZoneId.of("Asia/Shanghai");
    private final String source, priceServerBaseUrl, unit, currency;
    private final UserConfigService configs;
    private final GoldPriceMapper mapper;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final RestClient http;

    @Autowired
    public GoldPriceService(UserConfigService configs, GoldPriceMapper mapper, StringRedisTemplate redis,
            @Value("${app.price-source-url:}") String source,
            @Value("${app.price-server-base-url:}") String priceServerBaseUrl,
            @Value("${app.unit:gram}") String unit, @Value("${app.currency:CNY}") String currency) {
        this(configs, mapper, redis, source, priceServerBaseUrl, unit, currency, Clock.systemUTC(), client());
    }

    public GoldPriceService(UserConfigService configs, GoldPriceMapper mapper, StringRedisTemplate redis,
            String source, String unit, String currency) {
        this(configs, mapper, redis, source, "", unit, currency, Clock.systemUTC(), client());
    }

    GoldPriceService(UserConfigService configs, GoldPriceMapper mapper, StringRedisTemplate redis,
            String source, String unit, String currency, Clock clock, RestClient http) {
        this(configs, mapper, redis, source, "", unit, currency, clock, http);
    }

    GoldPriceService(UserConfigService configs, GoldPriceMapper mapper, StringRedisTemplate redis,
            String source, String priceServerBaseUrl, String unit, String currency, Clock clock, RestClient http) {
        this.configs = configs; this.mapper = mapper; this.redis = redis;
        this.source = source; this.priceServerBaseUrl = priceServerBaseUrl; this.unit = unit; this.currency = currency; this.clock = clock; this.http = http;
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

    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        if (priceServerBaseUrl.isBlank()) return;
        try {
            JsonNode response = http.get().uri(priceServerBaseUrl.replaceAll("/+$", "")
                    + "/api/Accumulate/GetGoldPriceList?type=0").retrieve().body(JsonNode.class);
            if (response == null || !response.path("status").asBoolean() || !response.path("data").isArray())
                throw new IllegalArgumentException("Invalid historical price response");
            Instant now = Instant.now(clock);
            LocalDate sourceDate = now.atZone(PRICE_ZONE).toLocalDate();
            int inserted = 0;
            for (JsonNode item : response.path("data")) {
                try {
                    LocalTime time = LocalTime.parse(item.path("time").asText());
                    Instant instant = sourceDate.atTime(time).atZone(PRICE_ZONE).toInstant();
                    if (instant.isAfter(now)) continue;
                    BigDecimal price = configs.decimal(item.get("last_price"));
                    inserted += mapper.insertMissing(sourceDate, time.getHour() * 60 + time.getMinute(), price.doubleValue());
                } catch (RuntimeException e) { log.warn("Skipping invalid historical gold price entry: {}", item, e); }
            }
            log.info("Backfilled {} missing gold price minutes", inserted);
        } catch (RuntimeException e) { log.warn("Unable to backfill historical gold prices on startup", e); }
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void collect() {
        Instant minute = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        try {
            BigDecimal price = fetchSourcePrice();
            var beijing = minute.atZone(PRICE_ZONE);
            LocalDate date = beijing.toLocalDate();
            int index = beijing.getHour() * 60 + beijing.getMinute();
            mapper.upsert(date, index, price.doubleValue());
            log.info("Collected gold price date={} minute={} price={}", date, index, price);
            try { redis.delete(key(date, index)); }
            catch (RuntimeException e) { log.warn("Unable to invalidate gold price cache", e); }
        } catch (RuntimeException e) { log.warn("Unable to collect gold price for {}", minute, e); }
    }

    public Map<String,Object> at(Instant time) {
        Instant minute = time.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        var beijing = minute.atZone(PRICE_ZONE);
        LocalDate date = beijing.toLocalDate();
        int index = beijing.getHour() * 60 + beijing.getMinute();
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

    public List<Map<String, Object>> day(LocalDate date) {
        return mapper.day(date);
    }

    private static String key(LocalDate date, int minute) { return "gold-price:beijing:" + date + ":" + minute; }

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
