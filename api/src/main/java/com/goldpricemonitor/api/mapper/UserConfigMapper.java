package com.goldpricemonitor.api.mapper;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.ibatis.annotations.*;
@Mapper public interface UserConfigMapper {
    @Insert("INSERT INTO user_config(user_id,monitor_interval_seconds,level,gold_price_url) VALUES (#{id},#{interval},0,#{url})") int insert(@Param("id") int id,@Param("interval") int interval,@Param("url") String url);
    @Select("SELECT * FROM user_config WHERE user_id=#{id}") Map<String,Object> byUser(int id);
    @Update("UPDATE user_config SET min_price=#{min},max_price=#{max} WHERE user_id=#{id}") int prices(@Param("id") int id,@Param("min") BigDecimal min,@Param("max") BigDecimal max);
}
