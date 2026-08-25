const { IsNull, MoreThan, LessThanOrEqual } = require("typeorm");
const { dataSource } = require("../db/data-source");
const appError = require("../utils/appError");
const {
  isValidString,
  isValidPassword,
  isInteger,
} = require("../utils/validUtils");

const courseController = {
  async getAllCourses(req, res, next) {
    const courses = await dataSource.getRepository("Course").find({
      select: {
        id: true,
        name: true,
        description: true,
        start_at: true,
        end_at: true,
        max_participants: true,
        user: {
          name: true,
        },
        skill: {
          name: true,
        },
      },
      where: {
        start_at: LessThanOrEqual(new Date()),
        end_at: MoreThan(new Date()),
      },
      relations: {
        user: true,
        skill: true,
      },
    });

    res.status(200).json({
      status: "success",
      //   data: {},
      data: courses.map((course) => {
        return {
          id: course.id,
          name: course.name,
          description: course.description,
          start_at: course.start_at,
          end_at: course.end_at,
          max_participants: course.max_participants,
          coach_name: course.user.name,
          skill_name: course.skill.name,
        };
      }),
    });
  },

  async bookingCourse(req, res, next) {
    const { id } = req.user;
    const { courseId } = req.params;
    // const courseRepo = dataSource.getRepository("Course");
    const course = await dataSource.getRepository("Course").findOneBy({
      id: courseId,
    });
    if (!course) {
      return next(appError(400, "ID錯誤"));
    }

    const creditPurchaseRepo = dataSource.getRepository("CreditPurchase");
    const courseBookingRepo = dataSource.getRepository("CourseBooking");
    const existUserBookingCourse = await courseBookingRepo.findOneBy({
      user_id: id,
      course_id: courseId,
    });
    if (existUserBookingCourse) {
      return next(appError(400, "已經報名過此課程"));
    }

    const purchases = await creditPurchaseRepo.find({
      where: {
        user_id: id,
      },
    });

    const total = purchases.reduce((sum, p) => sum + p.purchased_credits, 0);
    const unJoin = await courseBookingRepo.count({
      where: {
        user_id: id,
        cancelled_at: IsNull(),
      },
    });

    if (total - unJoin <= 0) {
      return next(appError(400, "已無可使用堂數"));
    }

    const bookingCount = await courseBookingRepo.count({
      where: {
        course_id: courseId,
        cancelled_at: IsNull(),
      },
    });

    if (bookingCount >= course.max_participants) {
      return next(appError(400, "已達最大參加人數，無法參加"));
    }

    const newCourseBooking = await courseBookingRepo.create({
      user_id: id,
      course_id: courseId,
    });
    await courseBookingRepo.save(newCourseBooking);
    res.status(200).json({
      status: "success",
      //   data: {},
      data: null,
    });
  },

  async deleteBookingCourse(req, res, next) {
    const { id } = req.user;
    const { courseId } = req.params;
    const courseBookingRepo = dataSource.getRepository("CourseBooking");
    const userCourseBooking = await courseBookingRepo.findOneBy({
      user_id: id,
      course_id: courseId,
      cancelled_at: IsNull(),
    });
    if (!userCourseBooking) {
      return next(appError(400, "ID錯誤"));
    }

    userCourseBooking.cancelled_at = new Date();
    const updateResult = await courseBookingRepo.save(userCourseBooking);

    if (updateResult.affected === 0) {
      return next(appError(400, "取消失敗"));
    }

    res.status(200).json({
      status: "success",
      data: null,
    });
  },
};

module.exports = courseController;
