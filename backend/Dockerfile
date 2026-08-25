FROM node:24-alpine3.22

WORKDIR /app
COPY package*.json ./

# 安裝套件（嚴格依照依照 lock 檔案安裝）
# --omit=dev，代表只安裝 dependencies，不裝 devDependencies
RUN npm ci --omit=dev
COPY . .

EXPOSE 8080
CMD ["node", "app.js"]