CREATE TABLE url_owners (
    url_id  BIGINT PRIMARY KEY,
    user_id UUID NOT NULL
);

CREATE INDEX idx_url_owners_user_id ON url_owners(user_id);

