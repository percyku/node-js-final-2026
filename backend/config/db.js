module.exports = {
  host: process.env.DB_HOST || "localhost",
  port: process.env.DB_PORT || 5432,
  username: process.env.DB_USERNAME || "student",
  password: process.env.DB_PASSWORD || "student666",
  database: process.env.DB_DATABASE || "fitness",
  synchronize: process.env.DB_SYNCHRONIZE === "true",
  ssl: process.env.DB_ENABLE_SSL === "true",
};
