package com.goldpricemonitor.api.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GoldPriceMapper {
    @Select("SELECT price FROM gold_price WHERE `date` = #{date} AND `minute` = #{minute}")
    Double price(@Param("date") LocalDate date, @Param("minute") int minute);

    @Insert("INSERT INTO gold_price (`date`, `minute`, price) VALUES (#{date}, #{minute}, #{price}) ON DUPLICATE KEY UPDATE price = VALUES(price)")
    int upsert(@Param("date") LocalDate date, @Param("minute") int minute, @Param("price") double price);
}
