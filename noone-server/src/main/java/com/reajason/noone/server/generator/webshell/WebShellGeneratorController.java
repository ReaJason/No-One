package com.reajason.noone.server.generator.webshell;

import com.reajason.noone.core.generator.NoOneConfig;
import com.reajason.noone.core.generator.NoOneWebShellGenerator;
import com.reajason.noone.server.generator.webshell.dto.WebShellGenerateRequest;
import com.reajason.noone.server.generator.webshell.dto.WebShellGenerateResponse;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webshell")
@CrossOrigin("*")
public class WebShellGeneratorController {

    private final ProfileRepository profileRepository;

    public WebShellGeneratorController(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @PostMapping("/generate")
    public WebShellGenerateResponse generate(@RequestBody WebShellGenerateRequest request) {
        Profile profile = profileRepository.findById(request.getProfileId())
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + request.getProfileId()));

        NoOneConfig config = new NoOneConfig(profile);
        NoOneWebShellGenerator generator = new NoOneWebShellGenerator(config);

        boolean isJspx = "JSPX".equalsIgnoreCase(request.getFormat());
        String content = isJspx ? generator.generateJspx() : generator.generateJsp();
        String format = isJspx ? "JSPX" : "JSP";
        String fileName = "shell." + format.toLowerCase();

        return new WebShellGenerateResponse(content, format, fileName);
    }
}
