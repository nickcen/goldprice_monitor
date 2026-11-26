package com.goldpricemonitor.api.service;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import static com.goldpricemonitor.api.service.ApiSupport.error;
@Service public class GoldPriceService {
    private final String source,unit,currency;private final UserConfigService configs;private final RestClient http;
    public GoldPriceService(UserConfigService configs,@Value("${app.price-source-url:}") String source,@Value("${app.unit:gram}") String unit,@Value("${app.currency:CNY}") String currency){this.configs=configs;this.source=source;this.unit=unit;this.currency=currency;var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(Duration.ofSeconds(3));f.setReadTimeout(Duration.ofSeconds(5));this.http=RestClient.builder().requestFactory(f).build();}
    public Map<String,Object> latest(){if(source.isBlank())throw error(HttpStatus.SERVICE_UNAVAILABLE,"PRICE_SOURCE_UNAVAILABLE","金价数据源未配置");try{JsonNode node=http.get().uri(source).retrieve().body(JsonNode.class);if(node==null||!node.has("price")||!node.has("dataTime"))throw new IllegalArgumentException();BigDecimal price=configs.decimal(node.get("price"));Instant time=Instant.parse(node.get("dataTime").asText());return Map.of("price",price.toPlainString(),"unit",unit,"currency",currency,"precision",4,"dataTime",time.toString());}catch(Exception e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"PRICE_SOURCE_UNAVAILABLE","金价数据源不可用");}}
}
