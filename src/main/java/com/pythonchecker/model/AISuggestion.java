package com.pythonchecker.model;

public class AISuggestion {
    private String summary;
    private String review;
    private String advice;

    public AISuggestion(String summary, String review, String advice) {
        this.summary = summary;
        this.review = review;
        this.advice = advice;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }
}