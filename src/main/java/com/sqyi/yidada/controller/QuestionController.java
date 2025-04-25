package com.sqyi.yidada.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sqyi.yidada.annotation.AuthCheck;
import com.sqyi.yidada.common.BaseResponse;
import com.sqyi.yidada.common.DeleteRequest;
import com.sqyi.yidada.common.ErrorCode;
import com.sqyi.yidada.common.ResultUtils;
import com.sqyi.yidada.constant.UserConstant;
import com.sqyi.yidada.exception.BusinessException;
import com.sqyi.yidada.exception.ThrowUtils;
import com.sqyi.yidada.manager.AiManager;
import com.sqyi.yidada.model.dto.question.*;
import com.sqyi.yidada.model.entity.App;
import com.sqyi.yidada.model.entity.Question;
import com.sqyi.yidada.model.entity.User;
import com.sqyi.yidada.model.enums.AppTypeEnum;
import com.sqyi.yidada.model.vo.QuestionVO;
import com.sqyi.yidada.service.AppService;
import com.sqyi.yidada.service.QuestionService;
import com.sqyi.yidada.service.UserService;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChunk;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 题目接口
 *
 * @author sqyi
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private AppService appService;

    @Resource
    private AiManager aiManager;

    // region 增删改查

    /**
     * 创建题目
     *
     * @param questionAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<QuestionContentDTO> questionContent = questionAddRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, true);
        // 填充默认值
        User loginUser = userService.getLoginUser(request);
        question.setUserId(loginUser.getId());
        // 写入数据库
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newQuestionId = question.getId();
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除题目
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新题目（仅管理员可用）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<QuestionContentDTO> questionContent = questionUpdateRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, false);
        // 判断是否存在
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取题目（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVO(question, request));
    }

    /**
     * 分页获取题目列表（仅管理员可用）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取题目列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                               HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 分页获取当前登录用户创建的题目列表
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        // 获取封装类
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
    }

    /**
     * 编辑题目（给用户使用）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<QuestionContentDTO> questionContent = questionEditRequest.getQuestionContent();
        question.setQuestionContent(JSONUtil.toJsonStr(questionContent));
        // 数据校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = questionService.updateById(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion

    // region AI生成题目功能
    // 题目的系统信息
    private static final String GENERATE_QUESTION_SYSTEM_MESSAGE = """
            你是一位严谨的出题专家，我会给你如下信息：
            ```
            应用名称，
            【【【应用描述】】】，
            应用类别，
            要生成的题目数，
            每个题目的选项数
            ```
            
            请你根据上述信息，按照以下步骤来出题：
            1. 要求：题目和选项尽可能地短，题目不要包含序号，每题的选项数以我提供的为主，题目不能重复
            2. 严格按照下面的 json 格式输出题目和选项
            ```
            [{"options":[{"value":"选项内容","key":"A"},{"value":"","key":"B"}],"title":"题目标题"}]
            ```
            title 是题目，options 是选项，每个选项的 key 按照英文字母序（比如 A、B、C、D）以此类推，value 是选项内容
            3. 检查题目是否包含序号，若包含序号则去除序号
            4. 返回的题目列表格式必须为 JSON 数组""";

    /**
     * 生成题目的用户消息
     *
     * @param app
     * @param questionNumber
     * @param optionNumber
     * @return
     */
    private String getGenerateQuestionUserMessage(App app, int questionNumber, int optionNumber) {
        return app.getAppName() + "\n" +
                app.getAppDesc() + "\n" +
                AppTypeEnum.getEnumByValue(app.getAppType()).getText() + "类" + "\n" +
                questionNumber + "\n" +
                optionNumber;
    }

    @PostMapping("/ai_generate")
    public BaseResponse<List<QuestionContentDTO>> aiGenerateQuestion(
            @RequestBody AiGenerateQuestionRequest aiGenerateQuestionRequest) {
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        Long appId = aiGenerateQuestionRequest.getAppId();
        int questionNumber = aiGenerateQuestionRequest.getQuestionNumber();
        int optionNumber = aiGenerateQuestionRequest.getOptionNumber();
        // 获取应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 封装 Prompt
        String userMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // ai调用
        String result = aiManager.doStableRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, userMessage);
        // 封装结果
        List<QuestionContentDTO> questionContentDTOList = JSONUtil.toList(result, QuestionContentDTO.class);
        return ResultUtils.success(questionContentDTOList);
    }

    @GetMapping("/ai_generate/sse")
    public SseEmitter aiGenerateQuestionSse(AiGenerateQuestionRequest aiGenerateQuestionRequest) {
        ThrowUtils.throwIf(aiGenerateQuestionRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取参数
        Long appId = aiGenerateQuestionRequest.getAppId();
        int questionNumber = aiGenerateQuestionRequest.getQuestionNumber();
        int optionNumber = aiGenerateQuestionRequest.getOptionNumber();
        // 获取应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 封装 Prompt
        String userMessage = getGenerateQuestionUserMessage(app, questionNumber, optionNumber);
        // 建立 SSE 连接对象，0 表示永不超时
        SseEmitter emitter = new SseEmitter(0L);
        // AI 生成，sse 流式返回
        Flowable<ChatCompletionChunk> chatCompletionChunkFlowable =
                aiManager.doStreamStableRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, userMessage);
        StringBuffer contentBuilder = new StringBuffer();
        AtomicInteger flag = new AtomicInteger(0);
        // 这是整个数据流的源头，扮演被观察者角色
        chatCompletionChunkFlowable
                // 此操作符将后续所有操作（map、flatMap、doOnNext 等）
                // 切换到 IO 线程池中执行。
                // IO 线程池适用于异步阻塞型任务（如网络请求、文件读写），
                // 且线程数量可动态增长以应对高并发。
                .observeOn(Schedulers.io())
                // 从数据块中提取内容字段
                .map(chunk -> (String) chunk.getChoices().get(0).getMessage().getContent())
                // 移除所有空白字符（包括换行、空格等），简化后续处理
                .map(message -> message.replaceAll("\\s", ""))
                // 过滤掉空字符串，确保只处理有效数据
                .filter(StrUtil::isNotBlank)
                // FlatMap操作符使用一个指定的函数对原始Observable发射的每一项数据执行变换操作，
                // 这个函数返回一个本身也发射数据的Observable，
                // 然后FlatMap合并这些Observables发射的数据，最后将合并后的结果当做它自己的数据序列发射
                .flatMap(message -> {
                    // 将字符串转换为 List<Character>
                    List<Character> charList = new ArrayList<>();
                    for (char c : message.toCharArray()) {
                        charList.add(c);
                    }
                    // 将字符列表（List<Character>）
                    // 转换为一个 RxJava 的流（Flowable<Character>），
                    // 使得列表中的每个字符可以作为一个独立的事件被逐个发射到下游
                    return Flowable.fromIterable(charList);
                })
                .doOnNext(c -> {
                    {
                        // 识别第一个 [ 表示开始 AI 传输 json 数据，打开 flag 开始拼接 json 数组
                        if (c == '{') {
                            flag.addAndGet(1);
                        }
                        if (flag.get() > 0) {
                            contentBuilder.append(c);
                        }
                        if (c == '}') {
                            flag.addAndGet(-1);
                            if (flag.get() == 0) {
                                // 累积单套题目满足 json 格式后，sse 推送至前端
                                // sse 需要压缩成当行 json，sse 无法识别换行
                                emitter.send(JSONUtil.toJsonStr(contentBuilder.toString()));
                                // 清空 StringBuilder
                                contentBuilder.setLength(0);
                            }
                        }
                    }
                }).doOnComplete(emitter::complete)
                // .subscribe() 方法是启动数据流处理的核心入口
                // 调用该方法后，被观察者开始按照定义的逻辑发射数据，观察并处理这些数据。
                .subscribe();
        return emitter;
    }
    // endregion
}
