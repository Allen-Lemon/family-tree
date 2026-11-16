package com.example.familytree.web;

import com.example.familytree.domain.DomainModels.Gender;
import com.example.familytree.service.AccessService.BadRequestException;
import com.example.familytree.service.ProfileService;
import com.example.familytree.service.ProfileService.ProfileDto;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service) { this.service = service; }

    public record UpdateProfile(@NotNull @Size(max = 160) String displayName, String birthDate, @NotNull Gender gender,
                                @Size(max = 120) String spouseStatus, @Size(max = 120) String education,
                                @Size(max = 5000) String biography) {
        // Reject mass-assignment attempts locally without changing existing API/Jackson behavior.
        @JsonAnySetter public void unknown(String key, Object value) {
            throw new BadRequestException("profile 仅接受 displayName、birthDate、gender、spouseStatus、education、biography");
        }
    }

    @GetMapping
    ResponseEntity<ProfileDto> get(Authentication auth) { return privateResponse(service.get(auth)); }

    @PutMapping
    ResponseEntity<ProfileDto> update(@Valid @RequestBody UpdateProfile request, Authentication auth) {
        return privateResponse(service.update(request.displayName(), request.birthDate(), request.gender(),
                request.spouseStatus(), request.education(), request.biography(), auth));
    }

    @PostMapping(value = "/portrait", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ProfileDto> upload(@RequestParam("file") MultipartFile file, Authentication auth) {
        return privateResponse(service.uploadPortrait(file, auth));
    }

    @GetMapping("/portrait")
    ResponseEntity<byte[]> portrait(Authentication auth) {
        var image = service.portrait(auth);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    private ResponseEntity<ProfileDto> privateResponse(ProfileDto value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
