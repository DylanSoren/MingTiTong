package com.sqyi.yidada.scoring;

import com.sqyi.yidada.common.ErrorCode;
import com.sqyi.yidada.exception.BusinessException;
import com.sqyi.yidada.exception.ThrowUtils;
import com.sqyi.yidada.model.entity.App;
import com.sqyi.yidada.model.entity.UserAnswer;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.annotation.Annotation;
import java.util.List;

/**
 * @author sqyi
 *
 */
@Service
public class ScoringStrategyExecutor {
    // 策略列表，可以通过Spring自动注入
    @Resource
    private List<ScoringStrategy> scoringStrategyList;

    /**
     * 评分
     *
     * @param choiceList
     * @param app
     * @return
     * @throws Exception
     */
    public UserAnswer doScore(List<String> choiceList, App app) throws Exception {
        Integer appType = app.getAppType();
        Integer scoringStrategy = app.getScoringStrategy();
        ThrowUtils.throwIf((appType == null || scoringStrategy == null),
                ErrorCode.SYSTEM_ERROR, "应用类型或评分策略不能为空");
        // 根据注解执行对应策略
        for (ScoringStrategy strategy : scoringStrategyList) {
            if (strategy.getClass().isAnnotationPresent(ScoringStrategySetting.class)) {
                ScoringStrategySetting scoringStrategySetting =
                        strategy.getClass().getAnnotation(ScoringStrategySetting.class);
                if (scoringStrategySetting.appType() == appType
                        && scoringStrategySetting.scoringStrategy() == scoringStrategy) {
                    return strategy.doScore(choiceList, app);
                }
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用配置有误，未找到匹配的策略");
    }
}
