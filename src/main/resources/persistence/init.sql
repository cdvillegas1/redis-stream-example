-- noinspection SqlNoDataSourceInspectionForFile

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    age INT,
    email VARCHAR(255),
    password VARCHAR(255)
);

INSERT INTO users (name, age, email, password) VALUES
('John Doe', 30, 'john@example.com', 'password123'),
('Jane Smith', 25, 'jane@example.com', 'password456'),
('Bob Johnson', 40, 'bob@example.com', 'password789');