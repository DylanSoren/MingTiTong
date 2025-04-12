package com.sqyi.yidada.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sqyi.yidada.manager.AiManager;
import com.sqyi.yidada.model.dto.question.QuestionAnswerDTO;
import com.sqyi.yidada.model.dto.question.QuestionContentDTO;
import com.sqyi.yidada.model.entity.App;
import com.sqyi.yidada.model.entity.Question;
import com.sqyi.yidada.model.entity.UserAnswer;
import com.sqyi.yidada.model.vo.QuestionVO;
import com.sqyi.yidada.service.QuestionService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * AI生成测评类策略
 */
@ScoringStrategySetting(appType = 1, scoringStrategy = 1)
public class AiTestScoringStrategy implements ScoringStrategy {
    @Resource
    private QuestionService questionService;

    @Resource
    private AiManager aiManager;

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

    @Override
    public UserAnswer doScore(List<String> choices, App app) {
        Long appId = app.getId();
        // 1. 根据 appId 查询到app对应的题目
        // 查询app对应的题目
        Question question = questionService.getOne(
                new QueryWrapper<Question>().eq("appId", appId)
        );
        // 获取类结构（非json结构）的questionContent
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();

        // 2. 调用 AI 获取结果
        String userMessage = getAiTestScoringUserMessage(app, questionContentDTOList, choices);
        String result = aiManager.doStableRequest(userMessage, AI_TEST_SCORING_SYSTEM_MESSAGE);

        // 3. 构造返回值，填充答案对象的属性
        UserAnswer userAnswer = JSONUtil.toBean(result, UserAnswer.class);
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        // UserId在addUserAnswer中设置
        // userAnswer.setUserId();
        return userAnswer;
    }
}
