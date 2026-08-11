CREATE
DATABASE IF NOT EXISTS `mirizoom`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE
mirizoom;

SET
FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `reminder`;
DROP TABLE IF EXISTS `simulation_product_preferential_condition`;
DROP TABLE IF EXISTS `simulation_product`;
DROP TABLE IF EXISTS `simulation_portfolio`;
DROP TABLE IF EXISTS `simulation_tranche`;
DROP TABLE IF EXISTS `simulation_result`;
DROP TABLE IF EXISTS `simulation`;
DROP TABLE IF EXISTS `gift`;
DROP TABLE IF EXISTS `etf_holding`;
DROP TABLE IF EXISTS `etf_history_price`;
DROP TABLE IF EXISTS `etf`;
DROP TABLE IF EXISTS `family`;
DROP TABLE IF EXISTS `faq`;
DROP TABLE IF EXISTS `faq_category`;
DROP TABLE IF EXISTS `law_article`;
DROP TABLE IF EXISTS `ai_safety_report`;
DROP TABLE IF EXISTS `ai_consultation_event`;
DROP TABLE IF EXISTS `ai_conversation`;
DROP TABLE IF EXISTS `user`;

DROP TABLE IF EXISTS `preferential_interest_rate`;
DROP TABLE IF EXISTS `base_interest_rate`;
DROP TABLE IF EXISTS `interest_rate`;
DROP TABLE IF EXISTS `deposit_savings`;
DROP TABLE IF EXISTS `savings`;
DROP TABLE IF EXISTS `deposit`;

DROP TABLE IF EXISTS `kb_product_version`;
DROP TABLE IF EXISTS `kb_product_data_version`;
DROP TABLE IF EXISTS `kb_product`;

DROP TABLE IF EXISTS `gift_deduction_limit`;
DROP TABLE IF EXISTS `gift_tax_bracket`;

DROP TABLE IF EXISTS `kb_branch`;
DROP TABLE IF EXISTS `kb_desk_type`;
DROP TABLE IF EXISTS `kb_ticket_counter`;
DROP TABLE IF EXISTS `kb_ticket`;


SET
FOREIGN_KEY_CHECKS = 1;


