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
        String systemMessageContent = """
               你是一位严谨的判题专家，我会给你如下信息：
               ```
               应用名称，
               【【【应用描述】】】，
               题目和用户回答的列表：格式为 [{"title": "题目","answer": "用户回答"}]
               ```

               请你根据上述信息，按照以下步骤来对用户进行评价：
               1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，大于 200 字）
               2. 严格按照下面的 json 格式输出评价名称和评价描述
               ```
               {"resultName": "评价名称", "resultDesc": "评价描述"}
               ```
               3. 返回格式必须为 JSON 对象
               """;
        String userMessageContent = """
                MBTI 性格测试，
                【【【快来测测你的 MBTI 性格】】】，
                [{"title": "你通常更喜欢","answer": "独自工作"}, {"title": "当安排活动时","answer": "更愿意随机应变"}]
                """;
//        String systemMessageContent = "你是人工智能助手.";
//        String userMessageContent = "常见的十字花科植物有哪些？";
        System.out.println("-------------------result------------------------");
        System.out.println(aiManager.doStableRequest(systemMessageContent, userMessageContent));
    }
}
