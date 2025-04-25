package com.sqyi.yidada.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 生成题目请求
 *
 * @author sqyi
 */
@Data
public class AiGenerateQuestionRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 题目数
     */
    private int questionNumber;

    /**
     * 选项数
     */
    private int optionNumber;

    private static final long serialVersionUID = 1L;
}
