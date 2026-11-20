# 数据库设计（初版）

数据库使用 MySQL，以下建表语句按 MySQL 8.0 编写。所有主键和外键 ID 均为 `INT`，所有表均包含 `create_time` 和 `update_time`。时间字段使用 `DATETIME(3)`，应用和数据库连接统一使用 UTC，展示时转换到用户时区。

密码只保存加盐后的密码哈希，不保存明文；字段命名为 `password_hash`。手机号以字符串保存，应用写入前统一为带国家代码的格式（如 `+8613800000000`），避免同一号码出现多种写法。

```sql
CREATE TABLE `user` (
    id INT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL COMMENT '登录手机号，含国家代码',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    level INT NOT NULL DEFAULT 0 COMMENT '用户等级，具体取值由业务定义',
    status INT NOT NULL DEFAULT 1 COMMENT '0=不可用，1=可用',
    valid_fromtime DATETIME(3) NULL COMMENT '有效期开始，含此时刻',
    valid_totime DATETIME(3) NULL COMMENT '有效期结束，不含此时刻',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone),
    CONSTRAINT chk_user_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_user_valid_time CHECK (
        valid_fromtime IS NULL OR valid_totime IS NULL OR valid_totime > valid_fromtime
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sku (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL COMMENT '商品名称',
    price INT NOT NULL COMMENT '价格，单位：分',
    status INT NOT NULL DEFAULT 1 COMMENT '0=不可用，1=可用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '升序排序值',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_sku_status_sort (status, sort_order, id),
    CONSTRAINT chk_sku_price CHECK (price >= 0),
    CONSTRAINT chk_sku_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    sku_id INT NOT NULL,
    payment_number VARCHAR(64) NOT NULL COMMENT '系统支付单号',
    thirdparty_number VARCHAR(128) NULL COMMENT '第三方支付单号，支付前可为空',
    pay_time DATETIME(3) NULL COMMENT '实际支付时间，未支付时为空',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_number (payment_number),
    UNIQUE KEY uk_payment_thirdparty_number (thirdparty_number),
    KEY idx_payment_user_time (user_id, create_time),
    KEY idx_payment_sku (sku_id),
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_payment_sku FOREIGN KEY (sku_id) REFERENCES sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 关系与约束

- 一个用户可有多条支付记录；一个商品可关联多条支付记录。支付记录保留对应用户和商品的外键关系。
- `payment_number` 全局唯一。MySQL 唯一索引允许多个 `NULL`，所以尚未取得第三方单号的支付记录可以并存；取得后第三方单号必须唯一。
- 商品列表按 `sort_order ASC, id ASC` 排序，`id` 用于排序值相同时稳定排序。
- 有效期的空值表示该侧不设边界；有效区间为 `[valid_fromtime, valid_totime)`。用户是否可用还需同时检查 `status = 1`。
- `pay_time` 为空表示尚无已支付时间，但仅凭该列无法表达取消、退款等状态。支付状态、支付渠道、实付金额及商品快照等字段待支付流程确定后补充，避免商品价格变更影响历史订单的解释。
- 删除用户或商品前必须处理相关支付记录；外键默认阻止直接删除。业务上宜停用而非删除已有交易关联的记录。
