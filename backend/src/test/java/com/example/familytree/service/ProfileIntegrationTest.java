package com.example.familytree.service;

import com.example.familytree.config.AppProperties;
import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileIntegrationTest {
    private static final String PASSWORD = "test-password-123";
    private static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepo users;
    @Autowired TreeRepo trees;
    @Autowired MemberRepo members;
    @Autowired JdbcClient jdbc;
    @Autowired ProfileService profiles;
    @Autowired AppProperties properties;
    @Autowired PlatformTransactionManager transactionManager;
    User self, other;

    @BeforeEach void setup() {
        // Tests disable Flyway and create their schema from JPA; details are deliberately JDBC-owned, not entity fields.
        jdbc.sql("alter table users add column if not exists spouse_status varchar(120)").update();
        jdbc.sql("alter table users add column if not exists education varchar(120)").update();
        jdbc.sql("alter table users add column if not exists biography varchar(5000)").update();
        jdbc.sql("alter table users alter column created_at set default current_timestamp").update();
        self = users.saveAndFlush(new User("profile-self-" + UUID.randomUUID(), HASH, Role.MEMBER));
        other = users.saveAndFlush(new User("profile-other-" + UUID.randomUUID(), HASH, Role.MEMBER));
    }
    MockHttpSession login(User account) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", account.getUsername(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    UsernamePasswordAuthenticationToken auth(User account) { return new UsernamePasswordAuthenticationToken(account.getUsername(), "unused"); }
    byte[] png() throws IOException {
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out); image.flush(); return out.toByteArray();
    }
    MockMultipartFile image() throws IOException { return new MockMultipartFile("file", "../../not-used.png", "image/png", png()); }
    Path stored(String name) { return Path.of(properties.storageRoot()).resolve("profile-portraits").resolve(name); }
    String name(User account) { return jdbc.sql("select portrait_stored_name from users where id=:id").param("id", account.getId()).query(String.class).single(); }

    @Test void unassignedAccountCanReadAndSaveOwnProfileWithoutTouchingMemberOrAdministrativeFields() throws Exception {
        var tree = trees.saveAndFlush(new FamilyTree("independent-profile", "", other.getId()));
        var person = members.saveAndFlush(new Member(tree.getId(), "member-name", Gender.MALE));
        self.setMemberId(person.getId()); users.flush(); // Even a now-inaccessible historic binding is not a profile dependency.
        var session = login(self);
        mvc.perform(get("/api/v1/profile").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("username").value(self.getUsername())).andExpect(jsonPath("displayName").value(""))
                .andExpect(jsonPath("birthDate").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("age").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("gender").value("UNKNOWN")).andExpect(jsonPath("hasPortrait").value(false))
                .andExpect(jsonPath("spouseStatus").value(""))
                .andExpect(jsonPath("education").value(""))
                .andExpect(jsonPath("biography").value(""));
        var birth = LocalDate.now().minusYears(30).withDayOfMonth(1);
        mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("displayName", " 独立昵称 ", "birthDate", birth.toString(), "gender", "FEMALE"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("username").value(self.getUsername())).andExpect(jsonPath("displayName").value("独立昵称"))
                .andExpect(jsonPath("age").value(Period.between(birth, LocalDate.now()).getYears())).andExpect(jsonPath("gender").value("FEMALE"));
        assertEquals("member-name", members.findById(person.getId()).orElseThrow().getName());
        assertEquals(person.getId(), users.findById(self.getId()).orElseThrow().getMemberId());
        assertEquals(Role.MEMBER, users.findById(self.getId()).orElseThrow().getRole());
        assertEquals("", profiles.get(auth(other)).displayName());
        // A later entity-based account update must not overwrite JDBC-owned profile columns.
        self.setEnabled(false); users.flush(); self.setEnabled(true); users.flush();
        assertEquals("独立昵称", profiles.get(auth(self)).displayName());
    }

    @Test void profileDetailsRoundTripOnlyForCurrentAccountAndRemainIndependentOfMember() throws Exception {
        var tree = trees.saveAndFlush(new FamilyTree("profile-details", "", other.getId()));
        var person = members.saveAndFlush(new Member(tree.getId(), "unchanged-member", Gender.MALE));
        self.setMemberId(person.getId()); users.flush();
        profiles.update("other", null, Gender.UNKNOWN, "他人配偶", "他人学历", "他人简介", auth(other));
        var session = login(self);
        var details = Map.of("displayName", "自己", "gender", "FEMALE", "spouseStatus", "已婚，配偶情况自由描述",
                "education", "本科 / 自学", "biography", "第一行\n第二行 <自由文本>");
        mvc.perform(put("/api/v1/profile").session(session).with(csrf()).param("id", other.getId().toString())
                        .contentType("application/json").content(json.writeValueAsString(details)))
                .andExpect(status().isOk()).andExpect(jsonPath("spouseStatus").value(details.get("spouseStatus")))
                .andExpect(jsonPath("education").value(details.get("education")))
                .andExpect(jsonPath("biography").value(details.get("biography")));
        mvc.perform(get("/api/v1/profile").session(session).param("id", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value(self.getUsername()))
                .andExpect(jsonPath("spouseStatus").value(details.get("spouseStatus")))
                .andExpect(jsonPath("education").value(details.get("education")))
                .andExpect(jsonPath("biography").value(details.get("biography")));
        var foreign = profiles.get(auth(other));
        assertEquals("他人配偶", foreign.spouseStatus()); assertEquals("他人学历", foreign.education());
        assertEquals("他人简介", foreign.biography());
        assertEquals("unchanged-member", members.findById(person.getId()).orElseThrow().getName());
        assertEquals(person.getId(), users.findById(self.getId()).orElseThrow().getMemberId());
        assertEquals(Role.MEMBER, users.findById(self.getId()).orElseThrow().getRole());
        self.setEnabled(false); users.flush(); self.setEnabled(true); users.flush();
        assertEquals(details.get("biography"), profiles.get(auth(self)).biography());
    }

    @Test void omittedAndNullDetailsPreserveExistingValuesAndExplicitEmptyStringsClearThem() throws Exception {
        profiles.update("before", null, Gender.UNKNOWN, "配偶", "学历", "简介", auth(self));
        var session = login(self);
        for (String body : List.of("{\"displayName\":\"legacy\",\"gender\":\"UNKNOWN\"}",
                "{\"displayName\":\"legacy\",\"gender\":\"UNKNOWN\",\"spouseStatus\":null,\"education\":null,\"biography\":null}")) {
            mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("spouseStatus").value("配偶"))
                    .andExpect(jsonPath("education").value("学历")).andExpect(jsonPath("biography").value("简介"));
        }
        var legacy = profiles.update("legacy service", null, Gender.UNKNOWN, auth(self));
        assertEquals("配偶", legacy.spouseStatus()); assertEquals("学历", legacy.education()); assertEquals("简介", legacy.biography());
        mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json")
                        .content("{\"displayName\":\"clear\",\"gender\":\"UNKNOWN\",\"spouseStatus\":\"\",\"education\":\"\",\"biography\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("spouseStatus").value(""))
                .andExpect(jsonPath("education").value("")).andExpect(jsonPath("biography").value(""));
        assertEquals("", profiles.get(auth(self)).biography());
    }

    @Test void detailLengthBoundariesAreEnforcedByControllerAndService() throws Exception {
        var session = login(self);
        var maximum = Map.of("displayName", "limits", "gender", "UNKNOWN", "spouseStatus", "配".repeat(120),
                "education", "学".repeat(120), "biography", "传".repeat(5000));
        mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(maximum)))
                .andExpect(status().isOk()).andExpect(jsonPath("spouseStatus").value(maximum.get("spouseStatus")))
                .andExpect(jsonPath("education").value(maximum.get("education")))
                .andExpect(jsonPath("biography").value(maximum.get("biography")));
        for (String field : List.of("spouseStatus", "education", "biography")) {
            var request = new LinkedHashMap<String, Object>(maximum);
            request.put(field, "x".repeat(field.equals("biography") ? 5001 : 121));
            mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
            assertThrows(AccessService.BadRequestException.class, () -> profiles.update("invalid", null, Gender.UNKNOWN,
                    field.equals("spouseStatus") ? "x".repeat(121) : null,
                    field.equals("education") ? "x".repeat(121) : null,
                    field.equals("biography") ? "x".repeat(5001) : null, auth(self)));
        }
        assertEquals(maximum.get("biography"), profiles.get(auth(self)).biography());
        assertEquals("", profiles.get(auth(other)).biography());
    }

    @Test void forgedFieldsAreRejectedAndCannotModifyIdentityRoleBindingPortraitOrAnotherProfile() throws Exception {
        var session = login(self);
        for (String field : List.of("id", "role", "username", "memberId", "treeId", "portraitStoredName", "age", "unknown", "spouse_status")) {
            var request = new LinkedHashMap<String,Object>();
            request.put("displayName", "injected"); request.put("birthDate", null); request.put("gender", "UNKNOWN");
            request.put("spouseStatus", "injected"); request.put("education", "injected"); request.put("biography", "injected");
            request.put(field, "injected");
            mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("", profiles.get(auth(self)).displayName()); assertEquals("", profiles.get(auth(other)).displayName());
        assertEquals("", profiles.get(auth(self)).spouseStatus()); assertEquals("", profiles.get(auth(self)).education());
        assertEquals("", profiles.get(auth(self)).biography()); assertEquals("", profiles.get(auth(other)).biography());
        assertEquals(Role.MEMBER, users.findById(self.getId()).orElseThrow().getRole());
        mvc.perform(get("/api/v1/profile").session(session).param("id", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value(self.getUsername()));
    }

    @Test void birthDateStrictnessFutureEmptyAndAgeAreValidatedOnServer() throws Exception {
        var session = login(self);
        for (String date : List.of("2025-02-29", "2024-02-30", "2000-13-01", "2000-00-01", "0000-01-01", "2000-1-01", "2000-01-01T00:00:00", " 2000-01-01", LocalDate.now().plusDays(1).toString())) {
            mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("displayName", "date", "birthDate", date, "gender", "UNKNOWN"))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json")
                .content("{\"displayName\":\"leap\",\"birthDate\":\"2000-02-29\",\"gender\":\"OTHER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("birthDate").value("2000-02-29"))
                .andExpect(jsonPath("age").value(Period.between(LocalDate.of(2000,2,29), LocalDate.now()).getYears()));
        for (String empty : List.of("null", "\"\"")) {
            mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json")
                    .content("{\"displayName\":\"\",\"birthDate\":" + empty + ",\"gender\":\"UNKNOWN\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("birthDate").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("age").value(org.hamcrest.Matchers.nullValue()));
        }
        assertThrows(AccessService.BadRequestException.class, () -> profiles.update("valid", "2023-02-29", Gender.UNKNOWN, auth(self)));
    }

    @Test void profileRequiresAuthenticationCsrfAndValidWritableFields() throws Exception {
        mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profile/portrait")).andExpect(status().isUnauthorized());
        var session = login(self);
        mvc.perform(put("/api/v1/profile").session(session).contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        for (String body : List.of("{}", "{\"displayName\":null,\"gender\":\"UNKNOWN\"}", "{\"displayName\":\"ok\",\"gender\":\"INVALID\"}",
                "{\"displayName\":\"ok\",\"gender\":null}", json.writeValueAsString(Map.of("displayName", "x".repeat(161), "gender", "UNKNOWN")))) {
            mvc.perform(put("/api/v1/profile").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test void privatePortraitReencodesAndNeverLeaksStorageOrUsesFamilyMedia() throws Exception {
        profiles.update("portrait", null, Gender.UNKNOWN, "配偶情况", "教育经历", "个人简介", auth(self));
        var session = login(self);
        var payload = new ByteArrayOutputStream(); payload.write(png()); payload.write("<script>trailing-payload</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/profile/portrait").file(new MockMultipartFile("file", "../../secret.svg", "image/png", payload.toByteArray())).session(session).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value(self.getUsername())).andExpect(jsonPath("hasPortrait").value(true))
                .andExpect(jsonPath("spouseStatus").value("配偶情况")).andExpect(jsonPath("education").value("教育经历"))
                .andExpect(jsonPath("biography").value("个人简介"))
                .andExpect(jsonPath("portraitVersion").isString()).andExpect(jsonPath("portraitStoredName").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist()).andExpect(jsonPath("id").doesNotExist());
        String ownName = name(self);
        assertTrue(ownName.matches(self.getId() + "_[0-9a-f-]{36}\\.png")); assertTrue(Files.exists(stored(ownName)));
        var response = mvc.perform(get("/api/v1/profile/portrait").session(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentType("image/png")).andReturn().getResponse();
        assertNotNull(ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray())));
        assertFalse(new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("trailing-payload"));
        mvc.perform(get("/api/v1/profile/portrait").session(login(other)).param("id", self.getId().toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/profile/portrait/{id}", self.getId()).session(login(other))).andExpect(status().is4xxClientError());
        assertEquals(0, jdbc.sql("select count(*) from media").query(Integer.class).single());
        assertFalse(json.writeValueAsString(users.findById(self.getId()).orElseThrow()).contains("portraitStoredName"));
    }

    @Test void malformedUnsupportedOversizeAndMissingPortraitAreRejectedWithoutCreatingAReference() throws Exception {
        var session = login(self);
        for (byte[] data : List.of(new byte[0], new byte[]{1,2,3}, "<svg/>".getBytes(), "%PDF-1.4".getBytes(), "GIF89a".getBytes(), new byte[]{(byte)255,(byte)216,(byte)255,1}, new byte[ProfilePortraitStorage.MAX_BYTES + 1])) {
            mvc.perform(multipart("/api/v1/profile/portrait").file(new MockMultipartFile("file", "fake.png", "image/png", data)).session(session).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/v1/profile/portrait").session(session).with(csrf())).andExpect(status().isBadRequest());
        assertFalse(profiles.get(auth(self)).hasPortrait());
    }

    @Test void deletedAndDisabledAccountsCannotUseExistingSessionsOrDirectService() throws Exception {
        var session = login(self); var otherSession = login(other);
        self.setDeleted(true); self.setEnabled(false); other.setEnabled(false); users.flush();
        mvc.perform(get("/api/v1/profile/portrait").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profile").session(otherSession)).andExpect(status().isUnauthorized());
        assertThrows(org.springframework.security.authentication.DisabledException.class, () -> profiles.get(auth(self)));
        assertThrows(org.springframework.security.authentication.DisabledException.class, () -> profiles.update("no", null, Gender.UNKNOWN, auth(other)));
        assertThrows(org.springframework.security.authentication.DisabledException.class, () -> profiles.uploadPortrait(image(), auth(self)));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedReplacementDeletesOnlyOwnPreviousFileAndRollbackDeletesOnlyNewFile() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        var account = users.saveAndFlush(new User("profile-commit-" + UUID.randomUUID(), HASH, Role.MEMBER));
        var another = users.saveAndFlush(new User("profile-commit-other-" + UUID.randomUUID(), HASH, Role.MEMBER));
        var created = new ArrayList<Path>();
        var file = image();
        try {
            profiles.uploadPortrait(file, auth(account)); String first = name(account); created.add(stored(first));
            profiles.uploadPortrait(file, auth(another)); String foreign = name(another); created.add(stored(foreign));
            assertTrue(Files.exists(stored(first)));
            profiles.uploadPortrait(file, auth(account)); String second = name(account); created.add(stored(second));
            assertNotEquals(first, second); assertFalse(Files.exists(stored(first))); assertTrue(Files.exists(stored(second))); assertTrue(Files.exists(stored(foreign)));
            tx.executeWithoutResult(status -> {
                profiles.uploadPortrait(file, auth(account)); String rolledBack = name(account); created.add(stored(rolledBack));
                assertTrue(Files.exists(stored(rolledBack))); assertTrue(Files.exists(stored(second)));
                status.setRollbackOnly();
            });
            assertEquals(second, name(account)); assertFalse(Files.exists(created.getLast())); assertTrue(Files.exists(stored(second))); assertTrue(Files.exists(stored(foreign)));
            assertThrows(AccessService.BadRequestException.class, () -> profiles.uploadPortrait(new MockMultipartFile("file", "bad", "image/png", new byte[]{1}), auth(account)));
            assertEquals(second, name(account)); assertTrue(Files.exists(stored(second)));
            // Even a corrupt cross-account stored key cannot cause another user's file to be read/deleted.
            jdbc.sql("update users set portrait_stored_name=:name where id=:id").param("name", foreign).param("id", account.getId()).update();
            assertThrows(AccessService.NotFoundException.class, () -> profiles.portrait(auth(account)));
            profiles.uploadPortrait(file, auth(account)); created.add(stored(name(account))); assertTrue(Files.exists(stored(foreign)));
        } finally {
            for (var path : created) Files.deleteIfExists(path);
            users.deleteById(account.getId()); users.deleteById(another.getId());
            // setup() is outside a test transaction for this method too.
            users.deleteById(self.getId()); users.deleteById(other.getId());
        }
    }
}
