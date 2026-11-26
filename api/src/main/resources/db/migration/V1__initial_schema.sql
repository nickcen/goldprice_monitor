CREATE TABLE `user` (
    id INT NOT NULL AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL COMMENT '登录手机号，含国家代码',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    status INT NOT NULL DEFAULT 1 COMMENT '0=不可用，1=可用',
    valid_from_date DATE NULL COMMENT '有效期开始日期，含当日',
    valid_to_date DATE NULL COMMENT '有效期结束日期，不含当日',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone),
    CONSTRAINT chk_user_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_user_valid_date CHECK (
        valid_from_date IS NULL OR valid_to_date IS NULL OR valid_to_date > valid_from_date
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_config (
    id INT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    min_price DECIMAL(18, 4) NULL COMMENT '金价低价阈值',
    max_price DECIMAL(18, 4) NULL COMMENT '金价高价阈值',
    monitor_interval_seconds INT NOT NULL COMMENT '监控频率，单位：秒',
    level INT NOT NULL DEFAULT 0 COMMENT '用户等级，具体取值由业务定义',
    gold_price_url VARCHAR(2048) NOT NULL COMMENT '金价监控地址',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_config_user (user_id),
    CONSTRAINT fk_user_config_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT chk_user_config_min_price CHECK (min_price IS NULL OR min_price >= 0),
    CONSTRAINT chk_user_config_max_price CHECK (max_price IS NULL OR max_price >= 0),
    CONSTRAINT chk_user_config_price_range CHECK (min_price IS NULL OR max_price IS NULL OR max_price > min_price),
    CONSTRAINT chk_user_config_interval CHECK (monitor_interval_seconds > 0)
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
