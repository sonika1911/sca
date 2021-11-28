package com.sca.sb.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sca.sb.service.SpotBugService;

@Controller
public class UploadController {

	@Value("${analysis.dir.save}")
	private String directorySave;
	
	@Value("${analysis.reports.dir}")
	private String dirReports;
	
	@Value("${analysis.reports.filename}")
	private String fileHTML;
	
    @Autowired
    private SpotBugService spotBugService;

    @GetMapping("/")
    public String index() throws IOException {
    	File directory = new File(directorySave);
    	if(directory.exists())
    		FileUtils.cleanDirectory(directory);
    	
    	File file= new File(dirReports, fileHTML);
    	if(file.exists())
    		file.delete();
    	
        return "upload";
    }

    @PostMapping("/upload")
    public String fileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a file to upload");
            return "redirect:uploadStatus";
        }

        try {
        	
        	File directory = new File(directorySave);
            if (! directory.exists()){
                directory.mkdirs();
            }
            
            //Get the file and save it somewhere
            byte[] bytes = file.getBytes();
            Path path = Paths.get(directorySave, file.getOriginalFilename());
            Files.write(path, bytes);
            
            spotBugService.analyzeFile(file.getOriginalFilename());
            
            redirectAttributes.addFlashAttribute("message", "You successfully uploaded '" + file.getOriginalFilename() + "'");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "redirect:/spotbugreport";
    }

    @GetMapping("/spotbugreport")
    public String uploadStatus() {
        return "/analysis/reports/spotbug";
    }

}