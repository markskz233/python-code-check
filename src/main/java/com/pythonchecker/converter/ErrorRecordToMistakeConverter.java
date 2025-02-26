package com.pythonchecker.converter;

import com.pythonchecker.model.ErrorRecord;
import com.pythonchecker.model.Mistake;
import com.pythonchecker.model.User;
import org.springframework.stereotype.Component;

@Component
public class ErrorRecordToMistakeConverter {

    public Mistake convert(ErrorRecord errorRecord, User user) {
        Mistake mistake = new Mistake();
        mistake.setUser(user);
        mistake.setCode(errorRecord.getCodeContent());
        mistake.setErrorMessage(errorRecord.getErrorMessage());
        
        // 根据错误类型进行映射
        String errorType = errorRecord.getErrorType();
        if ("RuntimeError".equals(errorType)) {
            mistake.setErrorType("SYNTAX");
            mistake.setAlgorithmType(Mistake.AlgorithmType.NONE);
        } else if ("OutputMismatch".equals(errorType)) {
            mistake.setErrorType("LOGIC");
            mistake.setAlgorithmType(determineAlgorithmType(errorRecord.getCodeContent(), errorRecord.getProblemDescription()));
        } else {
            mistake.setErrorType(errorType);
            mistake.setAlgorithmType(determineAlgorithmType(errorRecord.getCodeContent(), errorRecord.getProblemDescription()));
        }
        
        mistake.setTitle(errorRecord.getProblemDescription());
        mistake.setCreatedAt(errorRecord.getSubmitTime());
        return mistake;
    }

    private Mistake.AlgorithmType determineAlgorithmType(String code, String description) {
        String codeAndDesc = (code + " " + description).toLowerCase();

        // 动态规划特征
        if (codeAndDesc.contains("dp[") || codeAndDesc.contains("动态规划") ||
            codeAndDesc.contains("最优子结构") || codeAndDesc.contains("状态转移")) {
            return Mistake.AlgorithmType.DYNAMIC_PROGRAMMING;
        }

        // 贪心算法特征
        if (codeAndDesc.contains("贪心") || codeAndDesc.contains("最优解") ||
            codeAndDesc.contains("局部最优")) {
            return Mistake.AlgorithmType.GREEDY;
        }

        // 回溯算法特征
        if (codeAndDesc.contains("回溯") || codeAndDesc.contains("递归") ||
            codeAndDesc.contains("深度优先搜索") || codeAndDesc.contains("dfs")) {
            return Mistake.AlgorithmType.BACKTRACKING;
        }

        // 分治算法特征
        if (codeAndDesc.contains("分治") || codeAndDesc.contains("归并排序") ||
            codeAndDesc.contains("快速排序")) {
            return Mistake.AlgorithmType.DIVIDE_AND_CONQUER;
        }

        // 排序算法特征
        if (codeAndDesc.contains("排序") || codeAndDesc.contains("sort")) {
            return Mistake.AlgorithmType.SORTING;
        }

        // 搜索算法特征
        if (codeAndDesc.contains("搜索") || codeAndDesc.contains("查找") ||
            codeAndDesc.contains("search") || codeAndDesc.contains("find")) {
            return Mistake.AlgorithmType.SEARCHING;
        }

        // 图算法特征
        if (codeAndDesc.contains("图") || codeAndDesc.contains("邻接") ||
            codeAndDesc.contains("最短路径") || codeAndDesc.contains("graph")) {
            return Mistake.AlgorithmType.GRAPH;
        }

        // 树算法特征
        if (codeAndDesc.contains("树") || codeAndDesc.contains("二叉") ||
            codeAndDesc.contains("tree") || codeAndDesc.contains("root")) {
            return Mistake.AlgorithmType.TREE;
        }

        return Mistake.AlgorithmType.NONE;
    }
}