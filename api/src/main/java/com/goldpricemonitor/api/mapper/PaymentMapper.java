package com.goldpricemonitor.api.mapper;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.goldpricemonitor.api.entity.Payment;
@Mapper public interface PaymentMapper extends BaseMapper<Payment> {
    String FILTER="<if test='userId != null'> AND p.user_id=#{userId}</if><if test='paymentNumber != null and paymentNumber != \"\"'> AND p.payment_number=#{paymentNumber}</if><if test='createdFrom != null'> AND p.create_time &gt;= #{createdFrom}</if><if test='createdTo != null'> AND p.create_time &lt; #{createdTo}</if><if test='paidFrom != null'> AND p.pay_time &gt;= #{paidFrom}</if><if test='paidTo != null'> AND p.pay_time &lt; #{paidTo}</if>";
    String FROM=" FROM payment p JOIN `user` u ON u.id=p.user_id JOIN sku s ON s.id=p.sku_id WHERE 1=1 ";
    @Select("<script>SELECT count(*)"+FROM+FILTER+"</script>") long count(@Param("userId") Integer userId,@Param("paymentNumber") String paymentNumber,@Param("createdFrom") LocalDateTime createdFrom,@Param("createdTo") LocalDateTime createdTo,@Param("paidFrom") LocalDateTime paidFrom,@Param("paidTo") LocalDateTime paidTo);
    @Select("<script>SELECT p.id,p.user_id AS userId,u.phone,p.sku_id AS skuId,s.name AS skuName,p.payment_number AS paymentNumber,p.thirdparty_number AS thirdpartyNumber,p.pay_time AS payTime,p.create_time AS createTime"+FROM+FILTER+" ORDER BY p.create_time DESC,p.id DESC LIMIT 10 OFFSET #{offset}</script>") List<Payment> list(@Param("userId") Integer userId,@Param("paymentNumber") String paymentNumber,@Param("createdFrom") LocalDateTime createdFrom,@Param("createdTo") LocalDateTime createdTo,@Param("paidFrom") LocalDateTime paidFrom,@Param("paidTo") LocalDateTime paidTo,@Param("offset") int offset);
    @Select("SELECT count(*) FROM payment WHERE sku_id=#{id}") int countBySku(int id);
}
