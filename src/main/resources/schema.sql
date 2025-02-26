-- 创建错误代码记录表
CREATE TABLE IF NOT EXISTS error_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_content TEXT NOT NULL,
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT,
    problem_description TEXT,
    submit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 创建错误类型统计视图
CREATE OR REPLACE VIEW error_statistics AS
SELECT 
    user_id,
    error_type,
    COUNT(*) as error_count
FROM error_records
GROUP BY user_id, error_type;

-- 添加索引以提高查询性能
CREATE INDEX idx_user_error_type ON error_records(user_id, error_type);
CREATE INDEX idx_submit_time ON error_records(submit_time);