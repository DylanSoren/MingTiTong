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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author sqyi
 *
 */
@ScoringStrategySetting(appType = 0, scoringStrategy = 0)
public class CustomScoreScoringStrategy implements ScoringStrategy{
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

        // 2. 统计用户总得分
        int totalScore = 0;

        // 获取类结构（非json结构）的questionContent
        QuestionVO questionVO = QuestionVO.objToVo(question);
        List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();
        // 校验数量
        ThrowUtils.throwIf(questionContentDTOList.size() != choices.size(),
                ErrorCode.PARAMS_ERROR, "题目和用户答案数量不一致");
        for (int i = 0; i < questionContentDTOList.size(); i++) {
            // 计算一道题中每个答案对应的分值（例如 {"A":3, "B":2, "C":1, "D":0}）
            Map<String, Integer> resultMap = questionContentDTOList.get(i).getOptions().stream()
                    .collect(Collectors.toMap(QuestionContentDTO.Option::getKey, QuestionContentDTO.Option::getScore));
            // 等价于
            // Map<String, Integer> resultMap = questionContentDTOList.get(i).getOptions().stream()
            //        .collect(Collectors.toMap(option -> option.getKey(), option -> option.getScore()));

            // 计算用户该题的得分
            Integer score = Optional.ofNullable(resultMap.get(choices.get(i))).orElse(0);
            totalScore += score;
        }

        // 3. 遍历测评结果信息，找到第一个用户分数 >= 得分范围的结果，作为最终结果
        ScoringResult maxScoringResult = scoringResultList.get(0);
        for (ScoringResult scoringResult : scoringResultList) {
            if (totalScore >= scoringResult.getResultScoreRange()) {
                maxScoringResult = scoringResult;
                break;
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
        userAnswer.setResultScore(totalScore);
        // UserId在addUserAnswer中设置
        // userAnswer.setUserId();
        return userAnswer;
    }
}
