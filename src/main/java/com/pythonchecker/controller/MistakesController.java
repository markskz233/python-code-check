package com.pythonchecker.controller;

import com.pythonchecker.model.Mistake;
import com.pythonchecker.model.User;
import com.pythonchecker.service.ErrorRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.pythonchecker.model.AISuggestion;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MistakesController {

    @Autowired
    private ErrorRecordService errorRecordService;

    @DeleteMapping("/api/mistakes/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteMistake(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean deleted = errorRecordService.deleteErrorRecord(id, user);
        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/mistakes")
    public String showMistakesPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        // 获取用户的所有错题
        List<Mistake> allMistakes = errorRecordService.getErrorRecordsAsMistakes(user);

        // 按错误类型分类
        List<Mistake> syntaxErrors = allMistakes.stream()
                .filter(m -> "SYNTAX".equals(m.getErrorType()))
                .collect(Collectors.toList());

        // 获取所有非语法错误（逻辑错误）
        List<Mistake> logicErrors = allMistakes.stream()
                .filter(m -> !"SYNTAX".equals(m.getErrorType()))
                .collect(Collectors.toList());

        // 生成AI学习建议
        AISuggestion aiSuggestion = generateAISuggestion(syntaxErrors, logicErrors);
        
        // 将分类后的错题和AI建议传递给视图
        model.addAttribute("syntaxErrors", syntaxErrors);
        model.addAttribute("logicErrors", logicErrors);
        model.addAttribute("aiSuggestion", aiSuggestion);
        
        // 添加CSRF token到模型中
        model.addAttribute("_csrf", new org.springframework.security.web.csrf.CsrfToken() {
            @Override
            public String getHeaderName() {
                return "X-CSRF-TOKEN";
            }
            
            @Override
            public String getParameterName() {
                return "_csrf";
            }
            
            @Override
            public String getToken() {
                return org.springframework.security.web.csrf.CsrfToken.class.getName();
            }
        });
        return "mistakes";
    }
    
    private AISuggestion generateAISuggestion(List<Mistake> syntaxErrors, List<Mistake> logicErrors) {
        if (syntaxErrors.isEmpty() && logicErrors.isEmpty()) {
            return null;
        }
        
        StringBuilder summary = new StringBuilder();
        StringBuilder review = new StringBuilder();
        StringBuilder advice = new StringBuilder();
        
        // 生成错题总结
        summary.append("您总共有 ").append(syntaxErrors.size() + logicErrors.size()).append(" 个错误，");
        summary.append("其中语法错误 ").append(syntaxErrors.size()).append(" 个，");
        summary.append("逻辑错误 ").append(logicErrors.size()).append(" 个。");
        
        // 生成知识点回顾
        if (!syntaxErrors.isEmpty()) {
            review.append("<strong>语法错误类型：</strong><br>");
            syntaxErrors.stream()
                    .map(Mistake::getErrorMessage)
                    .distinct()
                    .forEach(error -> review.append("• ").append(error).append("<br>"));
        }
        if (!logicErrors.isEmpty()) {
            review.append("<strong>逻辑错误类型：</strong><br>");
            logicErrors.stream()
                    .map(Mistake::getErrorMessage)
                    .distinct()
                    .forEach(error -> review.append("• ").append(error).append("<br>"));
        }
        
        // 生成学习建议
        if (syntaxErrors.size() > logicErrors.size()) {
            advice.append("建议您重点关注Python的基础语法学习，特别是：<br>");
            advice.append("1. 仔细检查代码的缩进和括号匹配<br>");
            advice.append("2. 注意变量的声明和使用规范<br>");
            advice.append("3. 复习Python的基本语法结构");
        } else if (!logicErrors.isEmpty()) {
            advice.append("建议您重点关注编程逻辑思维的培养，可以：<br>");
            advice.append("1. 在编写代码前先梳理程序的整体流程<br>");
            advice.append("2. 多做算法练习，提高逻辑思维能力<br>");
            advice.append("3. 学习调试技巧，善用断点和日志");
        }
        
        return new AISuggestion(summary.toString(), review.toString(), advice.toString());
    }
}