import request from "./request.js";
export function postLogin(data) {
  return request.post("users/login", data);
}

export function postGoogleLogin(data) {
  return request.post("users/google", data);
}

export function postGithubLogin(data) {
  return request.post("users/github", data);
}

export function postSignup(data) {
  return request.post("users/signup", data);
}

export function getUserCourses() {
  return request.get("users/courses");
}

export function getUserCreditPackage() {
  return request.get("users/credit-package");
}

export function getUserProfile() {
  return request.get("users/profile");
}

export function putUserProfile(data) {
  return request.put("users/profile", data);
}

export function putUserPassword(data) {
  return request.put("users/password", data);
}
