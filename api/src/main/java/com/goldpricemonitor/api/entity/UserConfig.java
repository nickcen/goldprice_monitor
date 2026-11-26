package com.goldpricemonitor.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("user_config")
public class UserConfig {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer monitorIntervalSeconds;
    private Integer level;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public Integer getMonitorIntervalSeconds() { return monitorIntervalSeconds; }
    public void setMonitorIntervalSeconds(Integer monitorIntervalSeconds) { this.monitorIntervalSeconds = monitorIntervalSeconds; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
