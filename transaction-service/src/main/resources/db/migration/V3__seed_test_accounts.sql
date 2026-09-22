INSERT INTO accounts (id, account_number, balance, currency, status, created_at, updated_at)
VALUES 
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'ACC-1001', 1000.00, 'USD', 'ACTIVE', NOW(), NOW()),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', 'ACC-1002', 500.00, 'USD', 'ACTIVE', NOW(), NOW()),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', 'ACC-1003', 2500.00, 'USD', 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;
