package com.sqyi.yidada.scoring;

import com.sqyi.yidada.model.entity.App;
import com.sqyi.yidada.model.entity.UserAnswer;

import java.util.List;

/**
 * 评分策略
 *
 * @author sqyi
 */
public interface ScoringStrategy {

    /**
     * 执行评分
     *
     * @param choices
     * @param app
     * @return
     * @throws Exception
     */
    UserAnswer doScore(List<String> choices, App app) throws Exception;
}