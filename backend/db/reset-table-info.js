const { dataSource } = require("./data-source");

async function clearAll() {
  for (const name of [
    "CreditPurchase",
    "CreditPackage",
    "CoachWithSkill",
    "CourseBooking",
    "Course",
    "Skill",
    "Coach",
    "User",
  ]) {
    if (dataSource.hasMetadata(name)) {
      await dataSource.createQueryBuilder().delete().from(name).execute();
      console.log("Clear done", name);
    }
  }
}

async function main() {
  await dataSource.initialize();
  await clearAll();

  console.log("🆑 clear all table done");
  await dataSource.destroy();
}

main().catch((e) => {
  console.error("clear table fail：", e.message);
  process.exit(1);
});
