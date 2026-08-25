const { EntitySchema } = require("typeorm");

module.exports = new EntitySchema({
  name: "CreditPurchase",
  tableName: "credit_purchase",
  columns: {
    id: {
      primary: true,
      type: "uuid",
      generated: "uuid",
      nullable: false,
    },
    user_id: {
      type: "uuid",
      nullable: false,
    },
    credit_package_id: {
      type: "uuid",
      nullable: false,
    },
    purchased_credits: {
      type: "integer",
      nullable: false,
    },
    price_paid: {
      type: "numeric",
      precision: 10,
      scale: 2,
      nullable: false,
    },
    created_at: {
      type: "timestamp",
      createDate: true,
      name: "created_at",
      nullable: false,
    },
    purchase_at: {
      type: "timestamp",
      name: "purchase_at",
      nullable: false,
    },
  },
  relations: {
    user: {
      target: "User",
      type: "many-to-one",
      joinColumn: {
        name: "user_id",
        referencedColumnName: "id",
      },
    },
    creditPackage: {
      target: "CreditPackage",
      type: "many-to-one",
      joinColumn: {
        name: "credit_package_id",
        referencedColumnName: "id",
      },
    },
  },
});
