const isValidString = (value) =>
  typeof value === "string" && value.trim() !== "";
const isInteger = (value) =>
  typeof value === "number" && Number.isInteger(value);
const isValidPassword = (value) =>
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,16}$/.test(value);

const formatLocalDate = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
};

module.exports = { isValidString, isInteger, isValidPassword, formatLocalDate };
