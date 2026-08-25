const router = require("express").Router();
const courseController = require("../controllers/course");
const isAuth = require("../middlewares/isAuth");

router.get("/", courseController.getAllCourses);

router.post("/:courseId", isAuth, courseController.bookingCourse);
router.delete("/:courseId", isAuth, courseController.deleteBookingCourse);

module.exports = router;
