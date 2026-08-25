const { dataSource } = require("../db/data-source");
const appError = require("../utils/appError");
const {
  isValidString,
  isValidPassword,
  isInteger,
  formatLocalDate,
} = require("../utils/validUtils");

const monthMap = {
  january: 1,
  february: 2,
  march: 3,
  april: 4,
  may: 5,
  june: 6,
  july: 7,
  august: 8,
  september: 9,
  october: 10,
  november: 11,
  december: 12,
};

const adminController = {
  async signup(req, res, next) {
    const { userId } = req.params;
    const { experience_years, description, profile_image_url } = req.body;
    if (
      !isValidString(userId) ||
      !isValidString(description) ||
      !isInteger(experience_years) ||
      experience_years <= 0
    ) {
      return next(appError(400, "欄位未填寫正確"));
    }

    if (
      isValidString(profile_image_url) &&
      !profile_image_url.startsWith("https")
    ) {
      return next(appError(400, "欄位未填寫正確"));
    }

    const userRepo = dataSource.getRepository("User");
    const existingUser = await userRepo.findOneBy({
      id: userId,
    });

    if (!existingUser) {
      return next(appError(400, "使用者不存在"));
    }
    if (existingUser.role === "COACH") {
      return next(appError(409, "使用者已經是教練"));
    }

    const coachRepo = dataSource.getRepository("Coach");
    const newCoach = coachRepo.create({
      user_id: userId,
      experience_years,
      description,
      profile_image_url,
    });

    const updatedUser = await userRepo.update(
      {
        id: userId,
        role: "USER",
      },
      {
        role: "COACH",
      },
    );

    if (updatedUser.affected === 0) {
      return next(appError(400, "更新使用者失敗"));
    }

    const savedCoach = await coachRepo.save(newCoach);
    const savedUser = await userRepo.findOneBy({ id: userId });

    res.status(201).json({
      status: "success",
      data: {
        user: savedUser,
        coach: savedCoach,
      },
    });
  },

  async getCoachProfile(req, res, next) {
    const { id } = req.user;
    const coachRepo = dataSource.getRepository("Coach");
    const coach = await coachRepo.findOne({
      where: { user_id: id },
      relations: {
        coachWithSkill: true,
      },
    });

    res.status(200).json({
      status: "success",
      data: {
        id: coach.id,
        experience_years: coach.experience_years,
        description: coach.description,
        profile_image_url: coach.profile_image_url,
        skill_ids: coach.coachWithSkill.map((skill) => skill.skill_id),
      },
    });
  },

  async putCoachProfile(req, res, next) {
    const { id } = req.user;
    const { experience_years, description, profile_image_url, skill_ids } =
      req.body;

    if (
      !isValidString(description) ||
      !isValidString(profile_image_url) ||
      !profile_image_url.startsWith("https") ||
      !isInteger(experience_years) ||
      experience_years <= 0 ||
      !Array.isArray(skill_ids) ||
      skill_ids.length === 0 ||
      skill_ids.every((skill) => !isValidString(skill))
    ) {
      return next(appError(400, "欄位未填寫正確"));
    }

    const coachRepo = dataSource.getRepository("Coach");
    const coachWithSkillRepo = dataSource.getRepository("CoachWithSkill");

    const coach = await coachRepo.findOne({
      select: { id: true },
      where: { user_id: id },
    });

    const newCoachWithSkill = skill_ids.map((skill) => ({
      coach_id: coach.id,
      skill_id: skill,
    }));

    await coachRepo.update(
      {
        id: coach.id,
      },
      {
        experience_years,
        description,
        profile_image_url,
      },
    );
    await coachWithSkillRepo.delete({ coach_id: coach.id });
    const insert = await coachWithSkillRepo.insert(newCoachWithSkill);

    const result = await coachRepo.findOne({
      where: { id: coach.id },
      relations: {
        coachWithSkill: true, // ← 簡單載入整個關係
      },
    });

    res.status(200).json({
      status: "success",
      data: {
        id: result.id,
        experience_years: result.experience_years,
        description: result.description,
        profile_image_url: result.profile_image_url,
        skill_ids: result.coachWithSkill.map((skill) => skill.skill_id),
      },
    });
  },

  async getCoachAllCourse(req, res, next) {
    res.status(201).json({
      status: "success",
      data: {},
    });
  },

  async getCoachCourseDetail(req, res, next) {
    const { id } = req.user;
    const { courseId } = req.params;
    if (!isValidString(courseId)) {
      return next(appError(400, "欄位未填寫正確"));
    }
    const course = await dataSource.getRepository("Course").findOne({
      select: {
        id: true,
        name: true,
        description: true,
        start_at: true,
        end_at: true,
        max_participants: true,
        meeting_url: true,
        skill: {
          id: true,
          name: true,
        },
      },
      where: {
        id: courseId,
        user_id: id,
      },
      relations: {
        skill: true,
        user: true,
      },
    });
    if (!course) {
      return next(appError(400, "課程不存在"));
    }
    res.status(200).json({
      status: "success",
      data: {
        id: course.id,
        name: course.name,
        description: course.description,
        start_at: course.start_at,
        end_at: course.end_at,
        max_participants: course.max_participants,
        skill_name: course.skill.name,
        skill_id: course.skill.id,
        meeting_url: course.meeting_url,
      },
    });
  },

  async postCoachCourse(req, res, next) {
    const { id } = req.user;
    const {
      skill_id,
      name,
      description,
      start_at,
      end_at,
      max_participants,
      meeting_url,
    } = req.body;
    if (
      !isValidString(skill_id) ||
      !isValidString(name) ||
      !isValidString(description) ||
      !isValidString(start_at) ||
      !isValidString(end_at) ||
      !isInteger(max_participants) ||
      !isValidString(meeting_url) ||
      !meeting_url.startsWith("https")
    ) {
      return next(appError(400, "欄位未填寫正確"));
    }

    const courseRepo = dataSource.getRepository("Course");
    const newCourse = courseRepo.create({
      user_id: id,
      skill_id,
      name,
      description,
      start_at,
      end_at,
      max_participants,
      meeting_url,
    });
    const savedCourse = await courseRepo.save(newCourse);
    const course = await courseRepo.findOne({
      where: { id: savedCourse.id },
    });

    res.status(201).json({
      status: "success",
      data: {
        course,
      },
    });
  },

  async putCoachCourse(req, res, next) {
    const { id } = req.user;
    const { courseId } = req.params;
    const {
      skill_id,
      name,
      description,
      start_at,
      end_at,
      max_participants,
      meeting_url,
    } = req.body;
    if (
      !isValidString(skill_id) ||
      !isValidString(name) ||
      !isValidString(description) ||
      !isValidString(start_at) ||
      !isValidString(end_at) ||
      !isInteger(max_participants) ||
      !isValidString(meeting_url) ||
      !meeting_url.startsWith("https")
    ) {
      return next(appError(400, "欄位未填寫正確"));
    }

    const courseRepo = dataSource.getRepository("Course");
    const existingCourse = await courseRepo.findOne({
      where: { id: courseId, user_id: id },
    });
    if (!existingCourse) {
      return next(appError(400, "課程不存在"));
    }

    const updateCourse = await courseRepo.update(
      {
        id: courseId,
      },
      {
        skill_id,
        name,
        description,
        start_at,
        end_at,
        max_participants,
        meeting_url,
      },
    );
    if (updateCourse.affected === 0) {
      return next(appError(400, "更新課程失敗"));
    }
    const savedCourse = await courseRepo.findOne({
      where: { id: courseId },
    });
    res.status(200).json({
      status: "success",
      data: {
        course: savedCourse,
      },
    });
  },

  async getCoachCourses(req, res, next) {
    const { id } = req.user;
    const courses = await dataSource.getRepository("Course").find({
      select: {
        id: true,
        name: true,
        start_at: true,
        end_at: true,
        max_participants: true,
        meeting_url: true,
      },
      where: {
        user_id: id,
      },
    });
    if (courses.length === 0) {
      res.status(200).json({
        status: "success",
        data: [],
      });
      return;
    }
    const courseIds = courses.map((course) => course.id);
    const coursesParticipant = await dataSource
      .getRepository("CourseBooking")
      .createQueryBuilder("course_booking")
      .select("course_id")
      .addSelect("COUNT(course_id)", "count")
      .where("course_id IN (:...courseIds)", { courseIds })
      .andWhere("cancelled_at is null")
      .groupBy("course_id")
      .getRawMany();
    const now = new Date();
    res.status(200).json({
      status: "success",
      data: courses.map((course) => {
        const startAt = new Date(course.start_at);
        const endAt = new Date(course.end_at);
        let status = "尚未開始";
        if (startAt < now) {
          status = "進行中";
          if (endAt < now) {
            status = "已結束";
          }
        }
        const courseParticipant = coursesParticipant.find(
          (courseParticipant) => courseParticipant.course_id === course.id,
        );
        return {
          id: course.id,
          name: course.name,
          status,
          start_at: course.start_at,
          end_at: course.end_at,
          max_participants: course.max_participants,
          meeting_url: course.meeting_url,
          participants: courseParticipant ? courseParticipant.count : 0,
        };
      }),
    });
  },

  async getCoachRevenue(req, res, next) {
    const { id } = req.user;
    const { month } = req.query;
    const monthNumber = monthMap[month];
    const year = new Date().getFullYear();

    if (!isValidString(month) || monthNumber == undefined) {
      return next(appError(400, "欄位未填寫正確"));
    }

    const courses = await dataSource.getRepository("Course").find({
      select: { id: true },
      where: { user_id: id },
    });

    const courseIds = courses.map((course) => course.id);
    if (courseIds.length === 0) {
      res.status(200).json({
        status: "success",
        data: {
          total: {
            revenue: 0,
            participants: 0,
            course_count: 0,
          },
        },
      });
      return;
    }

    //cal booking person
    const calbookings = await dataSource.query(
      `SELECT COUNT(DISTINCT(cb.user_id)) FROM course_booking cb
       JOIN course c ON c.id = cb.course_id
       WHERE c.user_id = $1 AND cb.cancelled_at IS NULL
       AND EXTRACT(YEAR FROM cb.created_at) = $2
       AND EXTRACT(MONTH FROM cb.created_at) = $3`,
      [id, year, monthNumber],
    );

    console.info("calbookings", calbookings, calbookings.length);

    //cal booking number
    const calCourses = await dataSource.query(
      `SELECT cb.id FROM course_booking cb
       JOIN course c ON c.id = cb.course_id
       WHERE c.user_id = $1 AND cb.cancelled_at IS NULL
       AND EXTRACT(YEAR FROM cb.created_at) = $2
       AND EXTRACT(MONTH FROM cb.created_at) = $3`,
      [id, year, monthNumber],
    );
    console.info("calCourses", calCourses, calCourses.length);

    const totalCreditPackage = await dataSource.query(
      `SELECT 
      SUM(credit_amount) AS total_credit_amount,
      SUM(price) AS total_price FROM "credit_packages"`,
    );

    console.info("totalCreditPackage", totalCreditPackage);

    const perCreditPrice =
      Number(totalCreditPackage[0].total_price) /
      Number(totalCreditPackage[0].total_credit_amount);
    const totalRevenue = calCourses.length * perCreditPrice;

    console.info("perCreditPrice", perCreditPrice);
    console.info("totalRevenue", totalRevenue);
    res.status(200).json({
      status: "success",
      data: {
        total: {
          revenue: Math.floor(totalRevenue),
          participants: parseInt(calbookings[0].count, 10),
          course_count: parseInt(calCourses.length, 10),
        },
      },
    });
  },
};

module.exports = adminController;
