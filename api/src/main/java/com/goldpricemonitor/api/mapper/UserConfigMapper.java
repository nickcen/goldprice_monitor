package com.goldpricemonitor.api.mapper;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.goldpricemonitor.api.entity.UserConfig;
@Mapper public interface UserConfigMapper extends BaseMapper<UserConfig> {
    @Select("SELECT * FROM user_config WHERE user_id=#{id}") UserConfig byUser(int id);
    @Update("UPDATE user_config SET min_price=#{min},max_price=#{max} WHERE user_id=#{id}") int prices(@Param("id") int id,@Param("min") BigDecimal min,@Param("max") BigDecimal max);
}
