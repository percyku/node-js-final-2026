const { IsNull, In } = require("typeorm");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const { dataSource } = require("../db/data-source");
const config = require("../config/index");
const appError = require("../utils/appError");
const { isValidString, isValidPassword } = require("../utils/validUtils");

const PW_ERR = "密碼不符合規則，需要包含英文數字大小寫，最短8個字，最長16個字";

const usersController = {
  async signup(req, res, next) {
    const { name, email, password } = req.body;
    if (
      !isValidString(name) ||
      !isValidString(email) ||
      !isValidString(password)
    ) {
      return next(appError(400, "欄位未填寫正確"));
    }
    if (!isValidPassword(password)) {
      return next(appError(400, PW_ERR));
    }
    const userRepo = dataSource.getRepository("User");
    const existing = await userRepo.findOneBy({
      email: email.trim().toLowerCase(),
    });
    if (existing) {
      return next(appError(409, "Email 已被使用"));
    }
    const hashed = await bcrypt.hash(password, 10);
    const user = await userRepo.save({
      name: name.trim(),
      email: email.trim().toLowerCase(),
      password: hashed,
      role: "USER",
    });
    res.status(201).json({
      status: "success",
      data: { user: { id: user.id, name: user.name } },
    });
  },
  async login(req, res, next) {
    const { email, password } = req.body;
    if (!isValidString(email) || !isValidString(password)) {
      return next(appError(400, "欄位未填寫正確"));
    }
    if (!isValidPassword(password)) {
      return next(appError(400, PW_ERR));
    }
    const userRepo = dataSource.getRepository("User");
    const user = await userRepo.findOneBy({
      email: email.trim().toLowerCase(),
    });
    if (!user) {
      return next(appError(400, "使用者不存在或密碼輸入錯誤"));
    }
    const match = await bcrypt.compare(password, user.password);
    if (!match) {
      return next(appError(400, "使用者不存在或密碼輸入錯誤"));
    }
    const token = jwt.sign(
      { id: user.id, role: user.role },
      config.get("secret.jwtSecret"),
      { expiresIn: config.get("secret.jwtExpiresDay") },
    );
    res.status(201).json({
      status: "success",
      data: { token, user: { name: user.name } },
    });
  },
  async getProfile(req, res, next) {
    // isAuth 已經把 user 掛到 req.user 了
    res.json({
      status: "success",
      data: { user: { name: req.user.name, email: req.user.email } },
    });
  },
  async updateProfileName(req, res, next) {
    //   isAuth 已經把 user 掛到 req.user 了
    const { name } = req.body;
    if (!isValidString(name)) {
      next(appError(400, "欄位未填寫正確"));
      return;
    }
    const result = await dataSource
      .getRepository("User")
      .update({ id: req.user.id }, { name: name.trim() });
    if (result.affected === 0) {
      next(appError(400, "更新失敗"));
      return;
    }
    res.json({
      status: "success",
      data: { user: { name: name.trim() } },
    });
  },
  async updateProfilePassword(req, res, next) {
    //   isAuth 已經把 user 掛到 req.user 了
    const { password, new_password, confirm_new_password } = req.body;
    if (
      !isValidString(password) ||
      !isValidString(new_password) ||
      !isValidString(confirm_new_password)
    ) {
      next(appError(400, "欄位未填寫正確"));
      return;
    }
    if (
      !isValidPassword(password) ||
      !isValidPassword(new_password) ||
      !isValidPassword(confirm_new_password)
    ) {
      return next(appError(400, PW_ERR));
    }

    if (password === new_password) {
      return next(appError(400, "新密碼不能與舊密碼相同"));
    }

    if (new_password !== confirm_new_password) {
      return next(appError(400, "新密碼與驗證新密碼不一致"));
    }

    const match = await bcrypt.compare(password, req.user.password);
    if (!match) {
      return next(appError(400, "密碼輸入錯誤"));
    }

    const hashed = await bcrypt.hash(new_password, 10);

    const result = await dataSource
      .getRepository("User")
      .update({ id: req.user.id }, { password: hashed });
    if (result.affected === 0) {
      next(appError(400, "更新失敗"));
      return;
    }
    res.json({
      status: "success",
      data: null,
    });
  },

  async getUserCreditPurchase(req, res, next) {
    const { id } = req.user;

    const creditPurchase = await dataSource
      .getRepository("CreditPurchase")
      .find({
        select: {
          purchased_credits: true,
          price_paid: true,
          purchase_at: true,
          creditPackage: {
            name: true,
          },
        },
        where: {
          user_id: id,
        },
        relations: {
          creditPackage: true,
        },
        order: {
          purchase_at: "DESC",
        },
      });

    res.json({
      status: "success",
      data: creditPurchase.map((item) => {
        return {
          name: item.creditPackage.name,
          purchased_credits: item.purchased_credits,
          price_paid: parseInt(item.price_paid, 10),
          purchase_at: item.purchaseAt,
        };
      }),
    });
  },

  async getUserBooking(req, res, next) {
    const { id } = req.user;
    const creditPurchaseRepo = dataSource.getRepository("CreditPurchase");
    const courseBookingRepo = dataSource.getRepository("CourseBooking");
    const userCredit = await creditPurchaseRepo.sum("purchased_credits", {
      user_id: id,
    });
    const userUsedCredit = await courseBookingRepo.count({
      where: {
        user_id: id,
        cancelled_at: IsNull(),
      },
    });
    // const courseBookingList = await courseBookingRepo.find({
    //   select: {
    //     course_id: true,
    //     cancelled_at: true,
    //     course: {
    //       name: true,
    //       start_at: true,
    //       end_at: true,
    //       meeting_url: true,
    //       user_id: true,
    //     },
    //   },
    //   where: {
    //     user_id: id,
    //   },
    //   order: {
    //     course: {
    //       start_at: "ASC",
    //     },
    //   },
    //   relations: {
    //     course: true,
    //   },
    // });
    // console.log("=======================");
    // console.log(courseBookingList);
    // const coachUserIdMap = {};
    // if (courseBookingList.length > 0) {
    //   courseBookingList.forEach((courseBooking) => {
    //     coachUserIdMap[courseBooking.course.user_id] =
    //       courseBooking.course.user_id;
    //   });

    //   console.log(coachUserIdMap);
    //   const userRepo = dataSource.getRepository("User");
    //   const coachUsers = await userRepo.find({
    //     select: { id: true, name: true },
    //     where: {
    //       id: In(Object.values(coachUserIdMap)),
    //     },
    //   });
    //   coachUsers.forEach((user) => {
    //     coachUserIdMap[user.id] = user.name;
    //   });
    // }

    const courseBookingList = await dataSource.query(
      `
        SELECT cb.course_id,c.name,c.start_at,c.end_at,c.meeting_url,u.name AS user_name,cb.cancelled_at FROM course_booking cb
        JOIN course c ON c.id = cb.course_id
        JOIN users u ON u.id = c.user_id
        WHERE 1=1
        AND cb.user_id = $1  
      `,
      [id],
    );

    // console.log(courseBookingList);
    res.status(200).json({
      status: "success",
      data: {
        credit_remain: userCredit - userUsedCredit,
        credit_usage: userUsedCredit,
        course_booking: courseBookingList.map((courseBooking) => {
          return {
            // course_id: courseBooking.course_id,
            // name: courseBooking.course.name,
            // start_at: courseBooking.course.start_at,
            // end_at: courseBooking.course.end_at,
            // meeting_url: courseBooking.course.meeting_url,
            // coach_name: coachUserIdMap[courseBooking.course.user_id],
            // cancelled_at: courseBooking.cancelled_at,
            course_id: courseBooking.course_id,
            name: courseBooking.name,
            start_at: courseBooking.start_at,
            end_at: courseBooking.end_at,
            meeting_url: courseBooking.meeting_url,
            coach_name: courseBooking.user_name,
            cancelled_at: courseBooking.cancelled_at,
          };
        }),
      },
    });
  },
};

module.exports = usersController;
