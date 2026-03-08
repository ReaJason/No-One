package com.reajason.noone.server.generator.webshell;

import com.reajason.noone.core.generator.NoOneConfig;
import com.reajason.noone.core.generator.NoOneDotNetWebShellGenerator;
import com.reajason.noone.core.generator.NoOneNodeJsWebShellGenerator;
import com.reajason.noone.core.generator.NoOneJavaWebShellGenerator;
import com.reajason.noone.core.generator.ServletModule;
import com.reajason.noone.server.generator.webshell.dto.WebShellGenerateRequest;
import com.reajason.noone.server.generator.webshell.dto.WebShellGenerateResponse;
import com.reajason.noone.server.profile.Profile;
import com.reajason.noone.server.profile.ProfileRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/webshell")
@CrossOrigin("*")
public class WebShellGeneratorController {

    private static final Set<String> JAVA_FORMATS = Set.of("JSP", "JSPX");
    private static final Set<String> DOTNET_FORMATS = Set.of("ASPX", "ASHX", "ASMX", "SOAP");
    private static final Set<String> NODEJS_FORMATS = Set.of("MJS");

    private final ProfileRepository profileRepository;

    public WebShellGeneratorController(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @PostMapping("/generate")
    public WebShellGenerateResponse generate(@RequestBody WebShellGenerateRequest request) {
        Profile profile = profileRepository.findById(request.getProfileId())
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + request.getProfileId()));

        NoOneConfig config = new NoOneConfig(profile);
        String language = normalizeValue(request.getLanguage(), "language");
        String format = normalizeValue(request.getFormat(), "format");

        return switch (language) {
            case "JAVA" -> generateJavaWebShell(config, request.getServletModule(), format);
            case "DOTNET" -> generateDotNetWebShell(config, format);
            case "NODEJS" -> generateNodeJsWebShell(config, format);
            default -> throw new IllegalArgumentException("Unsupported webshell language: " + request.getLanguage());
        };
    }

    private WebShellGenerateResponse generateJavaWebShell(NoOneConfig config, String servletModuleValue, String format) {
        if (!JAVA_FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unsupported format for java webshell: " + format);
        }

        ServletModule servletModule = "JAKARTA".equalsIgnoreCase(servletModuleValue)
                ? ServletModule.JAKARTA : ServletModule.JAVAX;
        NoOneJavaWebShellGenerator generator = new NoOneJavaWebShellGenerator(config, servletModule);

        boolean isJspx = "JSPX".equals(format);
        String content = isJspx ? generator.generateJspx() : generator.generateJsp();
        String normalizedFormat = isJspx ? "JSPX" : "JSP";
        String fileName = "shell." + normalizedFormat.toLowerCase(Locale.ROOT);

        return new WebShellGenerateResponse(content, normalizedFormat, fileName);
    }

    private WebShellGenerateResponse generateDotNetWebShell(NoOneConfig config, String format) {
        if (!DOTNET_FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unsupported format for dotnet webshell: " + format);
        }

        NoOneDotNetWebShellGenerator generator = new NoOneDotNetWebShellGenerator(config);
        String content = switch (format) {
            case "ASPX" -> generator.generateAspx();
            case "ASHX" -> generator.generateAshx();
            case "ASMX" -> generator.generateAsmx();
            case "SOAP" -> generator.generateSoap();
            default -> throw new IllegalArgumentException("Unsupported dotnet webshell format: " + format);
        };
        return new WebShellGenerateResponse(content, format, "shell." + format.toLowerCase(Locale.ROOT));
    }

    private WebShellGenerateResponse generateNodeJsWebShell(NoOneConfig config, String format) {
        if (!NODEJS_FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unsupported format for nodejs webshell: " + format);
        }

        NoOneNodeJsWebShellGenerator generator = new NoOneNodeJsWebShellGenerator(config);
        String content = generator.generateMjs();
        return new WebShellGenerateResponse(content, format, "shell." + format.toLowerCase(Locale.ROOT));
    }

    private static String normalizeValue(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
