package com.sqyi.yidada.scoring;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sqyi.yidada.common.ErrorCode;
import com.sqyi.yidada.exception.ThrowUtils;
import com.sqyi.yidada.model.dto.question.QuestionContentDTO;
import com.sqyi.yidada.model.entity.App;
import com.sqyi.yidada.model.entity.Question;
import com.sqyi.yidada.model.entity.ScoringResult;
import com.sqyi.yidada.model.entity.UserAnswer;
import com.sqyi.yidada.model.vo.QuestionVO;
import com.sqyi.yidada.service.QuestionService;
import com.sqyi.yidada.service.ScoringResultService;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义测评类策略
 */
@ScoringStrategySetting(appType = 1, scoringStrategy = 0)
public class CustomTestScoringStrategy implements ScoringStrategy {
    @Resource
    private QuestionService questionService;

    @Resource
    private ScoringResultService scoringResultService;

    @Override
    public UserAnswer doScore(List<String> choices, App app) {
        Long appId = app.getId();
        // 1. 根据 appId 查询到app对应的 题目 和 可能返回的测评结果信息
        // 查询app对应的题目
        Question question = questionService.getOne(
                new QueryWrapper<Question>().eq("appId", appId)
        );
        // 查询app对应的可能返回的测评结果信息
        List<ScoringResult> scoringResultList = scoringResultService.list(
                new QueryWrapper<ScoringResult>().eq("appId", appId)
        );

        // 2. 统计用户每个选择对应的属性个数，如 I = 10 个，S = 5 个
        // 初始化一个Map，用于存储每个选项的计数
        Map<String, Integer> optionCount = new HashMap<>();

        // 获取类结构（非json结构）的questionContent
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();
        // 校验数量
        ThrowUtils.throwIf(questionContentDTOList.size() != choices.size(),
                ErrorCode.PARAMS_ERROR, "题目和用户答案数量不一致");

        // 遍历每道题
        for (QuestionContentDTO questionContentDTO :questionContentDTOList) {
            // 遍历用户的每个答案
            for (String choice : choices) {
                // 遍历题目的每个选项
                for (QuestionContentDTO.Option option : questionContentDTO.getOptions()) {
                    // 如果用户的答案和题目的选项匹配
                    if (choice.equals(option.getKey())) {
                        // 获取选项的result属性
                        String result = option.getResult();

                        // 如果result属性不在optionCount中，初始化为0
                        if (!optionCount.containsKey(result)) {
                            optionCount.put(result, 0);
                        }

                        // 在optionCount中增加计数
                        optionCount.put(result, optionCount.get(result) + 1);
                    }
                }
            }
        }

        // 3. 遍历每种评分结果，计算哪个结果的得分更高
        // 初始化最高分数
        int maxScore = 0;
        // 初始化最高分数测评结果信息
        ScoringResult maxScoringResult = scoringResultList.get(0);

        for (ScoringResult scoringResult : scoringResultList) {
            // 获取当前遍历到的 测评结果信息 中的 结果属性集合 （例如[I,N,T,J]）
            List<String> resultPropList = JSONUtil.toList(scoringResult.getResultProp(), String.class);
            // 计算结果属性集合对应的得分（例如[I,N,T,J]对应的得分为[1,3,4,5],score为1+3+4+5=13）
            int score = resultPropList.stream()
                    .mapToInt(rp -> optionCount.getOrDefault(rp, 0))
                    .sum();

            if (score > maxScore) {
                maxScore = score;
                maxScoringResult = scoringResult;
            }
        }

        // 4. 构造返回值，填充答案对象的属性
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAppId(appId);
        userAnswer.setAppType(app.getAppType());
        userAnswer.setScoringStrategy(app.getScoringStrategy());
        userAnswer.setChoices(JSONUtil.toJsonStr(choices));
        userAnswer.setResultId(maxScoringResult.getId());
        userAnswer.setResultName(maxScoringResult.getResultName());
        userAnswer.setResultDesc(maxScoringResult.getResultDesc());
        userAnswer.setResultPicture(maxScoringResult.getResultPicture());
        // UserId在addUserAnswer中设置
        // userAnswer.setUserId();
        return userAnswer;
    }
}
