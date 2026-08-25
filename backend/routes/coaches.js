const router = require("express").Router();
const coachesController = require("../controllers/coaches");

router.get("/", coachesController.getCoaches);

router.get("/:coachId", coachesController.getCoachDetail);

router.get("/:coachId/courses", coachesController.getCoachCourses);

module.exports = router;
