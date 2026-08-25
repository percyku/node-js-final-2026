const router = require("express").Router();
const usersController = require("../controllers/users");
const isAuth = require("../middlewares/isAuth");

router.post("/signup", usersController.signup);
router.post("/login", usersController.login);
router.get("/profile", isAuth, usersController.getProfile);
router.put("/profile", isAuth, usersController.updateProfileName);
router.put("/password", isAuth, usersController.updateProfilePassword);

router.get("/credit-package", isAuth, usersController.getUserCreditPurchase);
router.get("/courses", isAuth, usersController.getUserBooking);

module.exports = router;
