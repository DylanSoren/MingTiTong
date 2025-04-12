package com.sqyi.yidada;

import com.sqyi.yidada.manager.AiManager;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author sqyi
 *
 */
@SpringBootTest
public class ChatCompletionsPackagingTest {
    @Resource
    private AiManager aiManager;

    @Test
    void test2() {
        String systemMessageContent = "你是人工智能助手.";
        String userMessageContent = "常见的十字花科植物有哪些？";
        System.out.println(aiManager.doStableRequest(systemMessageContent, userMessageContent));
        System.out.println("\n-------------------------------------\n");
        System.out.println(aiManager.doUnstableRequest(systemMessageContent, userMessageContent));
    }
}
