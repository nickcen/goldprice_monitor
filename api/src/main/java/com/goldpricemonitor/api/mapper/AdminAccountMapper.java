package com.goldpricemonitor.api.mapper;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.goldpricemonitor.api.entity.AdminAccount;
@Mapper public interface AdminAccountMapper extends BaseMapper<AdminAccount> {
    @Select("SELECT * FROM admin_account WHERE username=#{username}") AdminAccount byUsername(String username);
    @Select("SELECT * FROM admin_account WHERE id=#{id} AND status=1") AdminAccount activeById(int id);
}
