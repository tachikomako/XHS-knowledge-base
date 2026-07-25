package io.github.tachikomako.xhsknowledge.importx;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("import_batches")
public class ImportBatchEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String clientBatchId;
    private String captureMode;
    private String extractorVersion;
    private Integer received;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private Integer failedCount;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientBatchId() { return clientBatchId; }
    public void setClientBatchId(String clientBatchId) { this.clientBatchId = clientBatchId; }
    public String getCaptureMode() { return captureMode; }
    public void setCaptureMode(String captureMode) { this.captureMode = captureMode; }
    public String getExtractorVersion() { return extractorVersion; }
    public void setExtractorVersion(String extractorVersion) { this.extractorVersion = extractorVersion; }
    public Integer getReceived() { return received; }
    public void setReceived(Integer received) { this.received = received; }
    public Integer getCreatedCount() { return createdCount; }
    public void setCreatedCount(Integer createdCount) { this.createdCount = createdCount; }
    public Integer getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(Integer updatedCount) { this.updatedCount = updatedCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
