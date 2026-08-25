const config = require("./config/index");
const express = require("express");
const cors = require("cors");
const { dataSource } = require("./db/data-source");
const appError = require("./utils/appError");

const app = express();
app.use(cors());
app.use(express.json());

// M0 healthcheck（下一步實作）
app.get("/healthcheck", async (req, res) => {
  try {
    await dataSource.query("SELECT 1");
    res.status(200).send("OK");
  } catch {
    res.status(503).send("Service Unavailable");
  }
});
// 路由掛載（後續步驟逐一加入）
app.use("/api/coaches/skill", require("./routes/skill"));
app.use("/api/users", require("./routes/users"));
app.use("/api/credit-package", require("./routes/creditPackage"));
app.use("/api/admin", require("./routes/admin"));
app.use("/api/coaches", require("./routes/coaches"));
app.use("/api/courses", require("./routes/course"));

// 404 錯誤
app.use((req, res, next) => {
  //   res.status(404).json({
  //     status: "failed",
  //     message: "無此路由",
  //   });
  //   return;
  return next(appError(404, "無此路由"));
});

// 全域錯誤處理
app.use((err, req, res, next) => {
  const statusCode = err.status || 500;
  res.status(statusCode).json({
    status: statusCode === 500 ? "error" : "failed",
    message: err.message || "伺服器錯誤",
  });
});

// 啟動
dataSource
  .initialize()
  .then(() => {
    app.listen(config.get("web.port"), () => {
      console.log(`Server running on port ${config.get("web.port")}`);
    });
  })
  .catch((err) => {
    console.error("資料庫連線失敗", err);
    process.exit(1);
  });
