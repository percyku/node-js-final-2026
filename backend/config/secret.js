module.exports = {
  jwtSecret: process.env.JWT_SECRET || "defaultsecret",
  jwtExpiresDay: process.env.JWT_EXPIRES_DAY || "30d",
};
