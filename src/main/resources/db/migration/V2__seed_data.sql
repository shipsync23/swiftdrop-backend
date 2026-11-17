-- Demo data so the system can be used immediately after start-up.
-- All demo accounts use the password: Password@123  (bcrypt, cost 12)

INSERT INTO roles (id, name) VALUES
    (1, 'CUSTOMER'),
    (2, 'MERCHANT'),
    (3, 'COURIER'),
    (4, 'DISPATCHER'),
    (5, 'ADMIN');

INSERT INTO zones (name, description) VALUES
    ('Colombo', 'Colombo district'),
    ('Gampaha', 'Gampaha district'),
    ('Kandy',   'Kandy district'),
    ('Galle',   'Galle district'),
    ('Other',   'Rest of the island');

-- Fee = base_fee + per_kg_fee * weight, chosen by destination zone and weight band
INSERT INTO pricing_rules (zone_id, min_weight_kg, max_weight_kg, base_fee, per_kg_fee)
SELECT z.id, r.min_w, r.max_w, r.base + z_extra.extra, r.per_kg
FROM zones z
JOIN (VALUES ('Colombo', 0), ('Gampaha', 50), ('Kandy', 150), ('Galle', 150), ('Other', 250))
     AS z_extra(name, extra) ON z_extra.name = z.name
CROSS JOIN (VALUES (0.00,  2.00,  350.00, 0.00),
                   (2.00,  5.00,  400.00, 60.00),
                   (5.00, 30.00,  500.00, 80.00))
     AS r(min_w, max_w, base, per_kg);

-- Staff accounts (couriers, dispatchers and admins are created by an admin, never by Google login)
INSERT INTO users (id, email, password_hash, full_name, phone, role_id) VALUES
    ('00000000-0000-4000-8000-000000000001', 'admin@swiftdrop.lk',
     '$2a$12$O1X6HrjP/Aar76Owc5M8.us/k1WU00kaV/JwH7B7Pypw3h1Dga0fi', 'System Admin', '0710000001', 5),
    ('00000000-0000-4000-8000-000000000002', 'dispatcher@swiftdrop.lk',
     '$2a$12$O1X6HrjP/Aar76Owc5M8.us/k1WU00kaV/JwH7B7Pypw3h1Dga0fi', 'Demo Dispatcher', '0710000002', 4),
    ('00000000-0000-4000-8000-000000000003', 'courier1@swiftdrop.lk',
     '$2a$12$O1X6HrjP/Aar76Owc5M8.us/k1WU00kaV/JwH7B7Pypw3h1Dga0fi', 'Kamal Perera', '0710000003', 3),
    ('00000000-0000-4000-8000-000000000004', 'courier2@swiftdrop.lk',
     '$2a$12$O1X6HrjP/Aar76Owc5M8.us/k1WU00kaV/JwH7B7Pypw3h1Dga0fi', 'Nimal Silva', '0710000004', 3),
    ('00000000-0000-4000-8000-000000000005', 'customer@swiftdrop.lk',
     '$2a$12$O1X6HrjP/Aar76Owc5M8.us/k1WU00kaV/JwH7B7Pypw3h1Dga0fi', 'Demo Customer', '0710000005', 1);

INSERT INTO couriers (user_id, zone_id, vehicle_type, vehicle_number)
SELECT '00000000-0000-4000-8000-000000000003', id, 'Motorbike', 'WP BAA-1234' FROM zones WHERE name = 'Colombo';
INSERT INTO couriers (user_id, zone_id, vehicle_type, vehicle_number)
SELECT '00000000-0000-4000-8000-000000000004', id, 'Van', 'WP CAB-5678' FROM zones WHERE name = 'Gampaha';
