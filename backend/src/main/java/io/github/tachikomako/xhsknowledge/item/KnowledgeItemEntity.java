package io.github.tachikomako.xhsknowledge.item;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("knowledge_items")
public class KnowledgeItemEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sourceType;
    private String sourceItemId;
    private String canonicalUrl;
    private String originalUrl;
    private String title;
    private String content;
    private String contentStatus;
    private String contentLastError;
    private String author;
    private String coverUrl;
    private String imageUrlsJson;
    private String captureLevel;
    private String summary;
    private String userNote;
    private String categoryId;
    private String aiStatus;
    private Double aiConfidence;
    private String aiLastError;
    private String lifecycleStatus;
    private Integer manualMetadataLocked;
    private String createdAt;
    private String sourceUpdatedAt;
    private String userEditedAt;
    private String updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(String sourceItemId) { this.sourceItemId = sourceItemId; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentStatus() { return contentStatus; }
    public void setContentStatus(String contentStatus) { this.contentStatus = contentStatus; }
    public String getContentLastError() { return contentLastError; }
    public void setContentLastError(String contentLastError) { this.contentLastError = contentLastError; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getImageUrlsJson() { return imageUrlsJson; }
    public void setImageUrlsJson(String imageUrlsJson) { this.imageUrlsJson = imageUrlsJson; }
    public String getCaptureLevel() { return captureLevel; }
    public void setCaptureLevel(String captureLevel) { this.captureLevel = captureLevel; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getUserNote() { return userNote; }
    public void setUserNote(String userNote) { this.userNote = userNote; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getAiStatus() { return aiStatus; }
    public void setAiStatus(String aiStatus) { this.aiStatus = aiStatus; }
    public Double getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(Double aiConfidence) { this.aiConfidence = aiConfidence; }
    public String getAiLastError() { return aiLastError; }
    public void setAiLastError(String aiLastError) { this.aiLastError = aiLastError; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public Integer getManualMetadataLocked() { return manualMetadataLocked; }
    public void setManualMetadataLocked(Integer manualMetadataLocked) { this.manualMetadataLocked = manualMetadataLocked; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getSourceUpdatedAt() { return sourceUpdatedAt; }
    public void setSourceUpdatedAt(String sourceUpdatedAt) { this.sourceUpdatedAt = sourceUpdatedAt; }
    public String getUserEditedAt() { return userEditedAt; }
    public void setUserEditedAt(String userEditedAt) { this.userEditedAt = userEditedAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
