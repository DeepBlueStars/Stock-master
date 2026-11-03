package com.xian.stockai.config;

import com.xian.stockai.mcp.StockAiMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 StockAI 的 MCP 工具注册到 MCP Server。
 */
@Configuration
public class McpToolConfiguration {

    @Bean
    public ToolCallbackProvider stockAiToolCallbackProvider(StockAiMcpTools stockAiMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(stockAiMcpTools)
                .build();
    }
}
