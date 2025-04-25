package com.sqyi.yidada;

import com.volcengine.ark.runtime.model.completion.chat.*;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 请确保您已将 API Key 存储在环境变量 ARK_API_KEY 中
// 初始化Ark客户端，从环境变量中读取您的API Key
@SpringBootTest
public class ChatCompletionsExampleTest {
    // 从环境变量中获取您的 API Key。此为默认方式，您可根据需要进行修改
    static String apiKey = System.getenv("ARK_API_KEY");
    // 此为默认路径，您可根据业务所在地域进行配置
    static String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    // OkHttp 连接池配置（最大空闲连接数5个，保持时间1秒）
    static ConnectionPool connectionPool =
            new ConnectionPool(5, 1, TimeUnit.SECONDS);
    // 网络请求调度器
    static Dispatcher dispatcher = new Dispatcher();
    // 构建 Ark 服务客户端实例
    static ArkService arkService = ArkService.builder()
                                            .dispatcher(dispatcher)
                                            .connectionPool(connectionPool)
                                            .baseUrl(baseUrl)
                                            .apiKey(apiKey)
                                            .build();

    @Test
    void test() {
        System.out.println("\n---------- 标准请求示例 ----------");
        // 创建消息列表（包含系统提示和用户问题）
        final List<ChatMessage> messages = new ArrayList<>();
        // 系统角色消息：设置AI的基本行为
        final ChatMessage systemMessage = ChatMessage.builder()
                                                        .role(ChatMessageRole.SYSTEM)
                                                        .content("你是人工智能助手.")
                                                        .build();
        // 用户角色消息：提出问题
        final ChatMessage userMessage = ChatMessage.builder()
                                                    .role(ChatMessageRole.USER)
                                                    .content("常见的十字花科植物有哪些？")
                                                    .build();
        messages.add(systemMessage);
        messages.add(userMessage);

        // 构建聊天补全请求（需替换为您自己的推理接入点ID）
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
                .model("ep-20250411150222-nkx9r")
                .messages(messages)
                .maxTokens(1024)
                .build();

        // 发送请求并处理响应，逐条打印生成的回复
        ChatCompletionResult chatCompletionResult = arkService.createChatCompletion(chatCompletionRequest);
        List<ChatCompletionChoice> chatCompletionChoiceList = chatCompletionResult.getChoices();
        chatCompletionChoiceList.forEach(choice -> System.out.println(choice.getMessage().getContent()));

        // 关闭客户端线程池释放资源
        arkService.shutdownExecutor();
    }

    @Test
    void test2() {
        System.out.println("\n---------- 流式请求示例 ----------");
        final List<ChatMessage> streamMessages = new ArrayList<>();
        final ChatMessage streamSystemMessage = ChatMessage.builder()
                                                            .role(ChatMessageRole.SYSTEM)
                                                            .content("你是人工智能助手.")
                                                            .build();
        final ChatMessage streamUserMessage = ChatMessage.builder()
                                                            .role(ChatMessageRole.USER)
                                                            .content("常见的十字花科植物有哪些？")
                                                            .build();
        streamMessages.add(streamSystemMessage);
        streamMessages.add(streamUserMessage);

        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
                .model("ep-20250411150222-nkx9r")
                .messages(streamMessages)
                .stream(Boolean.TRUE)
                .build();

//        以下开始和非流式有区别
        Flowable<ChatCompletionChunk> chatCompletionChunkFlowable =
                arkService.streamChatCompletion(streamChatCompletionRequest)
                        // 执行结束自动关闭arkService
                        .doOnComplete(() -> arkService.shutdownExecutor())
                        .doOnError(e -> {
                            arkService.shutdownExecutor();
                            e.printStackTrace();
                        });

        chatCompletionChunkFlowable.blockingForEach(
                choice -> {
                    if (!choice.getChoices().isEmpty()) {
                        System.out.print(choice.getChoices().get(0).getMessage().getContent());
                    }
                }
        );

        // 关闭客户端线程池释放资源

    }
}