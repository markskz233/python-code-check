package com.pythonchecker.controller;

import com.pythonchecker.model.Mistake;
import com.pythonchecker.model.User;
import com.pythonchecker.service.ErrorRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class MistakesController {

    @Autowired
    private ErrorRecordService errorRecordService;

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

        // 将分类后的错题传递给视图
        model.addAttribute("syntaxErrors", syntaxErrors);
        model.addAttribute("logicErrors", logicErrors);
        return "mistakes";
    }
}