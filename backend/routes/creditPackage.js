const router = require("express").Router();
const isAuth = require("../middlewares/isAuth");
const creditPackageController = require("../controllers/creditPackage");

router.get("/", creditPackageController.getAll);
router.post("/", creditPackageController.postCreditPackage);
router.delete("/:creditPackageId", creditPackageController.deleteCreditPackage);

router.post(
  "/:creditPackageId",
  isAuth,
  creditPackageController.userBuyCreditPackage,
);

module.exports = router;
