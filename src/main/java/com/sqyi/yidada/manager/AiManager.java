package com.sqyi.yidada.manager;

import com.volcengine.ark.runtime.model.completion.chat.*;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author sqyi
 */
@Component
@ConfigurationProperties(prefix = "ai.chat-completion-request-config")
@Data
@Slf4j
public class AiManager {
    private String reasoningAccessPointId;
    private Integer defaultMaxTokens;
    private Double unstableTemperature;
    private Double stableTemperature;

    @Resource
    private ArkService arkService;

    /**
     * 不稳定答案的请求（简化消息传递）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @param maxTokens
     * @return
     */
    public String doUnstableRequest(String systemMessageContent, String userMessageContent, Integer maxTokens) {
        return doRequest(systemMessageContent, userMessageContent, unstableTemperature, maxTokens);
    }

    /**
     * 不稳定答案的请求（简化消息传递 + 默认maxTokens）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @return
     */
    public String doUnstableRequest(String systemMessageContent, String userMessageContent) {
        return doRequest(systemMessageContent, userMessageContent, unstableTemperature, defaultMaxTokens);
    }

    /**
     * 稳定答案的请求（简化消息传递）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @param maxTokens
     * @return
     */
    public String doStableRequest(String systemMessageContent, String userMessageContent, Integer maxTokens) {
        return doRequest(systemMessageContent, userMessageContent, stableTemperature, maxTokens);
    }

    /**
     * 稳定答案的请求（简化消息传递 + 默认maxTokens）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @return
     */
    public String doStableRequest(String systemMessageContent, String userMessageContent) {
        return doRequest(systemMessageContent, userMessageContent, stableTemperature, defaultMaxTokens);
    }

    /**
     * 默认maxTokens请求（简化消息传递）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @param temperature
     * @return
     */
    public String doRequest(String systemMessageContent, String userMessageContent,
                            Double temperature) {
        return doRequest(systemMessageContent, userMessageContent, temperature, defaultMaxTokens);
    }

    /**
     * 通用请求（简化消息传递）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @param temperature
     * @param maxTokens
     * @return
     */
    public String doRequest(String systemMessageContent, String userMessageContent,
                            Double temperature, Integer maxTokens) {
        // 创建消息列表（包含系统提示和用户问题）
        final List<ChatMessage> messages = new ArrayList<>();
        // 系统角色消息：设置AI的基本行为
        final ChatMessage systemMessage = ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM)
                .content(systemMessageContent)
                .build();
        // 用户角色消息：提出问题
        final ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER)
                .content(userMessageContent)
                .build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doRequest(messages, temperature, maxTokens);
    }

    /**
     * 通用请求
     *
     * @param messages
     * @param temperature
     * @param maxTokens
     * @return
     */
    public String doRequest(List<ChatMessage> messages, Double temperature, Integer maxTokens) {
        // 构建聊天补全请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(reasoningAccessPointId)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        try {
            // 发送请求并处理响应
            ChatCompletionResult chatCompletionResult = arkService.createChatCompletion(chatCompletionRequest);
            String aiResponse = chatCompletionResult.getChoices().get(0).getMessage().getContent().toString();

            // 关闭客户端线程池释放资源
            arkService.shutdownExecutor();

            return aiResponse;
        } catch (Exception e) {
            log.error("AI请求失败：", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 稳定答案的流式请求（简化消息传递 + 默认maxTokens）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @return
     */
    public Flowable<ChatCompletionChunk> doStreamStableRequest(String systemMessageContent, String userMessageContent) {
        return doStreamRequest(systemMessageContent, userMessageContent, stableTemperature, defaultMaxTokens);
    }

    /**
     * 通用流式请求（简化消息传递）
     *
     * @param systemMessageContent
     * @param userMessageContent
     * @param temperature
     * @param maxTokens
     * @return
     */
    public Flowable<ChatCompletionChunk> doStreamRequest(String systemMessageContent,
                                                         String userMessageContent,
                                                         Double temperature, Integer maxTokens) {
        // 创建消息列表（包含系统提示和用户问题）
        final List<ChatMessage> messages = new ArrayList<>();
        // 系统角色消息：设置AI的基本行为
        final ChatMessage systemMessage = ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM)
                .content(systemMessageContent)
                .build();
        // 用户角色消息：提出问题
        final ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER)
                .content(userMessageContent)
                .build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doStreamRequest(messages, temperature, maxTokens);
    }

    /**
     * 通用流式请求
     *
     * @param messages
     * @param temperature
     * @param maxTokens
     * @return
     */
    public Flowable<ChatCompletionChunk> doStreamRequest(List<ChatMessage> messages, Double temperature, Integer maxTokens) {
        // 构建聊天补全请求
        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                .model(reasoningAccessPointId)
                .stream(Boolean.TRUE)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        try {
            // 发送请求并处理响应
            Flowable<ChatCompletionChunk> chatCompletionChunkFlowable =
                    arkService.streamChatCompletion(streamChatCompletionRequest)
                            // 流结束后自动关闭
                            .doOnComplete(() -> arkService.shutdownExecutor())
                            .doOnError(e -> {
                                arkService.shutdownExecutor();
                                log.error(e.getMessage());
                            });

            return chatCompletionChunkFlowable;
        } catch (Exception e) {
            log.error("AI请求失败：", e);
            throw new RuntimeException(e);
        }
    }
}
