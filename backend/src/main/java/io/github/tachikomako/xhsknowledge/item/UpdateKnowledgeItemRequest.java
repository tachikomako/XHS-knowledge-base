package io.github.tachikomako.xhsknowledge.item;

import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;

public class UpdateKnowledgeItemRequest {

    private boolean categoryIdPresent;
    private String categoryId;
    private boolean tagIdsPresent;
    private List<String> tagIds;
    private boolean summaryPresent;
    private String summary;
    private boolean userNotePresent;
    private String userNote;

    @JsonSetter("categoryId")
    public void setCategoryId(String categoryId) {
        this.categoryIdPresent = true;
        this.categoryId = categoryId;
    }

    @JsonSetter("tagIds")
    public void setTagIds(List<String> tagIds) {
        this.tagIdsPresent = true;
        this.tagIds = tagIds;
    }

    @JsonSetter("summary")
    public void setSummary(String summary) {
        this.summaryPresent = true;
        this.summary = summary;
    }

    @JsonSetter("userNote")
    public void setUserNote(String userNote) {
        this.userNotePresent = true;
        this.userNote = userNote;
    }

    public boolean isCategoryIdPresent() { return categoryIdPresent; }
    public String getCategoryId() { return categoryId; }
    public boolean isTagIdsPresent() { return tagIdsPresent; }
    public List<String> getTagIds() { return tagIds; }
    public boolean isSummaryPresent() { return summaryPresent; }
    public String getSummary() { return summary; }
    public boolean isUserNotePresent() { return userNotePresent; }
    public String getUserNote() { return userNote; }
}
