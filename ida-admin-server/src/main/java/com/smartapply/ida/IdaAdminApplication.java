package com.smartapply.ida;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;

/**
 * 管理后台 — 独立 Spring Boot 进程（默认端口 {@code 8090}）。
 * <p>
 * <b>装配边界：</b>全包扫描后按以下规则排除，保证后台进程只加载「后台业务 + 用户/计费基础 Bean」：
 * <ul>
 *   <li>C 端/插件端全部 Controller：后台不对外暴露任何 C 端接口</li>
 *   <li>{@code module.app.auth/resume/interview/mockInterview/plugin/health}：C 端业务服务，后台用不到</li>
 *   <li>{@code module.plugin} 与 {@code ai} 包：插件业务与 LLM/语音能力，后台不调用</li>
 *   <li>{@code config.oss/sms} 与 {@code integration} 包：对象存储/短信集成，后台不依赖</li>
 *   <li>{@code http.filter}：C 端 Bearer/调试头过滤器；后台使用 {@code module.admin} 下自己的鉴权过滤器，
 *       防止 {@code X-User-Id} 调试头在后台伪造身份</li>
 * </ul>
 * 保留：{@code module.app.billing}（计费/兑换码服务）、{@code module.app.user}（用户 Mapper）、
 * {@code config}（WebMvcConfig/SystemEnvironmentConfig）、{@code http.interceptor/resolver}（登录拦截与参数解析）、
 * {@code handler}（全局异常处理）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.smartapply.ida.config.props")
@MapperScan(value = "com.smartapply.ida", annotationClass = Mapper.class)
@ComponentScan(
        basePackages = "com.smartapply.ida",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.app\\..*\\.controller\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.app\\.(auth|resume|interview|mockInterview|plugin|health)\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.module\\.plugin\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.ai\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.config\\.(oss|sms)\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.integration\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.smartapply\\.ida\\.http\\.filter\\..*")
        }
)
public class IdaAdminApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(IdaAdminApplication.class, args);
        printAccessInfo(ctx);
    }

    private static void printAccessInfo(ConfigurableApplicationContext ctx) {
        if (!(ctx instanceof WebServerApplicationContext webCtx)) {
            return;
        }
        Environment env = ctx.getEnvironment();
        String host = env.getProperty("ida.server.access-host", "127.0.0.1").trim();
        int port = webCtx.getWebServer().getPort();
        String api = env.getProperty("ida.server.api-v1-prefix", "/api/v1/admin");
        String base = "http://" + host + ":" + port;

        System.out.println();
        System.out.println(">>> " + env.getProperty("spring.application.name") + " 启动完成");
        System.out.println(">>> API  " + base + api);
        System.out.println(">>> 文档 " + base + "/doc.html");
        System.out.println();
    }
}
