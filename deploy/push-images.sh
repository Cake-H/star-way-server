#!/usr/bin/env bash
# ============================================================
# 构建并推送镜像到阿里云个人版容器镜像服务（ACR）
#
# 用法（在 Git Bash 中执行，项目根目录下）：
#   bash deploy/push-images.sh                  # 构建+推送全部 3 个服务，tag=latest
#   bash deploy/push-images.sh ida-app-server   # 只推 app-server
#   bash deploy/push-images.sh all v1.0.1       # 推全部，tag=v1.0.1
#
# 前置条件：
#   1. 已安装并启动 Docker Desktop
#   2. 已登录镜像仓库：
#      docker login --username=<阿里云账号> ccr.ccs.tencentyun.com
#      （密码为 ACR 控制台「访问凭证」里设置的固定密码）
# ============================================================
set -e

REGISTRY="ccr.ccs.tencentyun.com"
NAMESPACE="cakeh"

MODULE="${1:-all}"
TAG="${2:-latest}"

# 校验模块名
case "$MODULE" in
  ida-app-server|ida-plugin-server|ida-admin-server|all) ;;
  *) echo "用法: bash deploy/push-images.sh [ida-app-server|ida-plugin-server|ida-admin-server|all] [tag]"; exit 1 ;;
esac

push_one() {
  local m="$1"
  echo "==================== 构建 $m:$TAG ===================="
  docker build --build-arg MODULE="$m" -t "$REGISTRY/$NAMESPACE/$m:$TAG" .
  echo "==================== 推送 $m:$TAG ===================="
  docker push "$REGISTRY/$NAMESPACE/$m:$TAG"
}

if [ "$MODULE" = "all" ]; then
  push_one ida-app-server
  push_one ida-plugin-server
  push_one ida-admin-server
else
  push_one "$MODULE"
fi

echo "==================== 完成 ===================="
echo "镜像地址：$REGISTRY/$NAMESPACE/<模块名>:$TAG"
echo "服务器上更新：docker compose pull && docker compose up -d"
