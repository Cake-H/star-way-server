# 网申站点模块前端对接文档（APP 端）

## 1. 模块概述

网申站点模块为 C 端用户提供「网申网站列表」页面，包含：

- 站点分类列表（用于页面顶部分类 Tab）
- 上架站点列表（按分类筛选，卡片式展示，点击跳转对应网申入口）
- 对支持插件一键填表的站点，前端可引导用户安装浏览器扩展

> 站点目录数据由**管理后台**统一维护（`/api/v1/admin/apply-sites`，含上下架、排序、备注），
> 本模块**只读上架数据**（`status = 1`），无需前端处理下架/排序逻辑。
>
> 两个接口均需要 **Bearer Token** 鉴权。

---

## 2. 基础信息

| 项 | 值 |
|---|---|
| 基础路径 | `http://localhost:8088/api/v1/app` |
| 接口模块 | `/apply-sites` |
| 鉴权方式 | `Authorization: Bearer <token>` |
| Content-Type | `application/json` |
| 响应包装 | 统一返回 `ResponseDTO<T>`，业务数据在 `data` 字段 |
| Swagger 分组 | 「正式客户端-网申网站」 |

### 2.1 通用响应结构

```json
{
  "code": 0,
  "ok": true,
  "success": true,
  "msg": "操作成功",
  "message": "操作成功",
  "data": [ ... ]
}
```

- `code == 0` 且 `ok == true`（`success` 为 `ok` 的兼容别名）表示业务成功。
- 业务异常返回 `code != 0`，`msg` / `message` 中携带中文提示。

---

## 3. 接口清单

### 3.1 站点分类列表（Tab 用）

```http
GET /api/v1/app/apply-sites/categories
Authorization: Bearer <token>
```

**响应 `data`**（按当前种子数据）：

```json
["ATS系统", "综合招聘", "实习", "互联网"]
```

**说明**：

- 返回的是上架站点分类的**去重列表**，顺序按站点排序值（`sortOrder`）首次出现的顺序，**保证 Tab 顺序与站点列表顺序一致**。
- 「全部」Tab 后端不返回，由前端在列表头部自行插入一个固定的「全部」选项即可。
- 分类值由后台自由维护（如：互联网/国企/银行/外企/快消/综合招聘/实习/ATS系统），前端**不要硬编码分类枚举**，直接渲染接口返回值。

---

### 3.2 网申站点列表（按分类筛选）

```http
GET /api/v1/app/apply-sites?category=互联网
Authorization: Bearer <token>
```

**Query 参数**：

| 参数 | 必填 | 说明 |
|---|---|---|
| `category` | 否 | 分类筛选，**不传或传空返回全部上架站点**；值需与 3.1 返回的分类完全一致（后端按等值匹配，会自动 trim） |

**响应 `data`**：

```json
[
  {
    "id": "8",
    "name": "腾讯校招",
    "logoUrl": "https://cdn.example.com/logos/tencent.png",
    "url": "https://join.qq.com",
    "category": "互联网",
    "supportAutofill": 0
  },
  {
    "id": "1",
    "name": "北森校招（Beisen）",
    "logoUrl": null,
    "url": "https://www.beisen.com",
    "category": "ATS系统",
    "supportAutofill": 1
  }
]
```

**字段说明**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | 站点主键。**雪花 ID 为 19 位 Long，超出 JS Number 安全整数上限，后端统一以字符串返回**，前端请勿 `Number()` 强转 |
| `name` | string | 站点名称，如「腾讯校招」 |
| `logoUrl` | string \| null | 站点 Logo URL，**可能为 null**，前端需准备占位图 |
| `url` | string | 网申入口 URL，点击卡片时跳转（新窗口/WebView 打开） |
| `category` | string | 分类，与 3.1 返回值同源 |
| `supportAutofill` | number | 是否支持插件一键填表：`1`=支持（可展示「支持一键填表」角标并引导安装插件）、`0`=不支持 |

**说明**：

- 列表已按后台配置的排序值升序返回，前端**直接按返回顺序渲染**，无需本地排序。
- 只返回上架站点，下架站点对 C 端不可见。
- 该接口**无分页**，站点数量为目录级（几十量级），一次全量返回。

---

## 4. 页面建议

### 4.1 网申网站列表页

