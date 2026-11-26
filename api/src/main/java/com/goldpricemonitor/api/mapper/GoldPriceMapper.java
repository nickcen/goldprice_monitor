package com.goldpricemonitor.api.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GoldPriceMapper {
    @Select("SELECT `minute`, price FROM gold_price WHERE `date` = #{date} ORDER BY `minute`")
    List<Map<String, Object>> day(@Param("date") LocalDate date);
    @Select("SELECT price FROM gold_price WHERE `date` = #{date} AND `minute` = #{minute}")
    Double price(@Param("date") LocalDate date, @Param("minute") int minute);

    @Insert("INSERT INTO gold_price (`date`, `minute`, price) VALUES (#{date}, #{minute}, #{price}) ON DUPLICATE KEY UPDATE price = VALUES(price)")
    int upsert(@Param("date") LocalDate date, @Param("minute") int minute, @Param("price") double price);

    @Insert("INSERT IGNORE INTO gold_price (`date`, `minute`, price) VALUES (#{date}, #{minute}, #{price})")
    int insertMissing(@Param("date") LocalDate date, @Param("minute") int minute, @Param("price") double price);
}
