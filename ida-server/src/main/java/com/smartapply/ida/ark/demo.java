package com.smartapply.ida.ark;

import com.volcengine.ark.runtime.model.responses.constant.ResponsesConstants;
import com.volcengine.ark.runtime.model.responses.content.InputContentItemImage;
import com.volcengine.ark.runtime.model.responses.content.InputContentItemText;
import com.volcengine.ark.runtime.model.responses.item.ItemEasyMessage;
import com.volcengine.ark.runtime.model.responses.item.MessageContent;
import com.volcengine.ark.runtime.model.responses.request.CreateResponsesRequest;
import com.volcengine.ark.runtime.model.responses.request.ResponsesInput;
import com.volcengine.ark.runtime.model.responses.response.ResponseObject;
import com.volcengine.ark.runtime.service.ArkService;

/**
 * 火山方舟（豆包等）Responses API 本地试跑。
 * <p>
 * <b>凭证：</b>{@code System.getenv("变量名")} 的第一个参数必须是<b>环境变量的名字</b>，不能把 API Key 写在括号里当名字。
 * 在系统或运行配置里设置：
 * <pre>
 * ARK_API_KEY=你在火山引擎控制台「API Key 管理」里创建的密钥
 * </pre>
 * 可选：若使用 AK/SK 鉴权，请查阅 SDK 文档使用 {@code ArkService.builder().ak()...} 等方式。
 */
public class demo {

    private static final String ENV_ARK_API_KEY = "ARK_API_KEY";

    public static void main(String[] args) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("缺少 API Key：未读到 " + ENV_ARK_API_KEY + "。");
            System.err.println("说明：在 PowerShell 里执行 $env:ARK_API_KEY=... 只作用于「当前那个终端」。");
            System.err.println("从 IDEA / Cursor 点「运行」启动的 Java 不会继承该终端的环境变量。");
            System.err.println("任选一种方式：");
            System.err.println("  • 运行配置 → 环境变量 → 添加 ARK_API_KEY=你的密钥");
            System.err.println("  • 运行配置 → 虚拟机选项 → 添加 -DARK_API_KEY=你的密钥");
            System.err.println("  • Windows 设置 → 系统 → 关于 → 高级系统设置 → 环境变量（用户）→ 新建 ARK_API_KEY，然后完全退出并重启 IDE");
            System.err.println("  • 在同一 PowerShell 会话里：先 $env:ARK_API_KEY=... 再在该窗口用 mvn/java 启动程序");
            System.exit(1);
        }

        ArkService arkService = ArkService.builder()
                .apiKey(apiKey)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .build();

        CreateResponsesRequest request = CreateResponsesRequest.builder()
                .model("doubao-seed-2-0-lite-260215")
                .input(ResponsesInput.builder().addListItem(
                        ItemEasyMessage.builder().role(ResponsesConstants.MESSAGE_ROLE_USER).content(
                                MessageContent.builder()
                                        .addListItem(InputContentItemImage.builder()
                                                .imageUrl("https://ark-project.tos-cn-beijing.volces.com/doc_image/ark_demo_img_1.png")
                                                .build())
                                        .addListItem(InputContentItemText.builder().text("你看见了什么？").build())
                                        .build()
                        ).build()
                ).build())
                .build();
        ResponseObject resp = arkService.createResponse(request);
        System.out.println(resp);

        arkService.shutdownExecutor();
    }

    /** 环境变量 ARK_API_KEY，或 JVM 参数 -DARK_API_KEY / -Dark.api.key */
    private static String resolveApiKey() {
        String key = firstNonBlank(
                System.getenv(ENV_ARK_API_KEY),
                System.getProperty(ENV_ARK_API_KEY),
                System.getProperty("ark.api.key"));
        return key == null || key.isBlank() ? null : key.trim();
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        if (c != null && !c.isBlank()) {
            return c;
        }
        return null;
    }
}