CREATE TABLE `user`
(
    `user_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `password`       VARCHAR(255) NOT NULL,
    `user_name`      VARCHAR(100) NOT NULL,
    `birth_date`     DATE NULL,
    `phone`          VARCHAR(20)  NOT NULL,
    `role`           ENUM('ROOT', 'MIDDLE', 'DEFAULT', 'USER')
        NOT NULL DEFAULT 'USER',
    -- [추가] 권한(role)과 계정 차단 상태를 분리
    `account_status` ENUM('ACTIVE', 'BLOCKED')
        NOT NULL DEFAULT 'ACTIVE'
        COMMENT '계정 이용 상태',
    -- [추가] 기간 차단 만료 시각
    `blocked_until`  DATETIME NULL
        COMMENT '계정 차단 만료 시각',
    `created_at`     DATETIME     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL
        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `email`          VARCHAR(255) NOT NULL,
    `img`            VARCHAR(255) NULL,

    CONSTRAINT `pk_user`
        PRIMARY KEY (`user_id`),

    CONSTRAINT `uk_user_email`
        UNIQUE (`email`),

    -- [수정] 신규 제약조건 연결을 위해 기존 마지막 제약에 쉼표 추가
    CONSTRAINT `uk_user_phone`
        UNIQUE (`phone`),

    -- [추가] BLOCKED 상태에는 차단 만료 시각이 반드시 존재
    CONSTRAINT `chk_user_block_period`
        CHECK (
            (`account_status` = 'BLOCKED' AND `blocked_until` IS NOT NULL)
                OR
            (`account_status` = 'ACTIVE' AND `blocked_until` IS NULL)
            ),

    -- [추가] 만료된 차단 계정 조회용 인덱스
    INDEX            `idx_user_account_status_blocked_until` (`account_status`, `blocked_until`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `family`
(
    `family_id`   BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL,
    `family_name` VARCHAR(100) NOT NULL,
    `relation`    ENUM(
        'LINEAL_DESCENDANT',
        'OTHER'
        ) NOT NULL,
    `birth_date`  DATE         NOT NULL,
    `created_at`  DATETIME     NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL
        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `family_img`  VARCHAR(255) NULL,

    CONSTRAINT `pk_family`
        PRIMARY KEY (`family_id`),

    CONSTRAINT `fk_family_user`
        FOREIGN KEY (`user_id`)
            REFERENCES `user` (`user_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `kb_product_data_version`
(
    `kb_product_data_version_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `version_code`               VARCHAR(30) NOT NULL,
    `data_date`                  DATE        NOT NULL,
    `status`                     ENUM('LOADING', 'COMPLETED', 'FAILED') NOT NULL,
    `created_at`                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at`               DATETIME NULL,

    CONSTRAINT `pk_kb_product_data_version`
        PRIMARY KEY (`kb_product_data_version_id`),

    CONSTRAINT `uk_kb_product_data_version_code`
        UNIQUE (`version_code`),

    CONSTRAINT `chk_kb_product_data_version_completed`
        CHECK (
            `status` <> 'COMPLETED'
                OR `completed_at` IS NOT NULL
            )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `kb_product`
(
    `product_id`   BIGINT      NOT NULL AUTO_INCREMENT,
    `product_code` VARCHAR(50) NOT NULL,
    `product_type` ENUM(
        'DEPOSIT',
        'SAVINGS',
        'ETF'
        ) NOT NULL,
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_kb_product`
        PRIMARY KEY (`product_id`),

    CONSTRAINT `uk_kb_product_code`
        UNIQUE (`product_code`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `kb_product_version`
(
    `kb_product_version_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `kb_product_data_version_id` BIGINT       NOT NULL,
    `product_id`                 BIGINT       NOT NULL,
    `product_name`               VARCHAR(200) NOT NULL,
    `description`                TEXT NULL,
    `product_url`                VARCHAR(500) NULL,
    `sales_status`               ENUM('ON_SALE', 'DISCONTINUED') NOT NULL,
    `created_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_kb_product_version`
        PRIMARY KEY (`kb_product_version_id`),

    CONSTRAINT `fk_kb_product_version_data_version`
        FOREIGN KEY (`kb_product_data_version_id`)
            REFERENCES `kb_product_data_version` (`kb_product_data_version_id`)
            ON UPDATE CASCADE
            ON DELETE RESTRICT,

    CONSTRAINT `fk_kb_product_version_product`
        FOREIGN KEY (`product_id`)
            REFERENCES `kb_product` (`product_id`)
            ON UPDATE CASCADE
            ON DELETE RESTRICT,

    CONSTRAINT `uk_kb_product_version_data_version_product`
        UNIQUE (`kb_product_data_version_id`, `product_id`),

    INDEX                        `idx_kb_product_version_data_version_sales_status` (`kb_product_data_version_id`, `sales_status`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ETF 상세 정보 --
CREATE TABLE `etf`
(
    `kb_product_version_id` BIGINT        NOT NULL COMMENT 'KB상품 버전 ID',
    `stock_code`            VARCHAR(20)   NOT NULL COMMENT 'ETF 종목코드',
    `etf_category`          ENUM(
        'DOMESTIC_INDEX',
        'FOREIGN_INDEX',
        'BOND_MIXED'
        ) NOT NULL COMMENT 'ETF 분류',
    `tracking_index`        VARCHAR(200)  NOT NULL COMMENT '추종 지수',
    `annual_return_10y`     DECIMAL(8, 4) NOT NULL COMMENT '10년 연환산 수익률(%), 음수 허용',
    `bond_ratio`            DECIMAL(7, 4) NOT NULL COMMENT '채권 비중(%)',
    `risk_level`            ENUM(
        'EX_LOW',
        'LOW',
        'MEDIUM',
        'HIGH',
        'EX_HIGH'
        ) NOT NULL COMMENT '위험등급',
    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`kb_product_version_id`),

    CONSTRAINT `fk_etf_product_version`
        FOREIGN KEY (`kb_product_version_id`)
            REFERENCES `kb_product_version` (`kb_product_version_id`)
            ON UPDATE CASCADE
            ON DELETE CASCADE,

    CONSTRAINT `chk_etf_bond_ratio`
        CHECK (
            `bond_ratio` BETWEEN 0 AND 100
            )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ETF 종가 이력 --
CREATE TABLE `etf_history_price`
(
    `product_id`  BIGINT         NOT NULL,
    `base_date`   DATE           NOT NULL,
    `close_price` DECIMAL(15, 4) NOT NULL,
    `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_etf_history_price`
        PRIMARY KEY (`product_id`, `base_date`),

    CONSTRAINT `fk_etf_history_price_product`
        FOREIGN KEY (`product_id`)
            REFERENCES `kb_product` (`product_id`)
            ON UPDATE CASCADE
            ON DELETE RESTRICT,

    CONSTRAINT `chk_etf_history_price_close_price`
        CHECK (`close_price` > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ETF 구성 종목 --
CREATE TABLE `etf_holding`
(
    `holding_id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `kb_product_version_id` BIGINT        NOT NULL,
    `holding_rank`          SMALLINT      NOT NULL,
    `holding_name`          VARCHAR(200)  NOT NULL,
    `holding_code`          VARCHAR(50) NULL,
    `asset_type`            ENUM(
        'STOCK',
        'BOND',
        'ETF',
        'FUTURES',
        'CASH'
        ) NOT NULL,
    `country_code`          CHAR(2) NULL,
    `weight_percent`        DECIMAL(7, 4) NOT NULL,
    `base_date`             DATE          NOT NULL,
    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`holding_id`),

    CONSTRAINT `uk_etf_holding_rank`
        UNIQUE (`kb_product_version_id`, `holding_rank`),

    CONSTRAINT `fk_etf_holding_product_version`
        FOREIGN KEY (`kb_product_version_id`)
            REFERENCES `etf` (`kb_product_version_id`)
            ON UPDATE CASCADE
            ON DELETE CASCADE,

    CONSTRAINT `chk_etf_holding_rank`
        CHECK (`holding_rank` BETWEEN 1 AND 10),

    CONSTRAINT `chk_etf_holding_weight`
        CHECK (`weight_percent` > 0 AND `weight_percent` <= 100),

    INDEX                   `idx_etf_holding_version_rank` (`kb_product_version_id`, `holding_rank`),

    INDEX                   `idx_etf_holding_name` (`holding_name`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `deposit`
(
    `kb_product_version_id` BIGINT   NOT NULL,
    `min_amount`            BIGINT   NOT NULL,
    `max_amount`            BIGINT NULL,
    `min_month`             SMALLINT NOT NULL,
    `max_month`             SMALLINT NOT NULL,
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_deposit`
        PRIMARY KEY (`kb_product_version_id`),

    CONSTRAINT `fk_deposit_product_version`
        FOREIGN KEY (`kb_product_version_id`)
            REFERENCES `kb_product_version` (`kb_product_version_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_deposit_min_amount`
        CHECK (`min_amount` > 0),

    CONSTRAINT `chk_deposit_max_amount`
        CHECK (`max_amount` IS NULL OR `max_amount` >= `min_amount`),

    CONSTRAINT `chk_deposit_min_month`
        CHECK (`min_month` > 0),

    CONSTRAINT `chk_deposit_max_month`
        CHECK (`max_month` >= `min_month`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `savings`
(
    `kb_product_version_id` BIGINT   NOT NULL,
    `savings_category`      ENUM(
        'FIXED_INSTALLMENT',
        'FREE_INSTALLMENT'
        ) NOT NULL,
    `min_month`             SMALLINT NOT NULL,
    `max_month`             SMALLINT NOT NULL,
    `monthly_min_amount`    BIGINT   NOT NULL,
    `monthly_max_amount`    BIGINT   NOT NULL,
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_savings`
        PRIMARY KEY (`kb_product_version_id`),

    CONSTRAINT `fk_savings_product_version`
        FOREIGN KEY (`kb_product_version_id`)
            REFERENCES `kb_product_version` (`kb_product_version_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_savings_min_month`
        CHECK (`min_month` > 0),

    CONSTRAINT `chk_savings_max_month`
        CHECK (`max_month` >= `min_month`),

    CONSTRAINT `chk_savings_monthly_min_amount`
        CHECK (`monthly_min_amount` > 0),

    CONSTRAINT `chk_savings_monthly_max_amount`
        CHECK (
            `monthly_max_amount` >= `monthly_min_amount`
            )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `base_interest_rate`
(
    `base_interest_rate_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `kb_product_version_id` BIGINT        NOT NULL,
    `min_month`             SMALLINT      NOT NULL,
    `max_month`             SMALLINT NULL,
    `max_month_key`         SMALLINT
                            GENERATED ALWAYS AS (COALESCE(`max_month`, 32767)) STORED,
    `base_rate_percent`     DECIMAL(7, 4) NOT NULL,
    `max_rate_percent`      DECIMAL(7, 4) NOT NULL,
    `base_date`             DATE          NOT NULL,
    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_base_interest_rate`
        PRIMARY KEY (`base_interest_rate_id`),

    CONSTRAINT `fk_base_interest_rate_product_version`
        FOREIGN KEY (`kb_product_version_id`)
            REFERENCES `kb_product_version` (`kb_product_version_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_base_interest_rate_min_month`
        CHECK (`min_month` > 0),

    CONSTRAINT `chk_base_interest_rate_max_month`
        CHECK (`max_month` IS NULL OR `max_month` >= `min_month`),

    CONSTRAINT `chk_base_interest_rate_base_rate`
        CHECK (`base_rate_percent` >= 0),

    CONSTRAINT `chk_base_interest_rate_max_rate`
        CHECK (`max_rate_percent` >= `base_rate_percent`),

    CONSTRAINT `uk_base_interest_rate_version_term`
        UNIQUE (
                `kb_product_version_id`,
                `min_month`,
                `max_month_key`
            ),

    INDEX                   `idx_base_interest_rate_version_month` (`kb_product_version_id`, `min_month`, `max_month`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `preferential_interest_rate`
(
    `preferential_interest_rate_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `kb_product_version_id`         BIGINT        NOT NULL,
    `additional_rate_percent`       DECIMAL(7, 4) NOT NULL,
    `condition_code`                VARCHAR(50)   NOT NULL,
    `preferential_condition`        VARCHAR(500)  NOT NULL,
    `base_date`                     DATE          NOT NULL,
    `created_at`                    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_preferential_interest_rate`
        PRIMARY KEY (`preferential_interest_rate_id`),

    CONSTRAINT `fk_preferential_interest_rate_product_version`
        FOREIGN KEY (`kb_product_version_id`)
            REFERENCES `kb_product_version` (`kb_product_version_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_preferential_interest_rate_rate`
        CHECK (`additional_rate_percent` >= 0),

    CONSTRAINT `uk_preferential_interest_rate_condition`
        UNIQUE (`kb_product_version_id`, `condition_code`),

    INDEX                           `idx_preferential_interest_rate_version` (`kb_product_version_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `simulation`
(
    `simul_id`                   BIGINT      NOT NULL AUTO_INCREMENT,
    `kb_product_data_version_id` BIGINT      NOT NULL,
    `family_id`                  BIGINT      NOT NULL,
    `requested_amount`           BIGINT      NOT NULL,
    `status`                     ENUM('DRAFT', 'SAVED') NOT NULL,
    `tax_payment_method`         ENUM('RECIPIENT_PAYS', 'DONOR_PAYS') NOT NULL,
    `investment_period_months`   INT         NOT NULL,
    `as_of_date`                 DATE        NOT NULL,
    `gift_date`                  DATE        NOT NULL,
    `investment_end_date`        DATE        NOT NULL,
    `calculation_version`        VARCHAR(30) NOT NULL,
    `formula_version`            VARCHAR(30) NOT NULL,
    `version`                    BIGINT      NOT NULL DEFAULT 1,
    `selected_portfolio_id`      BIGINT NULL,
    `previous_gift_amount`       BIGINT      NOT NULL,
    `deduction_limit`            BIGINT      NOT NULL,
    `deduction_renewal_date`     DATE NULL,
    `created_at`                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `saved_at`                   DATETIME NULL,
    `expired_at`                 DATETIME NULL,

    CONSTRAINT `pk_simulation`
        PRIMARY KEY (`simul_id`),

    CONSTRAINT `fk_simulation_product_data_version`
        FOREIGN KEY (`kb_product_data_version_id`)
            REFERENCES `kb_product_data_version` (`kb_product_data_version_id`)
            ON DELETE RESTRICT
            ON UPDATE CASCADE,

    CONSTRAINT `fk_simulation_family`
        FOREIGN KEY (`family_id`)
            REFERENCES `family` (`family_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_simulation_requested_amount`
        CHECK (`requested_amount` > 0),

    CONSTRAINT `chk_simulation_investment_period`
        CHECK (`investment_period_months` BETWEEN 1 AND 240),

    CONSTRAINT `chk_simulation_investment_end_date`
        CHECK (
            `investment_end_date`
                = DATE_ADD(
                    `gift_date`,
                    INTERVAL `investment_period_months` MONTH
                                            )
            ),

    CONSTRAINT `chk_simulation_version`
        CHECK (`version` >= 1),

    CONSTRAINT `chk_simulation_gift_snapshot`
        CHECK (
            `previous_gift_amount` >= 0
                AND `deduction_limit` >= 0
            ),

    CONSTRAINT `chk_simulation_status_fields`
        CHECK (
            (
                `status` = 'DRAFT'
                    AND `saved_at` IS NULL
                    AND `expired_at` IS NOT NULL
                )
                OR
            (
                `status` = 'SAVED'
                    AND `saved_at` IS NOT NULL
                    AND `expired_at` IS NULL
                )
            ),

    INDEX                        `idx_simulation_family_history` (`family_id`, `updated_at` DESC, `simul_id` DESC),

    INDEX                        `idx_simulation_family_status` (`family_id`, `status`),

    INDEX                        `idx_simulation_status_expiry` (`status`, `expired_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `simulation_result`
(
    `simul_result_id`       BIGINT   NOT NULL AUTO_INCREMENT,
    `simul_id`              BIGINT   NOT NULL,
    `scenario_type`         ENUM('IMMEDIATE', 'TAX_OPTIMIZED') NOT NULL,
    `deduction_amount`      BIGINT   NOT NULL,
    `taxable_amount`        BIGINT   NOT NULL,
    `gift_tax`              BIGINT   NOT NULL,
    `donor_required_amount` BIGINT   NOT NULL,
    `post_tax_amount`       BIGINT   NOT NULL,
    `investment_principal`  BIGINT   NOT NULL,
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_simulation_result`
        PRIMARY KEY (`simul_result_id`),

    CONSTRAINT `uk_simulation_result_scenario`
        UNIQUE (`simul_id`, `scenario_type`),

    CONSTRAINT `fk_simulation_result_simulation`
        FOREIGN KEY (`simul_id`)
            REFERENCES `simulation` (`simul_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_simulation_result_money`
        CHECK (
            `deduction_amount` >= 0
                AND `taxable_amount` >= 0
                AND `gift_tax` >= 0
                AND `donor_required_amount` >= 0
                AND `post_tax_amount` >= 0
                AND `investment_principal` >= 0
            )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `simulation_tranche`
(
    `tranche_id`            BIGINT   NOT NULL AUTO_INCREMENT,
    `simul_result_id`       BIGINT   NOT NULL,
    `sequence_no`           INT      NOT NULL,
    `gift_date`             DATE     NOT NULL,
    `gift_amount`           BIGINT   NOT NULL,
    `estimated_gift_tax`    BIGINT   NOT NULL,
    `donor_required_amount` BIGINT   NOT NULL,
    `investment_amount`     BIGINT   NOT NULL,
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_simulation_tranche`
        PRIMARY KEY (`tranche_id`),

    CONSTRAINT `uk_simulation_tranche_result_sequence`
        UNIQUE (`simul_result_id`, `sequence_no`),

    CONSTRAINT `fk_simulation_tranche_result`
        FOREIGN KEY (`simul_result_id`)
            REFERENCES `simulation_result` (`simul_result_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_simulation_tranche_sequence`
        CHECK (`sequence_no` > 0),

    CONSTRAINT `chk_simulation_tranche_money`
        CHECK (
            `gift_amount` > 0
                AND `estimated_gift_tax` >= 0
                AND `donor_required_amount` >= 0
                AND `investment_amount` >= 0
            )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `simulation_portfolio`
(
    `simul_portfolio_id`    BIGINT   NOT NULL AUTO_INCREMENT,
    `simul_result_id`       BIGINT   NOT NULL,
    `portfolio_type`        ENUM(
        'CONSERVATIVE',
        'BALANCED',
        'AGGRESSIVE'
        ) NOT NULL,
    `deposit_amount`        BIGINT   NOT NULL,
    `savings_amount`        BIGINT   NOT NULL,
    `etf_amount`            BIGINT   NOT NULL,
    `expected_future_value` BIGINT   NOT NULL,
    `is_recommended`        TINYINT(1) NOT NULL,
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT `pk_simulation_portfolio`
        PRIMARY KEY (`simul_portfolio_id`),

    CONSTRAINT `uk_simulation_portfolio_result_type`
        UNIQUE (`simul_result_id`, `portfolio_type`),

    CONSTRAINT `fk_simulation_portfolio_result`
        FOREIGN KEY (`simul_result_id`)
            REFERENCES `simulation_result` (`simul_result_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `chk_simulation_portfolio_recommended`
        CHECK (`is_recommended` IN (0, 1)),

    CONSTRAINT `chk_simulation_portfolio_money`
        CHECK (
            `deposit_amount` >= 0
                AND `savings_amount` >= 0
                AND `etf_amount` >= 0
                AND `expected_future_value` >= 0
            )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `simulation_product`
(
    `simulation_product_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `simul_portfolio_id`    BIGINT        NOT NULL,
    `kb_product_version_id` BIGINT        NOT NULL,
    `is_selected`           TINYINT(1) NOT NULL,
    `allocated_amount`      BIGINT        NOT NULL,
    `applied_annual_rate`   DECIMAL(8, 4) NOT NULL,
    `expected_future_value` BIGINT        NOT NULL,
    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_simulation_product`
        PRIMARY KEY (`simulation_product_id`),

    CONSTRAINT `uk_simulation_product_portfolio_product`
        UNIQUE (`simul_portfolio_id`, `kb_product_version_id`),

    CONSTRAINT `fk_simulation_product_portfolio`
        FOREIGN KEY (`simul_portfolio_id`)
            REFERENCES `simulation_portfolio` (`simul_portfolio_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `fk_simulation_product_product_version`
        FOREIGN KEY (`kb_product_version_id`)
            REFERENCES `kb_product_version` (`kb_product_version_id`)
            ON DELETE RESTRICT
            ON UPDATE CASCADE,

    CONSTRAINT `chk_simulation_product_selected`
        CHECK (`is_selected` IN (0, 1)),

    CONSTRAINT `chk_simulation_product_amounts`
        CHECK (
            `allocated_amount` >= 0
                AND `expected_future_value` >= 0
            ),

    INDEX                   `idx_simulation_product_portfolio_selected` (`simul_portfolio_id`, `is_selected`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `simulation_product_preferential_condition`
(
    `simulation_product_id`         BIGINT NOT NULL,
    `preferential_interest_rate_id` BIGINT NOT NULL,

    CONSTRAINT `pk_simulation_product_preferential_condition`
        PRIMARY KEY (
                     `simulation_product_id`,
                     `preferential_interest_rate_id`
            ),

    CONSTRAINT `fk_sim_product_pref_simulation_product`
        FOREIGN KEY (`simulation_product_id`)
            REFERENCES `simulation_product` (`simulation_product_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    CONSTRAINT `fk_sim_product_pref_interest_rate`
        FOREIGN KEY (`preferential_interest_rate_id`)
            REFERENCES `preferential_interest_rate`
                (`preferential_interest_rate_id`)
            ON DELETE RESTRICT
            ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `gift`
(
    `gift_id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `family_id`       BIGINT   NOT NULL,
    -- 시뮬레이션에서 넘어온 증여의 출처 시나리오. 직접 등록한 증여는 NULL.
    `simul_result_id` BIGINT   NULL,
    -- 분할 증여의 회차. 회차마다 증여일이 달라 gift 행을 나눠 갖는다. 직접 등록한 증여는 NULL.
    `sequence_no`     INT      NULL,
    `amount`          BIGINT   NOT NULL,
    `gift_date`       DATE     NOT NULL,
    `status`          ENUM(
        'PLANNED',
        'COMPLETED',
        'CANCELLED'
        ) NOT NULL DEFAULT 'PLANNED',
    `memo`            TEXT NULL,
    `created_at`      DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT `pk_gift`
        PRIMARY KEY (`gift_id`),

    -- 같은 시나리오를 두 번 증여로 등록하는 것을 DB 에서 막는다.
    -- 직접 등록한 증여는 두 컬럼이 NULL 이고, MySQL UNIQUE 는 NULL 중복을 허용해 제약에 걸리지 않는다.
    CONSTRAINT `uk_gift_result_sequence`
        UNIQUE (`simul_result_id`, `sequence_no`),

    CONSTRAINT `fk_gift_family`
        FOREIGN KEY (`family_id`)
            REFERENCES `family` (`family_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE,

    -- 시뮬레이션을 지워도 실제 증여 이력은 남아야 하므로 CASCADE 가 아니라 SET NULL 이다.
    CONSTRAINT `fk_gift_simulation_result`
        FOREIGN KEY (`simul_result_id`)
            REFERENCES `simulation_result` (`simul_result_id`)
            ON DELETE SET NULL
            ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `reminder`
(
    `reminder_id`   BIGINT   NOT NULL AUTO_INCREMENT,
    `gift_id`       BIGINT   NOT NULL,
    `reminder_type` ENUM (
        'DEDUCTION_RENEWAL',
        'FILING_DEADLINE'
        )                    NOT NULL,
    `target_date`   DATE     NOT NULL,
    `status`        ENUM (
        'PENDING',
        'SENT',
        'FAILED'
        )                    NOT NULL DEFAULT 'PENDING',
    `sent_at`       DATETIME NULL,
    -- 사용자가 알림함에서 확인한 시각. NULL 이면 안 읽음.
    -- status 는 발송 상태라 읽음과 별개다. 발송 기능이 붙기 전까지는 PENDING 으로 남는 것이 정상.
    `read_at`       DATETIME NULL,
    `created_at`    DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT `pk_reminder`
        PRIMARY KEY (`reminder_id`),

    -- 리마인더는 (증여, 종류) 한 쌍으로 유일하다. 같은 알림을 두 번 읽어도 행이 하나만 남는다.
    CONSTRAINT `uk_reminder_gift_type`
        UNIQUE (`gift_id`, `reminder_type`),

    CONSTRAINT `fk_reminder_gift`
        FOREIGN KEY (`gift_id`)
            REFERENCES `gift` (`gift_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE `law_article`
(
    `law_id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `law_code`               VARCHAR(20)  NOT NULL COMMENT '국가법령정보 법령ID (예: 001561). 개정돼도 불변',
    `law_key`                VARCHAR(30)  NOT NULL COMMENT '법령키 = 법령ID+시행일자+공포번호. 법령 버전을 유일 식별.값이 같으면 같은 버전',
    `law_name`               VARCHAR(200) NOT NULL,
    `law_type`               VARCHAR(60)  NOT NULL COMMENT '법종구분: 법률 / 대통령령 / 재정경제부령',
    `ministry`               VARCHAR(60) NULL COMMENT '소관부처',
    `promulgation_no`        VARCHAR(20) NULL COMMENT '공포번호',
    `promulgation_date`      DATE NULL,
    `effective_date`         DATE NULL COMMENT '법령 시행일자',

    -- 조문 / 별표 단위
    `unit_type`              ENUM ('ARTICLE', 'APPENDIX') NOT NULL DEFAULT 'ARTICLE' COMMENT 'ARTICLE=조문, APPENDIX=별표',
    `article_no`             VARCHAR(50)  NOT NULL COMMENT '제3조 / 제3조의2 / [별표1]',
    `article_key`            VARCHAR(20)  NOT NULL COMMENT '조문키 또는 별표키. 법령 내 유일',
    `title`                  VARCHAR(200) NULL COMMENT '조문제목. 제목 없는 조문이 있어 NULL 허용',
    `article_effective_date` DATE NULL COMMENT '조문시행일자. 법령 시행일과 다를 수 있음',
    `content`                MEDIUMTEXT   NOT NULL COMMENT '조문내용 + 항 + 호 + 목 조립 결과',

    -- 개정 이력 (<개정 …> / <신설 …> 태그에서 분리 보존)
    `revision_history`       TEXT NULL COMMENT '"개정 2016-12-20; 신설 2018-12-31" 형태',
    `latest_revision_date`   DATE NULL COMMENT '개정 이력 중 최신 일자. 시점 필터용',

    -- 벡터 스토어 반영 시각. 배치는 건드리지 않고 RAG 쪽이 찍는다.
    -- 재임베딩 대상: WHERE embedded_at IS NULL OR embedded_at < updated_at
    `embedded_at`            DATETIME NULL COMMENT '벡터 스토어 반영 시각. NULL 이면 미반영',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `pk_law_article` PRIMARY KEY (`law_id`),
    CONSTRAINT `uk_law_article_unit` UNIQUE (`law_code`, `unit_type`, `article_key`),
    INDEX                    `idx_law_article_code` (`law_code`, `effective_date`),
    INDEX                    `idx_law_article_name_no` (`law_name`, `article_no`),
    INDEX                    `idx_law_article_revision` (`latest_revision_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;



CREATE TABLE `faq_category`
(
    `faq_category_id` INT          NOT NULL AUTO_INCREMENT,
    `category_name`   VARCHAR(200) NOT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT `pk_faq_category`
        PRIMARY KEY (`faq_category_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `faq`
(
    `faq_id`                 INT          NOT NULL,
    `faq_category_id2`       INT          NOT NULL,
    `question`               VARCHAR(255) NOT NULL,
    `prompt`                 VARCHAR(500) NOT NULL,
    `answer`                 TEXT         NOT NULL,
    `show_branch_button`     BOOLEAN      NOT NULL,
    `show_tax_office_button` BOOLEAN      NOT NULL,
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT `pk_faq`
        PRIMARY KEY (`faq_id`),

    CONSTRAINT `fk_faq_faq_category`
        FOREIGN KEY (`faq_category_id2`)
            REFERENCES `faq_category` (`faq_category_id`)
            ON DELETE CASCADE
            ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `gift_deduction_limit`
(
    `deduction_limit_id` BIGINT   NOT NULL AUTO_INCREMENT,
    `effective_from`     DATE     NOT NULL COMMENT '이 공제 한도가 적용되기 시작하는 증여일(법 시행일)',
    `effective_to`       DATE NULL
        COMMENT '다음 개정 시행일. 현행 버전이면 NULL',
    `relation`           ENUM('LINEAL_DESCENDANT', 'OTHER') NOT NULL
        COMMENT 'family.relation 과 동일',
    `is_minor`           TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '수증자 미성년 여부',
    `deduction_limit`    BIGINT   NOT NULL COMMENT '10년 합산 공제 한도(원)',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`deduction_limit_id`),

    CONSTRAINT `chk_gift_deduction_limit_minor`
        CHECK (`is_minor` IN (0, 1)),

    CONSTRAINT `chk_gift_deduction_limit_relation_minor`
        CHECK (
            `relation` = 'LINEAL_DESCENDANT'
                OR `is_minor` = 0
            ),

    CONSTRAINT `chk_gift_deduction_limit_amount`
        CHECK (`deduction_limit` >= 0)
);


CREATE TABLE `gift_tax_bracket`
(
    `bracket_id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `effective_from`        DATE          NOT NULL,
    `effective_to`          DATE NULL,
    `lower_bound`           BIGINT        NOT NULL,
    `upper_bound`           BIGINT NULL,
    `tax_rate`              DECIMAL(5, 4) NOT NULL,
    `progressive_deduction` BIGINT        NOT NULL DEFAULT 0,
    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`bracket_id`),

    CONSTRAINT `uk_gift_tax_bracket_version`
        UNIQUE (`effective_from`, `lower_bound`),

    CONSTRAINT `chk_gift_tax_bracket_lower_bound`
        CHECK (`lower_bound` >= 0),

    CONSTRAINT `chk_gift_tax_bracket_upper_bound`
        CHECK (
            `upper_bound` IS NULL
                OR `upper_bound` > `lower_bound`
            ),

    CONSTRAINT `chk_gift_tax_bracket_rate`
        CHECK (`tax_rate` >= 0 AND `tax_rate` <= 1),

    CONSTRAINT `chk_gift_tax_bracket_progressive_deduction`
        CHECK (`progressive_deduction` >= 0)
);


/* simulation_portfolio 생성 후 순환 참조 FK를 추가한다. */
ALTER TABLE `simulation`
    ADD CONSTRAINT `fk_simulation_selected_portfolio`
        FOREIGN KEY (`selected_portfolio_id`)
            REFERENCES `simulation_portfolio` (`simul_portfolio_id`)
            ON DELETE SET NULL
            ON UPDATE CASCADE;


CREATE
OR REPLACE
    SQL SECURITY INVOKER
VIEW `vw_family_previous_gift` AS

WITH ranked_deduction_rules AS (
    /*
     * 현재 날짜에 적용되는 공제 한도입니다.
     * 같은 관계·미성년 여부에 여러 규칙이 존재하면
     * 가장 최근에 시행된 규칙을 선택합니다.
     */
    SELECT
        relation,
        is_minor,
        deduction_limit,
        ROW_NUMBER() OVER (
            PARTITION BY relation, is_minor
            ORDER BY effective_from DESC,
                     deduction_limit_id DESC
        ) AS rule_rank
    FROM gift_deduction_limit
    WHERE effective_from <= CURRENT_DATE
      AND (
          effective_to IS NULL
          OR effective_to > CURRENT_DATE
      )
),

active_deduction_rules AS (
    SELECT
        relation,
        is_minor,
        deduction_limit
    FROM ranked_deduction_rules
    WHERE rule_rank = 1
),

window_gifts AS (
    /*
     * 최근 10년 안의 확정 증여를 오래된 순서로 정렬합니다.
     *
     * remaining_after:
     * 현재 증여를 제외한 뒤에도 10년 창 안에 남아 있는
     * 이후 증여 금액 합계입니다.
     */
    SELECT
        g.family_id,
        g.gift_id,
        g.amount,
        g.gift_date,

        COALESCE(
            SUM(g.amount) OVER (
                PARTITION BY g.family_id
                ORDER BY
                    g.gift_date ASC,
                    g.gift_id ASC
                ROWS BETWEEN
                    1 FOLLOWING
                    AND UNBOUNDED FOLLOWING
            ),
            0
        ) AS remaining_after
    FROM gift g
    WHERE g.status = 'COMPLETED'
      AND g.gift_date >= DATE_SUB(
          CURRENT_DATE,
          INTERVAL 10 YEAR
      )
      AND g.gift_date <= CURRENT_DATE
),

gift_summary AS (
    SELECT
        family_id,
        COUNT(*) AS completed_count,
        SUM(amount) AS previous_gift_amount,
        MAX(gift_date) AS previous_gift_date
    FROM window_gifts
    GROUP BY family_id
),

renewal_candidates AS (
    /*
     * GiftService.renewalGift()의 다음 조건과 같습니다.
     *
     * remaining -= gift.amount;
     * if (remaining < deductionLimit) {
     *     return gift;
     * }
     */
    SELECT
        wg.family_id,
        wg.gift_id,
        wg.gift_date,

        ROW_NUMBER() OVER (
            PARTITION BY wg.family_id
            ORDER BY
                wg.gift_date ASC,
                wg.gift_id ASC
        ) AS renewal_rank
    FROM window_gifts wg
    JOIN family f
      ON f.family_id = wg.family_id
    JOIN active_deduction_rules dr
      ON dr.relation = f.relation
     AND dr.is_minor =
         CASE
             WHEN f.relation = 'LINEAL_DESCENDANT'
              AND TIMESTAMPDIFF(
                      YEAR,
                      f.birth_date,
                      CURRENT_DATE
                  ) < 19
             THEN 1
             ELSE 0
         END
    WHERE wg.remaining_after < dr.deduction_limit
)

SELECT f.user_id,
       f.family_id,
       f.family_name AS name,

       TIMESTAMPDIFF(
           YEAR, f.birth_date,
                 CURRENT_DATE
       )             AS recipient_age,

       CASE
           WHEN COALESCE(gs.completed_count, 0) > 0
               THEN 1
           ELSE 0
           END       AS has_previous_gifts,

       gs.previous_gift_amount,
       gs.previous_gift_date,

       CASE
           WHEN rc.gift_date IS NULL
               THEN NULL
           ELSE DATE_ADD(
                   rc.gift_date,
                   INTERVAL 10 YEAR
        )
           END       AS deduction_renewal_date

FROM family f

         LEFT JOIN gift_summary gs
                   ON gs.family_id = f.family_id

         LEFT JOIN renewal_candidates rc
                   ON rc.family_id = f.family_id
                       AND rc.renewal_rank = 1;


CREATE TABLE ai_conversation (
                                 ai_conversation_id BIGINT NOT NULL AUTO_INCREMENT
                                     COMMENT '내부 대화 세션 ID',

                                 conversation_id CHAR(36) NULL
                                     COMMENT 'FastAPI 대화 ID',

                                 user_id BIGINT NOT NULL
                                     COMMENT '사용자 ID',

                                 status VARCHAR(20) NOT NULL
                                     DEFAULT 'ACTIVE'
                                     COMMENT 'ACTIVE, CLOSED, ARCHIVED',

                                 processing_status VARCHAR(20) NOT NULL
                                     DEFAULT 'IDLE'
                                     COMMENT 'IDLE, PROCESSING',

                                 processing_started_at DATETIME(6) NULL
                                     COMMENT '현재 질문 처리 시작 시각',

                                 transcript_json JSON NOT NULL
                                     COMMENT '보호 처리된 전체 대화 JSON',

                                 turn_count INT NOT NULL
                                     DEFAULT 0
                                     COMMENT '발급된 질문 순번',

                                 last_message_at DATETIME(6) NULL,

                                 closed_at DATETIME(6) NULL,

                                 created_at DATETIME(6) NOT NULL
                                     DEFAULT CURRENT_TIMESTAMP(6),

                                 updated_at DATETIME(6) NOT NULL
                                     DEFAULT CURRENT_TIMESTAMP(6)
                                     ON UPDATE CURRENT_TIMESTAMP(6),

                                 active_user_id BIGINT
                                                    GENERATED ALWAYS AS (
                                                        CASE
                                                            WHEN status = 'ACTIVE' THEN user_id
                                                            ELSE NULL
                                                            END
                                                        ) STORED,

                                 PRIMARY KEY (ai_conversation_id),

                                 UNIQUE KEY uk_ai_conversation_external (
                                     conversation_id
                                     ),

                                 UNIQUE KEY uk_ai_conversation_active_user (
                                     active_user_id
                                     ),

                                 KEY idx_ai_conversation_user_created (
                                     user_id,
                                     created_at
                                     ),

                                 KEY idx_ai_conversation_processing (
                                     processing_status,
                                     processing_started_at
                                     ),

                                 CONSTRAINT fk_ai_conversation_user
                                     FOREIGN KEY (user_id)
                                         REFERENCES `user` (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 상담 대화 세션';


CREATE TABLE ai_consultation_event (
                                       ai_consultation_event_id BIGINT NOT NULL AUTO_INCREMENT
                                           COMMENT 'AI 상담 분류 이벤트 ID',

    /*
     * Spring에서 관리하는 내부 대화 세션 PK입니다.
     * ai_conversation 테이블과 연결됩니다.
     */
                                       ai_conversation_id BIGINT NOT NULL
                                           COMMENT '내부 AI 대화 세션 ID',

    /*
     * FastAPI가 발급한 대화 ID입니다.
     * 하나의 conversation_id에 여러 질문 이벤트가 저장될 수 있으므로
     * UNIQUE 인덱스를 설정하면 안 됩니다.
     */
                                       conversation_id CHAR(36) NOT NULL
                                           COMMENT 'FastAPI 대화 ID',

    /*
     * 질문 요청 한 건을 식별하는 UUID입니다.
     * 동일 요청이 중복 저장되는 것을 방지합니다.
     */
                                       request_id CHAR(36) NOT NULL
                                           COMMENT '개별 질문 요청 ID',

    /*
     * 같은 대화 세션 안에서 질문 순서를 나타냅니다.
     * 예: 1, 2, 3
     */
                                       turn_no INT NOT NULL
                                           COMMENT '대화 세션 내 질문 순서',

    /*
     * 상담 요청 사용자입니다.
     * 관리자 조회 성능과 이벤트 발생 당시 사용자 추적을 위해
     * 대화 테이블과 별도로 저장합니다.
     */
                                       user_id BIGINT NOT NULL
                                           COMMENT '상담 요청 사용자 ID',

    /*
     * FastAPI가 분류한 질문 유형입니다.
     * 예: assessment, family, product, jailbreak, other
     */
                                       intent VARCHAR(30) NOT NULL
                                           COMMENT 'FastAPI 질문 분류 결과',

    /*
     * 개별 질문에 대한 FastAPI 응답 상태입니다.
     * 대화 세션의 ACTIVE 상태와는 다른 값입니다.
     *
     * 예: COMPLETED, CLARIFICATION_REQUIRED, REJECTED
     */
                                       response_status VARCHAR(30) NOT NULL
                                           COMMENT 'FastAPI 개별 질문 응답 상태',

    /*
     * 관리자 신고 화면 등에 표시할 질문 일부입니다.
     * 질문 원문이 아니라 개인정보를 제거한 내용만 저장해야 합니다.
     */
                                       question_excerpt VARCHAR(1000) NULL
                                           COMMENT '개인정보가 마스킹된 질문 일부',

    /*
     * FastAPI 분류 및 응답 처리가 완료된 시각입니다.
     */
                                       occurred_at DATETIME(6) NOT NULL
                                           DEFAULT CURRENT_TIMESTAMP(6)
                                           COMMENT '상담 분류 이벤트 발생 시각',

                                       created_at DATETIME(6) NOT NULL
                                           DEFAULT CURRENT_TIMESTAMP(6)
                                           COMMENT '이벤트 레코드 생성 시각',

                                       PRIMARY KEY (
                                                    ai_consultation_event_id
                                           ),

    /*
     * 동일한 요청이 재처리되더라도 이벤트가 중복 저장되지 않게 합니다.
     */
                                       UNIQUE KEY uk_ai_consultation_event_request (
                                           request_id
                                           ),

    /*
     * 하나의 대화 세션에서 같은 turn_no가 중복되지 않게 합니다.
     */
                                       UNIQUE KEY uk_ai_consultation_event_turn (
                                           ai_conversation_id,
                                           turn_no
                                           ),

    /*
     * 사용자별 intent 발생 횟수 및 기간 조회용 인덱스입니다.
     */
                                       KEY idx_ai_consultation_event_user_intent_time (
                                           user_id,
                                           intent,
                                           occurred_at
                                           ),

    /*
     * FastAPI conversation_id를 이용한 장애 추적용 인덱스입니다.
     * UNIQUE가 아닌 일반 인덱스여야 합니다.
     */
                                       KEY idx_ai_consultation_event_conversation (
                                           conversation_id
                                           ),

    /*
     * 특정 내부 대화의 이벤트 목록 조회용 인덱스입니다.
     */
                                       KEY idx_ai_consultation_event_internal_conversation (
                                           ai_conversation_id,
                                           occurred_at
                                           ),

    /*
     * 대화 세션이 존재하는 경우에만 이벤트를 저장할 수 있습니다.
     */
                                       CONSTRAINT fk_ai_consultation_event_conversation
                                           FOREIGN KEY (
                                                        ai_conversation_id
                                               )
                                               REFERENCES ai_conversation (
                                                                           ai_conversation_id
                                                   ),

    /*
     * 존재하는 사용자에 대해서만 이벤트를 저장합니다.
     */
                                       CONSTRAINT fk_ai_consultation_event_user
                                           FOREIGN KEY (
                                                        user_id
                                               )
                                               REFERENCES `user` (
                                                                  user_id
                                                   ),

                                       CONSTRAINT ck_ai_consultation_event_turn_no
                                           CHECK (
                                               turn_no > 0
                                               )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='질문별 AI 상담 분류 결과 이벤트';


CREATE TABLE ai_safety_report
(
    ai_safety_report_id     BIGINT       NOT NULL AUTO_INCREMENT
        COMMENT 'AI 안전 신고 ID',

    report_key              VARCHAR(100) NOT NULL COMMENT '중복 신고 방지 키',

    report_type             VARCHAR(30)  NOT NULL COMMENT 'JAILBREAK 또는 OTHER_THRESHOLD',

    status                  VARCHAR(20)  NOT NULL
        DEFAULT 'OPEN' COMMENT 'OPEN, IN_REVIEW, RESOLVED, DISMISSED',

    user_id                 BIGINT NULL
        COMMENT '신고 대상 사용자 ID',

    trigger_event_id        BIGINT       NOT NULL COMMENT '신고를 발생시킨 상담 이벤트 ID',

    occurrence_count        INT          NOT NULL DEFAULT 1
        COMMENT '신고 발생 당시 누적 횟수',

    count_window_started_at DATETIME(6) NULL
        COMMENT '집계 시작 시각',

    count_window_ended_at   DATETIME(6) NULL
        COMMENT '집계 종료 시각',

    assigned_admin_id       BIGINT NULL
        COMMENT '담당 관리자 ID',

    resolution_note         VARCHAR(2000) NULL
        COMMENT '관리자 처리 내용',

    reviewed_at             DATETIME(6) NULL
        COMMENT '검토 완료 시각',

    created_at              DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    updated_at              DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (ai_safety_report_id),

    UNIQUE KEY uk_ai_safety_report_key (
        report_key
        ),

    KEY                     idx_ai_safety_report_status_created (
        status,
        created_at
        ),

    KEY                     idx_ai_safety_report_user_created (
        user_id,
        created_at
        ),

    KEY                     idx_ai_safety_report_type_created (
        report_type,
        created_at
        ),

    CONSTRAINT fk_ai_safety_report_event
        FOREIGN KEY (trigger_event_id)
            REFERENCES ai_consultation_event (
                                              ai_consultation_event_id
                )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='관리자 검토용 AI 안전 신고';

CREATE TABLE `kb_branch` (
                             `branch_id` BIGINT NOT NULL AUTO_INCREMENT,
                             `branch_name` VARCHAR(200) NOT NULL,
                             `region` VARCHAR(20) NULL COMMENT '서울/수도권/지방 (참고용 분류)',
                             `address` VARCHAR(300) NULL,
                             `latitude` DECIMAL(10,7) NULL,
                             `longitude` DECIMAL(10,7) NULL,
                             `kakao_place_id` VARCHAR(50) NULL COMMENT '카카오 검색 결과 place_id, 매칭 검증용',
                             `kakao_search_query` VARCHAR(200) NULL COMMENT '좌표 조회에 사용한 검색어',
                             `geocoded_at` DATETIME NULL COMMENT '카카오 API로 좌표를 채운 시각, NULL이면 미확정',
                             `is_active` TINYINT(1) NOT NULL DEFAULT 1,
                             `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT `pk_kb_branch` PRIMARY KEY (`branch_id`),
                             CONSTRAINT `uk_kb_branch_name` UNIQUE (`branch_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `kb_desk_type` (
                                `desk_type_code` VARCHAR(20) NOT NULL COMMENT 'PERSONAL, DEPOSIT_WITHDRAW, CORPORATE 등',
                                `desk_type_name` VARCHAR(100) NOT NULL,
                                `prefix` CHAR(1) NOT NULL COMMENT 'A, B, D',
                                `is_operating` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '실제 운영 여부 (지금은 PERSONAL만 1)',
                                `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT `pk_kb_desk_type` PRIMARY KEY (`desk_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `kb_ticket_counter` (
                                     `counter_id` BIGINT NOT NULL AUTO_INCREMENT,
                                     `branch_id` BIGINT NOT NULL,
                                     `desk_type_code` VARCHAR(20) NOT NULL,
                                     `current_number` INT NOT NULL DEFAULT 0,
                                     `business_date` DATE NOT NULL COMMENT '영업일 기준, 매일 초기화',
                                     `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT `pk_kb_ticket_counter` PRIMARY KEY (`counter_id`),
                                     CONSTRAINT `uk_kb_ticket_counter` UNIQUE (`branch_id`, `desk_type_code`, `business_date`),
                                     CONSTRAINT `fk_kb_ticket_counter_branch` FOREIGN KEY (`branch_id`) REFERENCES `kb_branch` (`branch_id`),
                                     CONSTRAINT `fk_kb_ticket_counter_desk_type` FOREIGN KEY (`desk_type_code`) REFERENCES `kb_desk_type` (`desk_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `kb_ticket` (
                             `ticket_id` BIGINT NOT NULL AUTO_INCREMENT,
                             `branch_id` BIGINT NOT NULL,
                             `desk_type_code` VARCHAR(20) NOT NULL,
                             `service_type` VARCHAR(30) NOT NULL COMMENT 'DEPOSIT_SAVINGS_FUND_TRUST, PERSONAL_LOAN 등, 사용자가 누른 버튼',
                             `ticket_number` INT NOT NULL COMMENT '카운터에서 채번된 값',
                             `user_id` BIGINT NOT NULL,
                             `status` ENUM('WAITING', 'CALLED', 'DONE', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'WAITING',
                             `business_date` DATE NOT NULL,
                             `issued_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             `called_at` DATETIME NULL,
                             `expired_at` DATETIME NULL,
                             `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT `pk_kb_ticket` PRIMARY KEY (`ticket_id`),
                             CONSTRAINT `uk_kb_ticket_number` UNIQUE (`branch_id`, `desk_type_code`, `business_date`, `ticket_number`),
                             CONSTRAINT `fk_kb_ticket_branch` FOREIGN KEY (`branch_id`) REFERENCES `kb_branch` (`branch_id`),
                             CONSTRAINT `fk_kb_ticket_desk_type` FOREIGN KEY (`desk_type_code`) REFERENCES `kb_desk_type` (`desk_type_code`),
                             CONSTRAINT `fk_kb_ticket_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
