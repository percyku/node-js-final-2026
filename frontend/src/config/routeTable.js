export const ROUTE_TABLE = {
  "post-users": ["/signup", "/login", "/google"],
  "get-courses": true,
  "get-credit-package": true,
  "post-credit-package": true,
  "delete-credit-package": [/^\/\d+$/], // credit-package/:id
  "get-coaches": [
    "/skill",
    /^\/\d+$/, // /:coachId
    /^\/\?.*$/, // /?per=&page=
    /^\/\d+\/courses$/, // /:coachId/courses
  ],
  "post-coaches": ["/skill"],
  "delete-coaches": [
    /^\/skill\/\d+$/, // /skill/:skillId
  ],
  "post-admin": [
    /^\/coaches\/\d+$/, //  coaches/:userId
  ],
};
