package com.bank.csp.domain;

import jakarta.persistence.*; // Use jakarta.persistence (modern)
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "refactor_log")
public class RefactorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private LocalDateTime uploadTimestamp;

    private long fileSize;

    @Column(nullable = false)
    private String status;

    // --- Database columns for the refactored files ---
    // @Lob tells JPA this is a "Large Object" (for big text)
    // @Column(columnDefinition = "CLOB") specifies the SQL type as "Character Large Object"

    @Lob
    @Column(columnDefinition = "CLOB")
    private String refactoredHtml;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String refactoredCss;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String refactoredJs;

    // --- Database column for the "Change Log" tab ---
    // We store the List<String> as a single large text block, separated by newlines
    @Lob
    @Column(columnDefinition = "CLOB")
    private String changeLog;

    // --- Constructors ---

    /**
     * Default constructor required by JPA.
     */
    public RefactorLog() {
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public LocalDateTime getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(LocalDateTime uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRefactoredHtml() {
        return refactoredHtml;
    }

    public void setRefactoredHtml(String refactoredHtml) {
        this.refactoredHtml = refactoredHtml;
    }

    public String getRefactoredCss() {
        return refactoredCss;
    }

    public void setRefactoredCss(String refactoredCss) {
        this.refactoredCss = refactoredCss;
    }

    public String getRefactoredJs() {
        return refactoredJs;
    }

    public void setRefactoredJs(String refactoredJs) {
        this.refactoredJs = refactoredJs;
    }

    public String getChangeLog() {
        return this.changeLog;
    }

    public void setChangeLog(List<String> changeLog) {
        // Convert the List<String> into a single string for database storage
        this.changeLog = String.join("\n", changeLog);
    }

    // This is a helper method for Thymeleaf (the HTML template)
    // to read the change log back as a list.
    public List<String> getChangeLogAsList() {
        if (this.changeLog == null || this.changeLog.isEmpty()) {
            return List.of("No changes were logged.");
        }
        return List.of(this.changeLog.split("\n"));
    }
}

