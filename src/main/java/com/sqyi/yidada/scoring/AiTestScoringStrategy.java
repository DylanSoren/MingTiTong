package com.sqyi.yidada.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sqyi.yidada.manager.AiManager;
import com.sqyi.yidada.model.dto.question.QuestionAnswerDTO;
import com.sqyi.yidada.model.dto.question.QuestionContentDTO;
import com.sqyi.yidada.model.entity.App;
import com.sqyi.yidada.model.entity.Question;
import com.sqyi.yidada.model.entity.UserAnswer;
import com.sqyi.yidada.model.vo.QuestionVO;
import com.sqyi.yidada.service.QuestionService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI生成测评类策略
 */
@ScoringStrategySetting(appType = 1, scoringStrategy = 1)
public class AiTestScoringStrategy implements ScoringStrategy {
    @Resource
    private QuestionService questionService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedissonClient redissonClient;

    private final Cache<String, String> answerCacheMap =
            Caffeine.newBuilder().initialCapacity(1024)
                    // 缓存5分钟移除
                    .expireAfterAccess(5L, TimeUnit.MINUTES)
                    .build();

    private static final String AI_ANSWER_LOCK = "AI_ANSWER_LOCK";

    /**
     * AI 评分系统消息
     */
    private static final String AI_TEST_SCORING_SYSTEM_MESSAGE = """
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
            3. 返回格式必须为 JSON 对象""";

    /**
     * 生成本地缓存的key
     * @param appId
     * @param choicesStr
     * @return
     */
    private String buildCacheKey(Long appId, String choicesStr) {
        return DigestUtil.md5Hex(appId + ":" + choicesStr);
    }

    /**
     * AI 评分用户消息封装
     *
     * @param app
     * @param questionContentDTOList
     * @param choices
     * @return
     */
    private String getAiTestScoringUserMessage(App app, List<QuestionContentDTO> questionContentDTOList,
                                               List<String> choices) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        List<QuestionAnswerDTO> questionAnswerDTOList = new ArrayList<>();
        for (int i = 0; i < questionContentDTOList.size(); i++) {
            QuestionAnswerDTO questionAnswerDTO = new QuestionAnswerDTO();
            questionAnswerDTO.setTitle(questionContentDTOList.get(i).getTitle());
            questionAnswerDTO.setUserAnswer(choices.get(i));
            questionAnswerDTOList.add(questionAnswerDTO);
        }
        userMessage.append(JSONUtil.toJsonStr(questionAnswerDTOList));
        return userMessage.toString();
    }

    /**
     * 根据用户选择的答案进行评分
     *
     * @param choices
     * @param app
     * @return
     */
    @Override
    public UserAnswer doScore(List<String> choices, App app) {
        Long appId = app.getId();
        String choicesStr = JSONUtil.toJsonStr(choices);
        String cacheKey = buildCacheKey(appId, choicesStr);

        // 1.从本地缓存中查找
        String answerJson = answerCacheMap.getIfPresent(cacheKey);
        // 命中缓存则直接返回结果
        if (StrUtil.isNotBlank(answerJson)) {
            UserAnswer userAnswer = JSONUtil.toBean(answerJson, UserAnswer.class);
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(choicesStr);
            return userAnswer;
        }

        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + cacheKey);
        try {
            boolean isGet = lock.tryLock();
            if (isGet) return null;
            // 2. 根据 appId 查询到app对应的题目
            // 查询app对应的题目
            Question question = questionService.getOne(
                    new QueryWrapper<Question>().eq("appId", appId)
            );
            // 获取类结构（非json结构）的questionContent
            QuestionVO questionVO = QuestionVO.objToVo(question);
            List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();

            // 3. 调用 AI 获取结果
            String userMessage = getAiTestScoringUserMessage(app, questionContentDTOList, choices);
            String result = aiManager.doStableRequest(AI_TEST_SCORING_SYSTEM_MESSAGE, userMessage);

            // 4. 缓存 AI 结果
            answerCacheMap.put(cacheKey, result);

            // 5. 构造返回值，填充答案对象的属性
            UserAnswer userAnswer = JSONUtil.toBean(result, UserAnswer.class);
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(choicesStr);
            // UserId在addUserAnswer中设置
            // userAnswer.setUserId();
            return userAnswer;
        } finally {
            lock.unlock();
        }
    }
}
