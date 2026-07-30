-- 公开注册只允许创建客户；客服账号由管理员人员管理模块显式创建。
ALTER TABLE support_users ALTER COLUMN role_code SET DEFAULT 'CUSTOMER';

-- 修复旧公开注册产生的低额度、尚未绑定客户资料的客服账号。
INSERT INTO customers (customer_no, customer_name, status)
SELECT CONCAT('CUST-', LPAD(u.id, 10, '0')), u.display_name, 'ACTIVE'
  FROM support_users u
  LEFT JOIN customer_accounts ca ON ca.user_id = u.id
 WHERE u.role_code = 'SUPPORT_AGENT'
   AND u.daily_quota = 10
   AND ca.user_id IS NULL
ON DUPLICATE KEY UPDATE customer_name = VALUES(customer_name);

INSERT INTO customer_accounts (user_id, customer_id)
SELECT u.id, c.id
  FROM support_users u
  JOIN customers c ON c.customer_no = CONCAT('CUST-', LPAD(u.id, 10, '0'))
  LEFT JOIN customer_accounts ca ON ca.user_id = u.id
 WHERE u.role_code = 'SUPPORT_AGENT'
   AND u.daily_quota = 10
   AND ca.user_id IS NULL;

UPDATE support_users u
JOIN customer_accounts ca ON ca.user_id = u.id
   SET u.role_code = 'CUSTOMER', u.updated_at = CURRENT_TIMESTAMP(3)
 WHERE u.role_code = 'SUPPORT_AGENT'
   AND u.daily_quota = 10;
