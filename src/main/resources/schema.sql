CREATE TABLE IF NOT EXISTS sys_user (
    sys_user_id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    organization_id INTEGER,
    active_lookup_id INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address TEXT
);

CREATE TABLE IF NOT EXISTS attendance_type_lookup (
    attendance_type_lookup_id SERIAL PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS attendance_log (
    attendance_log_id SERIAL PRIMARY KEY,
    sys_user_id INTEGER REFERENCES sys_user(sys_user_id),
    attendance_type_lookup_id INTEGER REFERENCES attendance_type_lookup(attendance_type_lookup_id),
    attendance_time TIMESTAMP NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address TEXT,
    weather TEXT,
    signature TEXT,
    selfie_picture TEXT,
    organization_id INTEGER
);

INSERT INTO attendance_type_lookup (type_name) VALUES ('Attendance'), ('Exit') ON CONFLICT (type_name) DO NOTHING;