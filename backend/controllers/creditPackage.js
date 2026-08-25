const { dataSource } = require("../db/data-source");
const appError = require("../utils/appError");
const {
  isValidString,
  isValidPassword,
  isInteger,
} = require("../utils/validUtils");

const creditPackageController = {
  async getAll(req, res, next) {
    const creditPackage = await dataSource.getRepository("CreditPackage").find({
      select: { id: true, name: true, credit_amount: true, price: true },
      order: { created_at: "ASC" },
    });
    res.json({ status: "success", data: creditPackage });
  },

  async postCreditPackage(req, res, next) {
    const { name, credit_amount, price } = req.body;
    if (!isValidString(name)) {
      next(appError(400, "欄位未填寫正確"));
      return;
    }

    if (
      !isInteger(credit_amount) ||
      !isInteger(price) ||
      credit_amount <= 0 ||
      price <= 0
    ) {
      next(appError(400, "欄位未填寫正確"));
      return;
    }
    const creditPackageRepo = dataSource.getRepository("CreditPackage");
    const existing = await creditPackageRepo.findOneBy({ name: name.trim() });
    if (existing) {
      next(appError(409, "資料重複"));
      return;
    }
    const creditPackage = await creditPackageRepo.save({
      name: name.trim(),
      credit_amount,
      price,
    });
    res.json({ status: "success", data: creditPackage });
  },

  async deleteCreditPackage(req, res, next) {
    try {
      const { creditPackageId } = req.params;
      const result = await dataSource
        .getRepository("CreditPackage")
        .delete(creditPackageId);
      if (result.affected === 0) {
        next(appError(400, "ID錯誤"));
        return;
      }
      res.json({ status: "success", data: result });
    } catch (error) {
      next(error);
    }
  },

  async userBuyCreditPackage(req, res, next) {
    const { id } = req.user;
    const { creditPackageId } = req.params;
    const creditPackageRepo = dataSource.getRepository("CreditPackage");
    const creditPackage = await creditPackageRepo.findOne({
      where: {
        id: creditPackageId,
      },
    });

    if (!creditPackage) {
      return next(appError(400, "ID錯誤"));
    }

    const creditPurchaseRepo = dataSource.getRepository("CreditPurchase");
    const newPurchase = await creditPurchaseRepo.create({
      user_id: id,
      credit_package_id: creditPackageId,
      purchased_credits: creditPackage.credit_amount,
      price_paid: creditPackage.price,
      purchase_at: new Date().toISOString(),
    });
    await creditPurchaseRepo.save(newPurchase);
    res.status(200).json({
      status: "success",
      data: null,
    });
  },
};

module.exports = creditPackageController;
