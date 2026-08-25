const { EntitySchema } = require("typeorm");

module.exports = new EntitySchema({
  name: "CourseBooking",
  tableName: "course_booking",
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
    course_id: {
      type: "uuid",
      nullable: false,
    },
    booking_at: {
      type: "timestamp",
      createDate: true,
      name: "booking_at",
      nullable: false,
    },
    join_at: {
      type: "timestamp",
      name: "join_at",
      nullable: true,
    },
    leave_at: {
      type: "timestamp",
      name: "leave_at",
      nullable: true,
    },
    cancelled_at: {
      type: "timestamp",
      name: "cancelled_at",
      nullable: true,
    },
    cancellation_reason: {
      type: "varchar",
      length: 300,
      nullable: true,
    },
    created_at: {
      type: "timestamp",
      createDate: true,
      name: "created_at",
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
    course: {
      target: "Course",
      type: "many-to-one",
      joinColumn: {
        name: "course_id",
        referencedColumnName: "id",
      },
    },
  },
});
