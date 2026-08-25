const router = require("express").Router();
const adminController = require("../controllers/admin");
const isAuth = require("../middlewares/isAuth");
const isCoach = require("../middlewares/isCoach");

router.get(
  "/coaches/courses",
  isAuth,
  isCoach,
  adminController.getCoachCourses,
);

router.get(
  "/coaches/revenue",
  isAuth,
  isCoach,
  adminController.getCoachRevenue,
);

router.post(
  "/coaches/courses",
  isAuth,
  isCoach,
  adminController.postCoachCourse,
);
router.put(
  "/coaches/courses/:courseId",
  isAuth,
  isCoach,
  adminController.putCoachCourse,
);

router.get(
  "/coaches/courses",
  isAuth,
  isCoach,
  adminController.getCoachAllCourse,
);
router.post("/coaches/:userId", adminController.signup);
router.get("/coaches", isAuth, isCoach, adminController.getCoachProfile);
router.put("/coaches", isAuth, isCoach, adminController.putCoachProfile);

router.get(
  "/coaches/courses/:courseId",
  isAuth,
  isCoach,
  adminController.getCoachCourseDetail,
);

module.exports = router;
