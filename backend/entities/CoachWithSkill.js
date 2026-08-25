const { EntitySchema } = require("typeorm");

module.exports = new EntitySchema({
  name: "CoachWithSkill",
  tableName: "coach_with_skills",
  columns: {
    id: { type: "uuid", primary: true, generated: "uuid" },
    coach_id: { type: "uuid", nullable: false },
    skill_id: { type: "uuid", nullable: false },
    created_at: { type: "timestamp", createDate: true },
  },
  relations: {
    coach: {
      type: "many-to-one",
      target: "Coach",
      joinColumn: {
        name: "coach_id",
        referencedColumnName: "id",
      },
    },
    skill: {
      type: "many-to-one",
      target: "Skill",
      joinColumn: {
        name: "skill_id",
        referencedColumnName: "id",
      },
      onDelete: "CASCADE",
    },
  },
});
