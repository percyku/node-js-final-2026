const { dataSource } = require("../db/data-source");
const appError = require("../utils/appError");
const { isValidString } = require("../utils/validUtils");

const skillController = {
  async getSkills(req, res, next) {
    const skills = await dataSource.getRepository("Skill").find({
      select: { id: true, name: true },
      order: { created_at: "ASC" },
    });
    res.json({ status: "success", data: skills });
    return;
  },

  async postSkill(req, res, next) {
    const { name } = req.body;
    if (!isValidString(name)) {
      next(appError(400, "欄位未填寫正確"));
      return;
    }
    const skillRepo = dataSource.getRepository("Skill");
    const existing = await skillRepo.findOneBy({ name: name.trim() });
    if (existing) {
      next(appError(409, "資料重複"));
      return;
    }
    const skill = await skillRepo.save({ name: name.trim() });
    res.json({ status: "success", data: skill });
  },

  async deleteSkill(req, res, next) {
    try {
      const { skillId } = req.params;
      const result = await dataSource.getRepository("Skill").delete(skillId);
      if (result.affected === 0) {
        next(appError(400, "ID錯誤"));
        return;
      }
      res.json({ status: "success" });
    } catch (error) {
      next(error);
    }
  },
};
module.exports = skillController;
