const { IsNull, MoreThan, LessThanOrEqual } = require("typeorm");
const { dataSource } = require("../db/data-source");
const appError = require("../utils/appError");
const {
  isValidString,
  isValidPassword,
  isInteger,
} = require("../utils/validUtils");

const coachesController = {
  async getCoachDetail(req, res, next) {
    const { coachId } = req.params;
    if (!isValidString(coachId)) {
      return next(appError(400, "欄位未填寫正確"));
    }

    const coach = await dataSource.getRepository("Coach").findOne({
      select: {
        id: true,
        user_id: true,
        experience_years: true,
        description: true,
        profile_image_url: true,
        created_at: true,
        updated_at: true,
        user: {
          name: true,
          role: true,
        },
      },
      where: {
        id: coachId,
      },
      relations: {
        user: true,
      },
    });

    if (!coach) {
      return next(appError(400, "找不到該教練"));
    }
    const coachSkill = await dataSource.getRepository("CoachWithSkill").find({
      select: {
        skill: {
          name: true,
        },
      },
      where: {
        coach_id: coach.id,
      },
      relations: {
        skill: true,
      },
    });

    const skills = coachSkill.map((coachSkill) => coachSkill.skill.name);

    res.status(200).json({
      status: "success",
      data: {
        user: coach.user,
        coach: {
          id: coach.id,
          user_id: coach.user_id,
          experience_years: coach.experience_years,
          description: coach.description,
          profile_image_url: coach.profile_image_url,
          created_at: coach.created_at,
          updated_at: coach.updated_at,
          skills,
        },
      },
    });
  },

  async getCoachCourses(req, res, next) {
    const { coachId } = req.params;
    if (!isValidString(coachId)) {
      return next(appError(400, "欄位未填寫正確"));
    }
    const coach = await dataSource.getRepository("Coach").findOne({
      select: {
        id: true,
        user_id: true,
        user: {
          name: true,
        },
      },
      where: {
        id: coachId,
      },
      relations: {
        user: true,
      },
    });
    if (!coach) {
      return next(appError(400, "找不到該教練"));
    }
    const courses = await dataSource.getRepository("Course").find({
      select: {
        id: true,
        name: true,
        description: true,
        start_at: true,
        end_at: true,
        max_participants: true,
        skill: {
          name: true,
        },
      },
      where: {
        user_id: coach.user_id,
        end_at: MoreThan(new Date()),
      },
      relations: {
        skill: true,
      },
    });
    res.status(200).json({
      status: "success",
      data: courses.map((course) => ({
        id: course.id,
        name: course.name,
        description: course.description,
        start_at: course.start_at,
        end_at: course.end_at,
        max_participants: course.max_participants,
        coach_name: coach.user.name,
        skill_name: course.skill.name,
      })),
    });
  },

  async getCoaches(req, res, next) {
    const { per, page } = req.query;
    if (!isValidString(per) || !isValidString(page)) {
      return next(appError(400, "欄位未填寫正確"));
    }
    const perInt = parseInt(per, 10);
    const pageInt = parseInt(page, 10);
    if (!isInteger(perInt) || !isInteger(pageInt)) {
      return next(appError(400, "欄位未填寫正確"));
    }
    const coaches = await dataSource.getRepository("Coach").find({
      select: {
        id: true,
        user_id: true,
        user: {
          name: true,
        },
      },
      take: perInt,
      skip: (pageInt - 1) * perInt,
      relations: {
        user: true,
      },
    });
    res.status(200).json({
      status: "success",
      data: coaches.map((coach) => ({
        id: coach.id,
        user_id: coach.user_id,
        name: coach.user.name,
      })),
    });
  },
};

module.exports = coachesController;
