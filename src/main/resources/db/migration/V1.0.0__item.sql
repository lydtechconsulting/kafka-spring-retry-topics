CREATE SCHEMA IF NOT EXISTS kafka_retry_with_delay;

CREATE TABLE kafka_retry_with_delay.item (
    id uuid NOT NULL,
    name varchar(32) NOT NULL,
    status varchar(8) NOT NULL,
    CONSTRAINT item_pkey PRIMARY KEY (id)
);
