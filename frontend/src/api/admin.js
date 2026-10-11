import request from "./request.js";
export function getCoach() {
  return request.get(`admin/coaches`);
}

export function putCoach(data) {
  return request.put(`admin/coaches`, data);
}

export function getAdminCoachCourses() {
  return request.get(`admin/coaches/courses`);
}

export function getCoachCourse(id) {
  return request.get(`admin/coaches/courses/${id}`);
}

export function postCourses(data) {
  return request.post(`admin/coaches/courses`, data);
}

export function putCourses(data, id) {
  return request.put(`admin/coaches/courses/${id}`, data);
}

export function getMonthlyRevenue(month) {
  return request.get(`admin/coaches/revenue?month=${month}`);
}

export function postPromoteUserToCoach(id, data) {
  return request.post(`admin/coaches/${id}`, data);
}

// M7 管理員後台：以下兩支需要 ADMIN 角色
export function getAdminUsers({ page, per, role }) {
  const params = { page, per };
  if (role) {
    params.role = role;
  }
  return request.get(`admin/users`, { params });
}

export function postAdminPromoteUser(id, data) {
  return request.post(`admin/users/${id}/coach`, data);
}
