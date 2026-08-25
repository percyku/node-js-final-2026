const { EntitySchema } = require("typeorm");
module.exports = new EntitySchema({
  name: "CreditPackage",
  tableName: "credit_packages",
  columns: {
    id: { type: "uuid", primary: true, generated: "uuid" },
    name: { type: "varchar", nullable: false, unique: true },
    credit_amount: { type: "integer", nullable: false, default: 0 },
    price: { type: "integer", nullable: false, default: 0 },
    created_at: { type: "timestamp", createDate: true },
  },
});
