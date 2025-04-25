package com.sqyi.yidada.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * 问题答案数据传输对象
 *
 * @author sqyi
 */
@Data
public class QuestionAnswerDTO implements Serializable {

    /**
     * 题目标题
     */
    private String title;

    /**
     * 用户答案
     */
    private String userAnswer;

    private static final long serialVersionUID = 1L;
}
