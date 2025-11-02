package com.bank.csp.web;

import com.bank.csp.domain.RefactorLog;
import com.bank.csp.repository.RefactorLogRepository;
import com.bank.csp.service.RefactorService;
import org.springframework.data.domain.Sort; // <-- ADD THIS IMPORT
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.zip.ZipOutputStream;

@Controller
public class RefactorController {

    private final RefactorService refactorService;
    private final RefactorLogRepository logRepository;

    public RefactorController(RefactorService refactorService, RefactorLogRepository logRepository) {
        this.refactorService = refactorService;
        this.logRepository = logRepository;
    }

    /**
     * Serves the main upload page.
     */
    @GetMapping("/")
    public String index() {
        return "index"; // Renders templates/index.html
    }

    /**
     * Serves the audit log page, showing all past refactoring jobs.
     */
    @GetMapping("/audit")
    public String auditLog(Model model) {
        // --- THIS IS THE FIX ---
        // Find all logs, and explicitly tell Spring how to sort them.
        model.addAttribute("logs", logRepository.findAll(Sort.by(Sort.Direction.DESC, "uploadTimestamp")));
        // --- END OF FIX ---
        return "audit"; // Renders templates/audit.html
    }

    /**
     * Handles the file upload, processes it, saves to DB, and redirects to the results page.
     */
    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload.");
            return "redirect:/";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".html")) {
            redirectAttributes.addFlashAttribute("error", "Please upload a valid .html file.");
            return "redirect:/";
        }

        try {
            String htmlContent = new String(file.getBytes());

            // 1. Run the refactoring logic
            RefactorService.RefactorResult result = refactorService.refactor(htmlContent, originalFilename);

            // 2. Save the results to the database
            // --- THIS IS THE FIX ---
            // We now use the default constructor and setter methods

            RefactorLog log = new RefactorLog(); // Use the empty constructor

            // Set all the values using setters
            log.setOriginalFilename(originalFilename);
            log.setUploadTimestamp(LocalDateTime.now());
            log.setFileSize(file.getSize());
            log.setStatus("SUCCESS");
            log.setRefactoredHtml(result.getHtml());
            log.setRefactoredCss(result.getCss());
            log.setRefactoredJs(result.getJs());
            log.setChangeLog(result.getChangeLog()); // This now saves the List<String> as a single string

            // --- END OF FIX ---

            RefactorLog savedLog = logRepository.save(log);

            // 3. Redirect to the new results page with the ID of the new log entry
            return "redirect:/results/" + savedLog.getId();

        } catch (Exception e) {
            e.printStackTrace();
            // Also log this failure to the database
            RefactorLog log = new RefactorLog(); // Use the empty constructor
            log.setOriginalFilename(originalFilename);
            log.setUploadTimestamp(LocalDateTime.now());
            log.setFileSize(file.getSize());
            log.setStatus("FAILED: " + e.getMessage());

            logRepository.save(log);

            redirectAttributes.addFlashAttribute("error", "An error occurred during refactoring: " + e.getMessage());
            return "redirect:/";
        }
    }

    /**
     * Serves the results page for a specific refactoring job.
     */
    @GetMapping("/results/{id}")
    public String showResults(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        // Find the log in the database by its ID
        RefactorLog log = logRepository.findById(id).orElse(null);

        if (log == null) {
            redirectAttributes.addFlashAttribute("error", "Refactor log not found.");
            return "redirect:/audit";
        }

        // Add the log object to the model so the HTML page can access it
        model.addAttribute("log", log);
        return "results"; // Renders templates/results.html
    }

    /**
     * Handles the "Download .zip" button from the results page.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadZip(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        RefactorLog log = logRepository.findById(id).orElse(null);

        if (log == null) {
            redirectAttributes.addFlashAttribute("error", "Refactor log not found.");
            return ResponseEntity.notFound().build();
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            // Get the base filename (e.g., "login" from "login.html")
            String baseFilename = log.getOriginalFilename().substring(0, log.getOriginalFilename().lastIndexOf('.'));

            // Add all 3 files to the zip
            refactorService.addFileToZip(zos, baseFilename + "_clean.html", log.getRefactoredHtml());
            refactorService.addFileToZip(zos, baseFilename + ".css", log.getRefactoredCss());
            refactorService.addFileToZip(zos, baseFilename + ".js", log.getRefactoredJs());

            zos.close();

            String zipFilename = baseFilename + "_refactored.zip";

            // Send the zip file as a download
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(baos.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}

