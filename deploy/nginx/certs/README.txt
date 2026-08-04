把 TLS 证书放到本目录：

  server.crt  — 证书（含中间证书的全链）
  server.key  — 私钥（权限 600，勿提交 git）

获取方式：
  - Let's Encrypt: certbot certonly --standalone -d your-domain.com
    然后软链/复制 fullchain.pem → server.crt，privkey.pem → server.key
  - 自签（仅限本地测试）:
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
      -keyout server.key -out server.crt -subj "/CN=your-domain.com"