```
┌──────────────────────────────────┐
│  网申网站                          │
│  [全部] [ATS系统] [综合招聘] [实习] … │  ← GET /apply-sites/categories，头部插入「全部」
│  ┌────────────────────────────┐  │
│  │ [logo] 腾讯校招        →   │  │  ← GET /apply-sites?category=xxx
│  │        互联网              │  │
│  ├────────────────────────────┤  │
│  │ [logo] 北森校招（Beisen）→ │  │
│  │        ATS系统 ✦一键填表   │  │  ← supportAutofill=1 显示角标
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

1. 进入页面**并行**调用 `GET /apply-sites/categories` 和 `GET /apply-sites`（不传 category，即「全部」）。
2. 切换 Tab 时调用 `GET /apply-sites?category=<当前分类>`；切回「全部」时不传 `category`。
3. 点击站点卡片：H5/浏览器环境 `window.open(url)`；APP 内建议打开内置 WebView。
4. `supportAutofill == 1` 的站点：卡片上显示「支持一键填表」标识，点击时可附加引导气泡/弹窗，引导安装浏览器扩展（插件对接见 `plugin-autofill-async-frontend-integration.md`）。
5. `logoUrl` 为 null 或加载失败时，用站点名首字生成占位图标。

---

## 5. 常见业务错误

| 场景 | code | 说明 | 前端建议处理 |
|---|---|---|---|
| 未登录/Token 失效 | 30007 | "未登录或登录已失效，请重新登录" | 跳登录页（全局已有逻辑） |
| 分类不存在 | — | 不报错，返回空数组 `[]` | 展示空状态即可 |

> 本模块为只读目录接口，无参数校验类业务错误码；`category` 传任意值都不会 4xx，最多返回空列表。

---

## 6. UI/UX 建议

- **分类 Tab 横向滚动**：分类数量由后台运营增减，UI 上按可横向滚动的 Tab 条实现，不要写死个数。
- **一键填表引导**：`supportAutofill == 1` 是插件的核心获客入口，建议角标用品牌色突出；未安装插件的用户点击该站点时，可先弹「安装扩展，网申一键填表」引导，再跳转目标站点。
- **空状态**：某分类无站点时展示「该分类下暂无上架站点」。
- **缓存**：站点目录变更频率低（后台人工维护），前端可对分类和列表做短时效缓存（如 5 分钟），降低首屏请求压力；下拉刷新时强制重拉。
- **Logo 加载**：列表图片加懒加载，失败回退占位图。

---

## 7. 联调注意事项

1. **鉴权头格式**：必须是 `Authorization: Bearer <token>`，token 通过短信登录接口获取。
2. **Swagger 调试**：`http://localhost:8088/swagger-ui/index.html`，分组选择「正式客户端-网申网站」。
3. **数据库前置**：联调前需执行 `database/mysql/ida_v7_admin.sql`（建表 `ida_apply_site` 并写入种子数据；种子数据仅当表为空时插入）。
4. **造测试数据**：直接在管理后台「网申站点」模块增删改、上下架、调排序即可，C 端两个接口会实时反映；也可直接改 `ida_apply_site` 表的 `status` / `sort_order` / `category` 字段验证筛选和排序。
5. **category 传参注意 URL 编码**：分类为中文（如 `?category=综合招聘`），axios 会自动 encode，手写 fetch 时需 `encodeURIComponent`。
6. **本模块无长耗时接口**：超时用默认即可。

---

## 8. 附录：完整时序示例

```text
1. 用户进入「网申网站」页
   GET /api/v1/app/apply-sites/categories
   ← data: ["ATS系统", "综合招聘", "实习", "互联网"]
   GET /api/v1/app/apply-sites
   ← data: [ {北森校招…supportAutofill:1}, {Moka…}, {智联招聘…}, … ]   // 全部，按 sortOrder 升序

2. 前端渲染：Tab 条 = [全部, ATS系统, 综合招聘, 实习, 互联网]，默认选中「全部」

3. 用户切换到「互联网」Tab
   GET /api/v1/app/apply-sites?category=%E4%BA%92%E8%81%94%E7%BD%91
   ← data: [ {牛客网…}, {腾讯校招…}, {阿里巴巴校招…}, {字节跳动校招…} ]

4. 用户点击「北森校招（Beisen）」卡片（supportAutofill=1）
   → 前端弹「安装扩展，一键填表」引导 → 用户确认后 window.open("https://www.beisen.com")

5. 后台运营将某站点下架（status=0）
   → 用户下次进入页面/下拉刷新时，该站点从列表消失（前端无需感知）
```
